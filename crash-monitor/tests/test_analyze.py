"""Statistical tests: the estimator must recover an edge we planted ourselves."""

import math
import random

import pytest
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from crashmon import analyze


def simulate(c: float, n: int, seed: int = 7) -> list[float]:
    """Draw n rounds from the model P(X >= x) = c/x, rounded like the real game."""
    rng = random.Random(seed)
    out = []
    for _ in range(n):
        u = rng.random()
        x = c / u
        out.append(max(1.00, math.floor(x * 100) / 100))
    return out


def test_recovers_known_edge():
    crashes = simulate(0.97, 200_000)
    est = analyze.estimate_c(crashes)
    assert abs(est["c_hat"] - 0.97) < 0.01
    assert est["c_lo"] <= 0.97 <= est["c_hi"]


def test_recovers_a_different_edge():
    # A game shorting players at 90% RTP must not be reported as 97%.
    crashes = simulate(0.90, 200_000)
    est = analyze.estimate_c(crashes)
    assert abs(est["c_hat"] - 0.90) < 0.01
    assert not (est["c_lo"] <= 0.97 <= est["c_hi"])


def test_threshold_estimates_agree():
    crashes = simulate(0.97, 200_000)
    table = analyze.threshold_table(crashes, [2.0, 5.0, 10.0])
    for row in table:
        assert abs(row["c_hat"] - 0.97) < 0.05


def test_tail_fit_accepts_conforming_data():
    fit = analyze.tail_fit(simulate(0.97, 50_000))
    assert fit["ks_p"] > 0.01


def test_tail_fit_rejects_truncated_tail():
    # An operator secretly capping the multiplier at 20x should be detected.
    crashes = [min(20.0, c) for c in simulate(0.97, 50_000)]
    fit = analyze.tail_fit(crashes)
    assert fit["ks_p"] < 0.01


def test_wilson_interval_bounds():
    lo, hi = analyze.wilson_interval(0, 1000)
    assert lo == pytest.approx(0.0, abs=1e-12) and 0 < hi < 0.01
    lo, hi = analyze.wilson_interval(500, 1000)
    assert lo < 0.5 < hi


def test_ks_uniform_on_uniform_data():
    rng = random.Random(1)
    d, p = analyze.ks_uniform([rng.random() for _ in range(5000)])
    assert p > 0.01


def test_render_handles_empty_database():
    assert "No rounds" in analyze.render({"empty": True})


def _db_with(rows):
    """In-memory database holding the given (crash, ended_at, verified) rows."""
    from crashmon import db
    conn = db.connect(":memory:")
    for i, (crash, ended_at, verified) in enumerate(rows):
        db.insert_round(conn, {
            "round_id": f"{i:08d}-0000-0000-0000-000000000000",
            "crash": crash, "final_values": None, "started_at": ended_at,
            "ended_at": ended_at, "rtp": 0.97, "algorithm": "SHA512",
            "hash": None, "salt": None, "check_string": None,
            "verified": verified, "anomaly": None,
        })
    return conn


def test_recent_is_newest_first_and_limited():
    conn = _db_with([
        (1.10, "2026-08-25T12:00:00+00:00", 1),
        (2.20, "2026-08-25T12:00:20+00:00", 1),
        (3.30, "2026-08-25T12:00:40+00:00", 1),
    ])
    out = analyze.render_recent(conn, 2)
    lines = [l for l in out.splitlines() if "x  " in l or "x " in l]
    # Newest (3.30) must appear before 2.20, and 1.10 must be excluded.
    assert out.index("3.30") < out.index("2.20")
    assert "1.10" not in out


def test_recent_marks_a_failed_verification():
    conn = _db_with([(1.50, "2026-08-25T12:00:00+00:00", 0)])
    assert "BAD" in analyze.render_recent(conn, 5)


def test_recent_handles_empty_database():
    conn = _db_with([])
    assert "No rounds" in analyze.render_recent(conn, 10)


def test_recent_asking_for_more_than_exists():
    conn = _db_with([(1.50, "2026-08-25T12:00:00+00:00", 1)])
    out = analyze.render_recent(conn, 50)
    assert "Last 1 rounds" in out
