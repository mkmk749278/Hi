"""SQLite storage.

One row per round, keyed by the provider's own round id so a reconnect that
replays an already-seen round cannot double count. ``uptime`` records when the
collector was actually connected, which is what lets the analysis distinguish
"the game produced no high multiplier" from "we were not watching".
"""

from __future__ import annotations

import sqlite3
from datetime import datetime, timezone

SCHEMA = """
CREATE TABLE IF NOT EXISTS rounds (
    round_id     TEXT PRIMARY KEY,
    crash        REAL NOT NULL,
    final_values TEXT,
    started_at   TEXT,
    ended_at     TEXT,
    rtp          REAL,
    algorithm    TEXT,
    hash         TEXT,
    salt         TEXT,
    check_string TEXT,
    verified     INTEGER,
    anomaly      TEXT,
    collected_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rounds_ended ON rounds(ended_at);
CREATE INDEX IF NOT EXISTS idx_rounds_crash ON rounds(crash);
CREATE INDEX IF NOT EXISTS idx_rounds_verified ON rounds(verified);

CREATE TABLE IF NOT EXISTS uptime (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    connected_at  TEXT NOT NULL,
    disconnected_at TEXT,
    rounds        INTEGER NOT NULL DEFAULT 0,
    reason        TEXT
);

CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT
);
"""


def utcnow() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def connect(path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(path, timeout=30)
    conn.row_factory = sqlite3.Row
    # WAL keeps the analysis script readable while the collector is writing.
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.executescript(SCHEMA)
    conn.commit()
    return conn


def insert_round(conn: sqlite3.Connection, row: dict) -> bool:
    """Insert a round. Returns False if it was already stored."""
    cur = conn.execute(
        """
        INSERT OR IGNORE INTO rounds
            (round_id, crash, final_values, started_at, ended_at, rtp,
             algorithm, hash, salt, check_string, verified, anomaly, collected_at)
        VALUES
            (:round_id, :crash, :final_values, :started_at, :ended_at, :rtp,
             :algorithm, :hash, :salt, :check_string, :verified, :anomaly, :collected_at)
        """,
        {**row, "collected_at": utcnow()},
    )
    conn.commit()
    return cur.rowcount > 0


def open_uptime(conn: sqlite3.Connection) -> int:
    cur = conn.execute(
        "INSERT INTO uptime (connected_at) VALUES (?)", (utcnow(),)
    )
    conn.commit()
    return int(cur.lastrowid)


def close_uptime(conn: sqlite3.Connection, uptime_id: int, rounds: int,
                 reason: str) -> None:
    conn.execute(
        "UPDATE uptime SET disconnected_at=?, rounds=?, reason=? WHERE id=?",
        (utcnow(), rounds, reason[:200], uptime_id),
    )
    conn.commit()


def count_rounds(conn: sqlite3.Connection) -> int:
    return int(conn.execute("SELECT COUNT(*) FROM rounds").fetchone()[0])
