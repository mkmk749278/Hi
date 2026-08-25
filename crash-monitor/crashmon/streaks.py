"""Run-length analysis: how long the game stays below a multiplier.

Under the model P(X >= x) = c/x, each round independently falls below a
threshold t with probability p = 1 - c/t. Runs of such rounds therefore follow
the geometry of a coin: a run of at least k has probability p**k, and in n
rounds the expected number of runs of length at least k is about n*p**k*(1-p).

The longest run is the statistic people actually notice, and it grows only
logarithmically with how long you watch:

    E[longest run] ~ log(n * (1 - p)) / log(1 / p)

which is why a long streak feels extraordinary and is not. None of this makes a
streak predictive: after k losses the next round is still p to lose, because the
rounds are independent draws against seeds committed in advance.

Streaks must not be counted across a collection gap. If the collector was
disconnected, two adjacent database rows are not adjacent game rounds, and
splicing them together would invent runs that never happened. Sequences are
therefore split wherever the spacing between rounds implies a missed one.
"""

from __future__ import annotations

import math
from datetime import datetime

# Rounds land roughly every 20s and have been observed as far apart as 31s.
# Anything beyond this means the collector missed rounds in between.
DEFAULT_MAX_GAP_SECONDS = 120.0


def _parse(ts: str | None) -> datetime | None:
    if not ts:
        return None
    try:
        return datetime.fromisoformat(ts.replace("Z", "+00:00"))
    except ValueError:
        return None


def segment(series: list[tuple[float, str | None]],
            max_gap: float = DEFAULT_MAX_GAP_SECONDS) -> list[list[float]]:
    """Split (crash, ended_at) pairs into runs of genuinely consecutive rounds."""
    segments: list[list[float]] = []
    current: list[float] = []
    previous: datetime | None = None

    for crash, ended_at in series:
        stamp = _parse(ended_at)
        if current and previous is not None and stamp is not None:
            if (stamp - previous).total_seconds() > max_gap:
                segments.append(current)
                current = []
        current.append(crash)
        if stamp is not None:
            previous = stamp
    if current:
        segments.append(current)
    return segments


def run_lengths(segments: list[list[float]], threshold: float) -> list[int]:
    """Lengths of every maximal run of rounds crashing below ``threshold``."""
    runs: list[int] = []
    for chunk in segments:
        length = 0
        for crash in chunk:
            if crash < threshold:
                length += 1
            elif length:
                runs.append(length)
                length = 0
        if length:
            runs.append(length)
    return runs


def expected_longest(n: int, p: float) -> float | None:
    """Expected longest run of below-threshold rounds over n rounds."""
    if n <= 0 or not 0 < p < 1:
        return None
    value = n * (1 - p)
    if value <= 1:
        return None
    return math.log(value) / math.log(1 / p)


def analyse_threshold(segments: list[list[float]], threshold: float,
                      c: float) -> dict:
    n = sum(len(chunk) for chunk in segments)
    p = max(0.0, min(1.0, 1 - c / threshold))
    runs = run_lengths(segments, threshold)
    longest = max(runs) if runs else 0

    # Observed vs expected counts of runs reaching each length.
    buckets = []
    for k in (3, 5, 7, 10, 12, 15, 20, 25, 30):
        observed = sum(1 for r in runs if r >= k)
        expected = n * (p ** k) * (1 - p) if 0 < p < 1 else 0.0
        buckets.append({"k": k, "observed": observed, "expected": expected,
                        "probability": p ** k})

    return {
        "threshold": threshold,
        "p_below": p,
        "n": n,
        "runs": len(runs),
        "longest": longest,
        "expected_longest": expected_longest(n, p),
        "mean_run": sum(runs) / len(runs) if runs else 0.0,
        "buckets": buckets,
        "segments": len(segments),
    }
