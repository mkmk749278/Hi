"""Streak analysis: run counting, gap handling, and the longest-run estimate."""

import math
import random
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from crashmon import streaks


def ts(second: int) -> str:
    return f"2026-08-25T12:{second // 60:02d}:{second % 60:02d}+00:00"


def test_run_lengths_basic():
    # below 2x: 1.5, 1.2 | 1.1 1.3 1.4 | trailing 1.9
    seq = [1.5, 1.2, 9.0, 1.1, 1.3, 1.4, 5.0, 1.9]
    assert streaks.run_lengths([seq], 2.0) == [2, 3, 1]


def test_run_at_end_is_counted():
    assert streaks.run_lengths([[9.0, 1.1, 1.2]], 2.0) == [2]


def test_no_runs_when_nothing_below():
    assert streaks.run_lengths([[3.0, 4.0, 5.0]], 2.0) == []


def test_threshold_is_strict_below():
    # exactly 2.00 is not "below 2x"
    assert streaks.run_lengths([[2.0, 2.0]], 2.0) == []


def test_segment_splits_on_gap():
    # Two rounds 20s apart, then a 10 minute hole, then two more.
    series = [(1.1, ts(0)), (1.2, ts(20)), (1.3, ts(620)), (1.4, ts(640))]
    segs = streaks.segment(series)
    assert len(segs) == 2
    assert segs[0] == [1.1, 1.2] and segs[1] == [1.3, 1.4]


def test_gap_prevents_inventing_a_streak():
    # All four are below 2x, but a gap sits in the middle: the real evidence is
    # two runs of two, never one run of four.
    series = [(1.1, ts(0)), (1.2, ts(20)), (1.3, ts(620)), (1.4, ts(640))]
    segs = streaks.segment(series)
    assert streaks.run_lengths(segs, 2.0) == [2, 2]
    # Without segmenting, the same data would claim a run of four.
    assert streaks.run_lengths([[c for c, _ in series]], 2.0) == [4]


def test_normal_cadence_is_not_split():
    series = [(1.1, ts(0)), (1.2, ts(21)), (1.3, ts(52)), (1.4, ts(75))]
    assert len(streaks.segment(series)) == 1


def test_missing_timestamps_do_not_split():
    series = [(1.1, None), (1.2, None), (1.3, None)]
    assert streaks.segment(series) == [[1.1, 1.2, 1.3]]


def test_expected_longest_matches_simulation():
    # p = 0.515 is the below-2x rate at the game's advertised 97% RTP.
    p, n, trials = 0.515, 5000, 200
    rng = random.Random(11)
    observed = []
    for _ in range(trials):
        seq = [1.0 if rng.random() < p else 9.0 for _ in range(n)]
        runs = streaks.run_lengths([seq], 2.0)
        observed.append(max(runs) if runs else 0)
    mean_observed = sum(observed) / len(observed)
    predicted = streaks.expected_longest(n, p)
    # The asymptotic formula is good to about a round at this scale.
    assert abs(mean_observed - predicted) < 1.5, (mean_observed, predicted)


def test_expected_longest_degenerate_inputs():
    assert streaks.expected_longest(0, 0.5) is None
    assert streaks.expected_longest(100, 0.0) is None
    assert streaks.expected_longest(100, 1.0) is None


def test_analyse_threshold_shape():
    seq = [1.1, 1.2, 5.0, 1.3, 1.4, 1.5, 9.0]
    out = streaks.analyse_threshold([seq], 2.0, 0.97)
    assert out["longest"] == 3
    assert out["runs"] == 2
    assert out["n"] == 7
    assert 0 < out["p_below"] < 1
