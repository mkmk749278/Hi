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
