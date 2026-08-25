"""Long-running collector: Centrifugo round stream -> SQLite.

Subscribes to the global crash channel and records one row per completed round.
It listens only; it never sends a game command.

Why a websocket rather than polling ``/history/last``: high multipliers take
longer to play out than low ones, so any sampler slower than the round cadence
catches the long rounds preferentially and inflates the measured high-multiplier
rate. The push stream delivers every round exactly once, which is the only way
the resulting distribution is unbiased.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import json
import logging
import random
import signal
import time

import websockets

from . import config, db, verify
from .session import Session, SessionError, fetch_last_round, open_session

log = logging.getLogger("crashmon.collector")

# Emitted ~100x per round as the multiplier ticks up. Useless for our purposes
# and by far the bulk of the traffic, so it is dropped before any parsing work.
NOISE_EVENTS = {"changeCoefficient"}


class Collector:
    def __init__(self, db_path: str, stop: asyncio.Event) -> None:
        self.conn = db.connect(db_path)
        self.stop = stop
        self.session: Session | None = None
        self.pending: dict | None = None   # round announced but not yet finished
        self.stored = 0
        self.duplicates = 0

    # -- session ---------------------------------------------------------

    def _session_stale(self) -> bool:
        if self.session is None:
            return True
        exp = self.session.expires_at
        return exp is not None and exp - time.time() < config.TOKEN_REFRESH_MARGIN

    async def ensure_session(self) -> Session:
        if self._session_stale():
            self.session = await asyncio.to_thread(open_session)
        assert self.session is not None
        return self.session

    # -- event handling --------------------------------------------------

    def on_start(self, data: dict) -> None:
        info = data.get("roundInfo") or {}
        fair = info.get("provablyFair") or {}
        round_id = info.get("id")
        if not round_id:
            return
        self.pending = {
            "round_id": round_id,
            "started_at": data.get("currentTime"),
            "rtp": info.get("rtp"),
            "algorithm": fair.get("algorithm"),
            # Committed now; the salt that opens it arrives only at endGame.
            "committed_hash": fair.get("hash"),
        }

    def on_end(self, data: dict) -> None:
        fair = data.get("provablyFair") or {}
        values = data.get("finalCoefficientValues") or []
        check_string = fair.get("checkString")

        parsed = verify.parse_check_string(check_string)
        pending = self.pending or {}

        # Prefer the id we saw announced; fall back to the revealed pre-image so
        # a collector that starts mid-round still records that round.
        round_id = pending.get("round_id") or (parsed or {}).get("round_id")
        if not round_id:
            log.warning("round ended with no identifiable id; skipped")
            self.pending = None
            return

        crash = float(values[0]) if values else None
        if crash is None and parsed and parsed["values"]:
            crash = parsed["values"][0]
        if crash is None:
            log.warning("round %s ended with no crash value; skipped", round_id)
            self.pending = None
            return

        published_hash = fair.get("hash")
        algorithm = fair.get("algorithm") or pending.get("algorithm") or "SHA512"
        verified = verify.verify_round(check_string, published_hash, algorithm)

        problems = verify.cross_check(check_string, round_id, crash)
        committed = pending.get("committed_hash")
        if committed and published_hash and committed != published_hash:
            # The hash revealed at the end is not the one promised at the start:
            # the commitment was swapped mid-round.
            problems.append("hash changed between startGame and endGame")

        row = {
            "round_id": round_id,
            "crash": crash,
            "final_values": json.dumps(values) if values else None,
            "started_at": pending.get("started_at"),
            "ended_at": data.get("currentTime"),
            "rtp": pending.get("rtp"),
            "algorithm": algorithm,
            "hash": published_hash,
            "salt": fair.get("salt"),
            "check_string": check_string,
            "verified": None if verified is None else int(verified),
            "anomaly": "; ".join(problems) if problems else None,
        }

        if db.insert_round(self.conn, row):
            self.stored += 1
            if verified is False:
                log.error("PROVABLY-FAIR MISMATCH on round %s", round_id)
            if problems:
                log.error("round %s anomaly: %s", round_id, "; ".join(problems))
            log.info(
                "round %s crash=%.2fx verified=%s (%d stored)",
                round_id, crash,
                {True: "yes", False: "NO", None: "n/a"}[verified],
                self.stored,
            )
        else:
            self.duplicates += 1
        self.pending = None

    def dispatch(self, data: dict) -> None:
        event = data.get("eventType")
        if event in NOISE_EVENTS or not event:
            return
        if event == "startGame":
            self.on_start(data)
        elif event == "endGame":
            self.on_end(data)

    # -- connection ------------------------------------------------------

    async def run_once(self) -> None:
        session = await self.ensure_session()
        channels = session.channels or ["(server-assigned)"]
        uri = f"wss://{config.GATEWAY}/websocket/lifecycle"

        kwargs = {"origin": config.GAME_ORIGIN, "user_agent_header": config.USER_AGENT}
        # Honour a proxy if the host uses one; direct otherwise.
        import os
        proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
        if proxy:
            kwargs["proxy"] = proxy

        async with websockets.connect(uri, **kwargs) as ws:
            # The token already carries the channel grant, so Centrifugo
            # subscribes us on connect; no explicit subscribe is needed.
            await ws.send(json.dumps(
                {"id": 1, "connect": {"token": session.main_token, "name": "crashmon"}}
            ))
            log.info("connected to %s, channel(s): %s", config.GATEWAY, ",".join(channels))
            uptime_id = db.open_uptime(self.conn)
            start_count = self.stored
            reason = "stopped"
            try:
                while not self.stop.is_set():
                    try:
                        raw = await asyncio.wait_for(ws.recv(), timeout=config.RECV_TIMEOUT)
                    except asyncio.TimeoutError:
                        reason = "no data within recv timeout"
                        log.warning("%s; reconnecting", reason)
                        break
                    if raw is None:
                        continue
                    text = raw.decode() if isinstance(raw, bytes) else raw
                    if not text.strip() or text.strip() == "{}":
                        await ws.send("{}")   # Centrifugo ping -> pong
                        continue
                    # A frame may carry several newline-delimited envelopes.
                    for line in text.splitlines():
                        line = line.strip()
                        if not line:
                            continue
                        try:
                            envelope = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        if "error" in envelope:
                            log.warning("gateway error: %s", str(envelope["error"])[:200])
                            continue
                        data = (envelope.get("push", {}).get("pub", {}) or {}).get("data")
                        if isinstance(data, dict):
                            self.dispatch(data)
            finally:
                db.close_uptime(self.conn, uptime_id, self.stored - start_count, reason)

    async def run_forever(self) -> None:
        backoff = config.BACKOFF_MIN
        while not self.stop.is_set():
            try:
                await self.run_once()
                backoff = config.BACKOFF_MIN
            except asyncio.CancelledError:
                raise
            except (SessionError, OSError, websockets.WebSocketException) as exc:
                log.warning("connection problem: %s", str(exc)[:300])
            except Exception:
                log.exception("unexpected collector error")
            if self.stop.is_set():
                break
            # Jitter so a gateway restart does not get a synchronised retry.
            delay = min(backoff, config.BACKOFF_MAX) * (0.5 + random.random())
            log.info("reconnecting in %.1fs", delay)
            with contextlib.suppress(asyncio.TimeoutError):
                await asyncio.wait_for(self.stop.wait(), timeout=delay)
            backoff = min(backoff * 2, config.BACKOFF_MAX)
        log.info("stopped: %d rounds stored, %d duplicates ignored",
                 self.stored, self.duplicates)


async def _selftest() -> int:
    session = await asyncio.to_thread(open_session)
    print(f"  session   : {session.user_name} (mode={session.mode})")
    print(f"  channels  : {', '.join(session.channels) or '(none)'}")
    last = await asyncio.to_thread(fetch_last_round, session)
    rnd = last.get("round") or {}
    print(f"  last round: {rnd.get('id')} crash={rnd.get('topCoefficient')}")
    print("  websocket : connecting, waiting for one complete round…")

    stop = asyncio.Event()
    collector = Collector(":memory:", stop)
    collector.session = session
    task = asyncio.create_task(collector.run_forever())
    for _ in range(120):
        if collector.stored:
            break
        await asyncio.sleep(1)
    stop.set()
    task.cancel()
    with contextlib.suppress(asyncio.CancelledError):
        await task
    if not collector.stored:
        print("  FAILED: no round captured within 120s")
        return 1
    row = collector.conn.execute(
        "SELECT round_id, crash, verified, anomaly FROM rounds LIMIT 1"
    ).fetchone()
    print(f"  captured  : {row['round_id']} crash={row['crash']}x "
          f"verified={'yes' if row['verified'] else 'NO'}")
    if row["anomaly"]:
        print(f"  anomaly   : {row['anomaly']}")
    print("  OK")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Collect 1win crash round history.")
    parser.add_argument("--db", default=config.DB_PATH, help="SQLite path")
    parser.add_argument("--selftest", action="store_true",
                        help="verify the whole chain against one live round, then exit")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(message)s",
    )

    if args.selftest:
        return asyncio.run(_selftest())

    async def runner() -> None:
        stop = asyncio.Event()
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            with contextlib.suppress(NotImplementedError):
                loop.add_signal_handler(sig, stop.set)
        collector = Collector(args.db, stop)
        existing = db.count_rounds(collector.conn)
        log.info("database %s holds %d rounds", args.db, existing)
        await collector.run_forever()

    asyncio.run(runner())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
