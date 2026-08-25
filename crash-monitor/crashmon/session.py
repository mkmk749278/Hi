"""Obtain a read-only demo session on the crash game.

Chain, none of which involves a user account:

    POST <casino>/api/CASINO-3/games-one  {"id": ..., "isDemo": true}
        -> gameUrl carrying a launch token in its ``b`` query parameter
    POST <gateway>/user/auth              Auth-Token: <launch token>
        -> sessionId / customerId, identified as "demo_user"
    POST <gateway>/user/token             Session-Id / Customer-Id
        -> Centrifugo JWTs; the main token grants the global round channel

The round channel is shared by every player on the game, so a demo session
observes exactly the rounds real-money players see. It is a read credential and
nothing more: this module never places, cancels, or cashes out a bet.
"""

from __future__ import annotations

import base64
import json
import logging
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field

from . import config

log = logging.getLogger(__name__)


class SessionError(RuntimeError):
    """The demo session could not be established."""


def _post(url: str, headers: dict | None = None, body: dict | None = None) -> dict:
    payload = json.dumps(body).encode() if body is not None else None
    hdrs = {
        "user-agent": config.USER_AGENT,
        "accept": "application/json, text/plain, */*",
    }
    if payload is not None:
        hdrs["content-type"] = "application/json"
    hdrs.update(headers or {})
    req = urllib.request.Request(url, method="POST", data=payload, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=config.HTTP_TIMEOUT) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        detail = exc.read()[:400].decode(errors="replace")
        raise SessionError(f"POST {url} -> HTTP {exc.code}: {detail}") from exc
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise SessionError(f"POST {url} failed: {exc}") from exc


def _jwt_expiry(token: str) -> float | None:
    """Read ``exp`` out of a JWT without verifying it (we are not the audience)."""
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return float(json.loads(base64.urlsafe_b64decode(payload))["exp"])
    except Exception:  # a token we cannot read is simply refreshed on schedule
        return None


@dataclass
class Session:
    session_id: str
    customer_id: str
    user_name: str
    mode: str
    main_token: str
    expires_at: float | None = None
    channels: list[str] = field(default_factory=list)

    @property
    def headers(self) -> dict:
        return {"Session-Id": self.session_id, "Customer-Id": self.customer_id}


def _channels_of(token: str) -> list[str]:
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return list(json.loads(base64.urlsafe_b64decode(payload)).get("channels", []))
    except Exception:
        return []


def open_session() -> Session:
    """Run the full launch -> auth -> token chain and return a live session."""
    launch = _post(
        config.LAUNCH_URL,
        headers={"referer": f"{config.GAME_ORIGIN}/"},
        body={"id": config.GAME_ID, "isDemo": True},
    )
    game_url = launch.get("gameUrl")
    if not game_url:
        raise SessionError(f"launch response carried no gameUrl: {str(launch)[:200]}")

    query = urllib.parse.parse_qs(urllib.parse.urlparse(game_url).query)
    launch_token = (query.get("b") or [None])[0]
    if not launch_token:
        raise SessionError("gameUrl carried no 'b' launch token")

    gw = f"https://{config.GATEWAY}"
    auth = _post(
        f"{gw}/user/auth",
        headers={"Auth-Token": launch_token, "origin": config.GAME_ORIGIN},
    )
    for key in ("sessionId", "customerId"):
        if not auth.get(key):
            raise SessionError(f"auth response missing {key}: {str(auth)[:200]}")

    headers = {
        "Session-Id": auth["sessionId"],
        "Customer-Id": auth["customerId"],
        "origin": config.GAME_ORIGIN,
    }
    tokens = _post(f"{gw}/user/token", headers=headers)
    main_token = (tokens.get("centrifugo") or {}).get("mainToken")
    if not main_token:
        raise SessionError(f"token response missing mainToken: {str(tokens)[:200]}")

    session = Session(
        session_id=auth["sessionId"],
        customer_id=auth["customerId"],
        user_name=auth.get("userName", "?"),
        mode=auth.get("mode", "?"),
        main_token=main_token,
        expires_at=_jwt_expiry(main_token),
        channels=_channels_of(main_token),
    )
    log.info(
        "demo session open: user=%s mode=%s channels=%s",
        session.user_name,
        session.mode,
        ",".join(session.channels) or "(none)",
    )
    if session.mode != "demo":
        # Refuse to run against anything but a demo session. This collector is
        # read-only by design and must never attach to a funded account.
        raise SessionError(
            f"expected a demo session, gateway returned mode={session.mode!r}"
        )
    return session


def fetch_last_round(session: Session) -> dict:
    """One-shot REST read of the most recent round. Used by ``--selftest``."""
    url = f"https://{config.GATEWAY}/history/last"
    req = urllib.request.Request(
        url,
        headers={
            "user-agent": config.USER_AGENT,
            "accept": "application/json",
            "origin": config.GAME_ORIGIN,
            **session.headers,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=config.HTTP_TIMEOUT) as resp:
            return json.loads(resp.read().decode())
    except Exception as exc:
        raise SessionError(f"GET {url} failed: {exc}") from exc
