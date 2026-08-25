#!/usr/bin/env bash
# Install the collector as a systemd service. Run as root on the VPS.
#
#   sudo ./deploy/install.sh
#
# Idempotent: safe to re-run to upgrade an existing install.
set -euo pipefail

APP_DIR=/opt/crash-monitor
DATA_DIR=/var/lib/crash-monitor
SERVICE=crash-monitor
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $EUID -ne 0 ]]; then
    echo "error: run as root (sudo $0)" >&2
    exit 1
fi

# Debian and Ubuntu ship python3 without ensurepip, so `python3 -m venv` fails
# with "ensurepip is not available" until the matching venv package is present.
ensure_venv_support() {
    if python3 -c "import ensurepip" >/dev/null 2>&1; then
        return 0
    fi
    echo "==> python3-venv is missing (ensurepip unavailable)"
    if ! command -v apt-get >/dev/null 2>&1; then
        echo "error: install your distribution's python3 venv package, then re-run." >&2
        exit 1
    fi
    # The generic package does not exist on every release; the versioned one
    # (python3.12-venv and friends) is the reliable fallback.
    local pyver
    pyver="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
    apt-get update -qq
    for pkg in "python${pyver}-venv" python3-venv; do
        echo "    trying apt-get install $pkg"
        if apt-get install -y -qq "$pkg" >/dev/null 2>&1; then
            if python3 -c "import ensurepip" >/dev/null 2>&1; then
                echo "    installed $pkg"
                return 0
            fi
        fi
    done
    echo "error: could not install a working python3 venv package." >&2
    echo "       try manually:  apt install python${pyver}-venv" >&2
    exit 1
}

echo "==> checking prerequisites"
missing=()
command -v python3 >/dev/null 2>&1 || missing+=("python3")
command -v useradd >/dev/null 2>&1 || missing+=("useradd (passwd package)")
command -v systemctl >/dev/null 2>&1 || missing+=("systemctl (systemd)")
if (( ${#missing[@]} )); then
    echo "error: missing prerequisites: ${missing[*]}" >&2
    exit 1
fi
ensure_venv_support

echo "==> creating service user and directories"
id -u crashmon >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin crashmon
mkdir -p "$APP_DIR" "$DATA_DIR"

echo "==> installing application to $APP_DIR"
# Copy the package, not the whole repo.
rm -rf "${APP_DIR:?}/crashmon"
cp -r "$SRC_DIR/crashmon" "$APP_DIR/crashmon"
cp "$SRC_DIR/requirements.txt" "$APP_DIR/"

echo "==> creating virtualenv"
# A run that died inside ensurepip leaves a venv skeleton holding an
# interpreter but no pip, so testing only for bin/python would treat that
# wreckage as a usable environment and fail later on a missing pip. Require
# both, and rebuild from scratch when either is absent.
if [[ ! -x "$APP_DIR/venv/bin/python" || ! -x "$APP_DIR/venv/bin/pip" ]]; then
    rm -rf "${APP_DIR:?}/venv"
    if ! python3 -m venv "$APP_DIR/venv"; then
        echo "error: virtualenv creation failed in $APP_DIR/venv." >&2
        exit 1
    fi
fi
if [[ ! -x "$APP_DIR/venv/bin/pip" ]]; then
    echo "error: virtualenv has no pip even after a clean rebuild." >&2
    echo "       check that python3 -m ensurepip works on this host." >&2
    exit 1
fi
"$APP_DIR/venv/bin/pip" install --quiet --upgrade pip
"$APP_DIR/venv/bin/pip" install --quiet -r "$APP_DIR/requirements.txt"

chown -R crashmon:crashmon "$APP_DIR" "$DATA_DIR"

echo "==> verifying the live chain before enabling the service"
# python -m resolves packages from the current directory, which here is the
# checkout rather than $APP_DIR - and the service user cannot read it anyway.
# Name the location explicitly instead of depending on where this was invoked.
if ! sudo -u crashmon env PYTHONPATH="$APP_DIR" \
        "$APP_DIR/venv/bin/python" -m crashmon.collector --selftest; then
    echo "error: selftest failed — not enabling the service." >&2
    echo "       the endpoints may have moved; see README troubleshooting." >&2
    exit 1
fi

echo "==> installing crashmon-report helper"
cat > /usr/local/bin/crashmon-report <<WRAPPER
#!/bin/sh
# Report on the collected rounds. Extra arguments pass through to the module,
# e.g. crashmon-report --json  or  crashmon-report --thresholds 2,5,50
exec env PYTHONPATH="$APP_DIR" "$APP_DIR/venv/bin/python" -m crashmon.analyze \\
    --db "$DATA_DIR/crash.sqlite3" "\$@"
WRAPPER
chmod 0755 /usr/local/bin/crashmon-report

echo "==> installing systemd unit"
install -m 0644 "$SRC_DIR/deploy/$SERVICE.service" "/etc/systemd/system/$SERVICE.service"
systemctl daemon-reload
systemctl enable "$SERVICE"
systemctl restart "$SERVICE"

sleep 2
systemctl --no-pager --lines=15 status "$SERVICE" || true

cat <<NOTE

Installed.

  logs      journalctl -u $SERVICE -f
  database  $DATA_DIR/crash.sqlite3
  report    crashmon-report

At roughly 20s per round expect about 4,300 rounds a day; a day is already
enough to pin the below-2x rate to well under a percentage point.
NOTE
