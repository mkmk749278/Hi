"""Endpoints and tunables, all overridable by environment variable."""

import os

# Casino front end that mints a demo game session. No account is involved.
LAUNCH_URL = os.environ.get(
    "CRASHMON_LAUNCH_URL", "https://1wmhsu.life/api/CASINO-3/games-one"
)
GAME_ID = os.environ.get("CRASHMON_GAME_ID", "v_1wingames:crash")

# Game provider gateway. Discovered from the game's own config.json; override if
# the operator moves the game to a different cluster.
GATEWAY = os.environ.get("CRASHMON_GATEWAY", "crash-gateway-grm-cr.gamedev-tech.cc")

# The provider serves the game from this origin; the gateway checks it.
GAME_ORIGIN = os.environ.get("CRASHMON_GAME_ORIGIN", "https://1play.gamedev-tech.cc")

USER_AGENT = os.environ.get(
    "CRASHMON_USER_AGENT",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
)

DB_PATH = os.environ.get("CRASHMON_DB", "crash.sqlite3")

HTTP_TIMEOUT = float(os.environ.get("CRASHMON_HTTP_TIMEOUT", "30"))

# Centrifugo issues tokens with a ~3 day TTL. Re-auth well before that so a
# long-lived collector never gets dropped mid-round for an expired token.
TOKEN_REFRESH_MARGIN = float(os.environ.get("CRASHMON_TOKEN_MARGIN", str(6 * 3600)))

# Reconnect backoff bounds, seconds.
BACKOFF_MIN = float(os.environ.get("CRASHMON_BACKOFF_MIN", "2"))
BACKOFF_MAX = float(os.environ.get("CRASHMON_BACKOFF_MAX", "300"))

# If no message arrives in this long the connection is considered dead. The
# server pings every 25s, so a minute of silence is already abnormal.
RECV_TIMEOUT = float(os.environ.get("CRASHMON_RECV_TIMEOUT", "90"))
