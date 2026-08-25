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

echo "==> creating service user and directories"
id -u crashmon >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin crashmon
mkdir -p "$APP_DIR" "$DATA_DIR"

echo "==> installing application to $APP_DIR"
# Copy the package, not the whole repo.
rm -rf "${APP_DIR:?}/crashmon"
cp -r "$SRC_DIR/crashmon" "$APP_DIR/crashmon"
cp "$SRC_DIR/requirements.txt" "$APP_DIR/"

echo "==> creating virtualenv"
if [[ ! -x "$APP_DIR/venv/bin/python" ]]; then
    python3 -m venv "$APP_DIR/venv"
fi
"$APP_DIR/venv/bin/pip" install --quiet --upgrade pip
"$APP_DIR/venv/bin/pip" install --quiet -r "$APP_DIR/requirements.txt"

chown -R crashmon:crashmon "$APP_DIR" "$DATA_DIR"

echo "==> verifying the live chain before enabling the service"
if ! sudo -u crashmon "$APP_DIR/venv/bin/python" -m crashmon.collector --selftest; then
    echo "error: selftest failed — not enabling the service." >&2
    echo "       the endpoints may have moved; see README troubleshooting." >&2
    exit 1
fi

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
  report    $APP_DIR/venv/bin/python -m crashmon.analyze --db $DATA_DIR/crash.sqlite3

At roughly 20s per round expect about 4,300 rounds a day; a day is already
enough to pin the below-2x rate to well under a percentage point.
NOTE
