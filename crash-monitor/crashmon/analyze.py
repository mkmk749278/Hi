"""Turn collected rounds into the numbers the project actually wanted.

The model. Crash games in this family draw the multiplier so that

    P(X >= x) = c / x        for x >= 1,   with  c = 1 - house edge

and clamp anything below 1 to an instant bust at 1.00. The game publishes
``rtp: 0.97`` in its own startGame event, i.e. it claims c = 0.97.

Two things follow, and they are worth stating plainly because they decide what
this data can and cannot tell you:

1. A player who always cashes out at target t wins t with probability c/t, so the
   expected return is t * (c/t) = c whatever t is. Cash-out strategy cannot move
   the return; only c matters. Measuring c *is* measuring the RTP.

2. Conditioned on X >= x0, the law P(X >= x | X >= x0) = x0/x has no free
   parameter at all. So the tail shape is a clean goodness-of-fit test: if the
   observed tail departs from it, the game is not drawing from the stated family.

Rounds are independent draws, so nothing here predicts the next round, and this
module deliberately reports no such quantity.
"""

from __future__ import annotations

import argparse
import json
import math
import sqlite3

from . import db, streaks

DEFAULT_THRESHOLDS = [1.2, 1.5, 2.0, 3.0, 5.0, 10.0, 20.0, 50.0, 100.0]
PUBLISHED_RTP = 0.97

# Below this many rounds the interval on c is too wide to say anything useful,
# so the report says so rather than letting a stray point estimate be quoted.
MIN_USEFUL_ROUNDS = 1000


# -- small statistics helpers (stdlib only) --------------------------------

def wilson_interval(successes: int, n: int, z: float = 1.959964) -> tuple[float, float]:
    """Wilson score interval: behaves sensibly for the rare high thresholds
    where the normal approximation would run off the end of [0, 1]."""
    if n == 0:
        return (0.0, 1.0)
    p = successes / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def kolmogorov_p(d: float, n: int) -> float:
    """Asymptotic p-value for a KS statistic (Marsaglia's series)."""
    if n == 0 or d <= 0:
        return 1.0
    lam = (math.sqrt(n) + 0.12 + 0.11 / math.sqrt(n)) * d
    total = 0.0
    for k in range(1, 101):
        total += (-1) ** (k - 1) * math.exp(-2.0 * k * k * lam * lam)
    return max(0.0, min(1.0, 2.0 * total))


def ks_uniform(values: list[float]) -> tuple[float, float]:
    """KS test of ``values`` against Uniform(0, 1)."""
    n = len(values)
    if n == 0:
        return (0.0, 1.0)
    ordered = sorted(values)
    d = 0.0
    for i, u in enumerate(ordered):
        d = max(d, (i + 1) / n - u, u - i / n)
    return d, kolmogorov_p(d, n)


# -- loading ---------------------------------------------------------------

def load(conn: sqlite3.Connection) -> dict:
    rows = conn.execute(
        "SELECT crash, ended_at, rtp, verified, anomaly FROM rounds "
        "WHERE crash IS NOT NULL ORDER BY ended_at"
    ).fetchall()
    crashes = [float(r["crash"]) for r in rows]
    series = [(float(r["crash"]), r["ended_at"]) for r in rows]
    stamps = [r["ended_at"] for r in rows if r["ended_at"]]
    verified = [r["verified"] for r in rows]
    return {
        "crashes": crashes,
        "series": series,
        "n": len(crashes),
        "first": stamps[0] if stamps else None,
        "last": stamps[-1] if stamps else None,
        "verified_ok": sum(1 for v in verified if v == 1),
        "verified_bad": sum(1 for v in verified if v == 0),
        "verified_na": sum(1 for v in verified if v is None),
        "anomalies": [r["anomaly"] for r in rows if r["anomaly"]],
        "claimed_rtp": next((float(r["rtp"]) for r in rows if r["rtp"] is not None),
                            PUBLISHED_RTP),
    }


# -- the analysis ----------------------------------------------------------

def threshold_table(crashes: list[float], thresholds: list[float]) -> list[dict]:
    n = len(crashes)
    out = []
    for x in thresholds:
        at_or_above = sum(1 for c in crashes if c >= x)
        p = at_or_above / n if n else 0.0
        lo, hi = wilson_interval(at_or_above, n)
        out.append({
            "threshold": x,
            "count_at_or_above": at_or_above,
            "p_at_or_above": p,
            "p_below": 1 - p,
            # Each threshold gives an independent read on c.
            "c_hat": x * p,
            "c_lo": x * lo,
            "c_hi": x * hi,
        })
    return out


def estimate_c(crashes: list[float], x0: float = 2.0) -> dict:
    """Estimate c from a single threshold, with a confidence interval.

    x0 = 2.0 by default: high enough that two-decimal rounding is negligible,
    low enough that roughly half the rounds still qualify, so the interval is
    tight.
    """
    n = len(crashes)
    k = sum(1 for c in crashes if c >= x0)
    p = k / n if n else 0.0
    lo, hi = wilson_interval(k, n)
    return {"x0": x0, "n": n, "k": k, "c_hat": x0 * p,
            "c_lo": x0 * lo, "c_hi": x0 * hi}


def tail_fit(crashes: list[float], x0: float = 2.0) -> dict:
    """Parameter-free goodness of fit on the conditional tail.

    If P(X >= x | X >= x0) = x0/x then U = x0/X is Uniform(0, 1).
    """
    tail = [c for c in crashes if c >= x0]
    us = [x0 / c for c in tail]
    d, p = ks_uniform(us)
    return {"x0": x0, "n_tail": len(tail), "ks_d": d, "ks_p": p}


def analyse(data: dict, thresholds: list[float]) -> dict:
    crashes = data["crashes"]
    n = data["n"]
    if n == 0:
        return {**data, "empty": True}

    c_est = estimate_c(crashes)
    fit = tail_fit(crashes)
    lowest = min(crashes)
    at_lowest = sum(1 for c in crashes if abs(c - lowest) < 1e-9)

    segs = streaks.segment(data.get("series") or [(c, None) for c in crashes])
    streak_thresholds = [t for t in (2.0, 5.0) if t in thresholds] or [2.0]

    return {
        **data,
        "empty": False,
        "table": threshold_table(crashes, thresholds),
        "streaks": [streaks.analyse_threshold(segs, t, c_est["c_hat"] or PUBLISHED_RTP)
                    for t in streak_thresholds],
        "gaps": len(segs) - 1,
        "estimate": c_est,
        "fit": fit,
        "max": max(crashes),
        "min": lowest,
        "at_min": at_lowest,
        "mean": sum(crashes) / n,
        "median": sorted(crashes)[n // 2],
        # E[max] over n draws from P(X>=x)=c/x is approximately c*n.
        "expected_max": c_est["c_hat"] * n,
    }


# -- reporting -------------------------------------------------------------

def render(result: dict) -> str:
    if result.get("empty"):
        return "No rounds collected yet."

    n = result["n"]
    est = result["estimate"]
    fit = result["fit"]
    claimed = result["claimed_rtp"]
    lines: list[str] = []

    def head(text: str) -> None:
        lines.append("")
        lines.append(text)
        lines.append("-" * len(text))

    head(f"Sample")
    lines.append(f"  rounds            : {n:,}")
    lines.append(f"  first             : {result['first']}")
    lines.append(f"  last              : {result['last']}")
    lines.append(f"  mean / median     : {result['mean']:.3f}x / {result['median']:.2f}x")
    lines.append(f"  lowest observed   : {result['min']:.2f}x  "
                 f"({result['at_min']:,} rounds, {result['at_min']/n:.2%})")

    if n < MIN_USEFUL_ROUNDS:
        head("Small sample")
        lines.append(f"  Only {n:,} rounds. The edge estimate below is not yet")
        lines.append(f"  meaningful — read the confidence interval, not the point")
        lines.append(f"  estimate, until you have at least {MIN_USEFUL_ROUNDS:,} rounds")
        lines.append(f"  (about {MIN_USEFUL_ROUNDS * 20 / 3600:.0f} hours of collection).")

    head("Fairness verification")
    bad = result["verified_bad"]
    lines.append(f"  hash verified     : {result['verified_ok']:,}")
    lines.append(f"  hash MISMATCH     : {bad:,}")
    lines.append(f"  not checkable     : {result['verified_na']:,}")
    if bad or result["anomalies"]:
        lines.append("")
        lines.append("  *** ANOMALIES DETECTED — the committed hash did not match the")
        lines.append("  *** revealed outcome. This is the finding that matters; the")
        lines.append("  *** distribution below is secondary. Sample:")
        for a in result["anomalies"][:5]:
            lines.append(f"      - {a}")
    else:
        lines.append("  every round's outcome was committed before the round began.")

    head("Crash distribution")
    lines.append("  threshold    below      at/above    observed     model     implied c")
    for row in result["table"]:
        x = row["threshold"]
        model_below = 1 - min(1.0, claimed / x)
        lines.append(
            f"  {x:>7.2f}x   {row['p_below']:>7.2%}   {row['count_at_or_above']:>9,}"
            f"   {row['p_at_or_above']:>8.2%}  {min(1.0, claimed/x):>8.2%}"
            f"   {row['c_hat']:>7.4f}"
        )
        del model_below

    head("House edge / RTP")
    lines.append(f"  estimated c       : {est['c_hat']:.4f}  "
                 f"(95% CI {est['c_lo']:.4f} – {est['c_hi']:.4f})")
    suffix = "   <-- too few rounds to trust" if n < MIN_USEFUL_ROUNDS else ""
    lines.append(f"  implied RTP       : {est['c_hat']:.2%}{suffix}")
    lines.append(f"  implied edge      : {1 - est['c_hat']:.2%}")
    lines.append(f"  game claims RTP   : {claimed:.2%}")
    consistent = est["c_lo"] <= claimed <= est["c_hi"]
    lines.append(f"  consistent?       : {'yes' if consistent else 'NO — claimed RTP lies outside the interval'}")
    lines.append(f"  (estimated from {est['k']:,} of {est['n']:,} rounds reaching {est['x0']}x)")

    head("Tail shape (parameter-free goodness of fit)")
    lines.append(f"  rounds >= {fit['x0']}x    : {fit['n_tail']:,}")
    lines.append(f"  KS statistic      : {fit['ks_d']:.4f}")
    lines.append(f"  p-value           : {fit['ks_p']:.4f}")
    if fit["ks_p"] < 0.01:
        lines.append("  the tail does NOT follow the stated 1/x law — worth investigating.")
        lines.append("  (with very large samples, two-decimal rounding alone can trip this;")
        lines.append("   check the deviation is materially large before drawing conclusions.)")
    else:
        lines.append("  consistent with the stated 1/x law.")

    head("Maximum")
    lines.append(f"  highest observed  : {result['max']:.2f}x")
    lines.append(f"  expected over {n:,} rounds : ~{result['expected_max']:.0f}x")
    lines.append("  note: the maximum grows in proportion to how long you watch —")
    lines.append("  it measures observation time, not a property of the game.")

    for st in result.get("streaks", []):
        head(f"Consecutive rounds below {st['threshold']:.0f}x")
        lines.append(f"  each round falls below : {st['p_below']:.2%}")
        lines.append(f"  longest run observed   : {st['longest']:,}")
        if st["expected_longest"]:
            lines.append(f"  expected longest       : ~{st['expected_longest']:.1f}"
                         f"  (over {st['n']:,} rounds)")
        lines.append(f"  runs seen              : {st['runs']:,}"
                     f"  (mean length {st['mean_run']:.2f})")
        if st["segments"] > 1:
            lines.append(f"  note: {st['segments']} collection segments — runs are not")
            lines.append(f"        counted across gaps where rounds were missed.")
        lines.append("")
        lines.append("     run       chance of        observed   expected")
        lines.append("   length      starting          count      count")
        for b in st["buckets"]:
            if b["expected"] < 0.005 and b["observed"] == 0:
                continue
            odds = f"1 in {1/b['probability']:,.0f}" if b["probability"] > 0 else "-"
            lines.append(f"   >= {b['k']:>3}    {odds:>16}   {b['observed']:>8,}"
                         f"   {b['expected']:>8.2f}")

    head("Reminder")
    lines.append("  Rounds are independent draws. Nothing above predicts the next")
    lines.append("  round, and no cash-out target changes the expected return, which")
    lines.append(f"  is c = {est['c_hat']:.2%} regardless of strategy.")
    lines.append("")
    lines.append("  This applies to streaks in particular. After any number of")
    lines.append("  consecutive low rounds, the next round carries exactly the same")
    lines.append("  probability as the first. A long run is not a debt the game")
    lines.append("  repays; the streak table above shows how ordinary long runs are.")
    lines.append("")
    return "\n".join(lines)


def render_recent(conn: sqlite3.Connection, limit: int) -> str:
    """The last N rounds, newest first, for comparison against the live game."""
    rows = conn.execute(
        "SELECT round_id, crash, ended_at, verified FROM rounds "
        "WHERE ended_at IS NOT NULL ORDER BY ended_at DESC LIMIT ?",
        (limit,),
    ).fetchall()
    if not rows:
        return "No rounds collected yet."

    lines = [f"Last {len(rows)} rounds (newest first)",
             "-" * (len(f"Last {len(rows)} rounds (newest first)")), ""]
    lines.append("      time (UTC)      crash    hash   round id")
    for r in rows:
        stamp = (r["ended_at"] or "")[11:19]
        mark = {1: "ok", 0: "BAD"}.get(r["verified"], "-")
        lines.append(f"   {stamp:>12}   {r['crash']:>7.2f}x   {mark:>4}   {r['round_id'][:8]}")

    lines.append("")
    lines.append("Compare this row against the game's own history strip:")
    lines.append("")
    row = "  ".join(f"{r['crash']:.2f}" for r in rows)
    # Wrap the compact row so it stays readable in a narrow terminal.
    width, line = 72, "  "
    for token in row.split("  "):
        if len(line) + len(token) + 2 > width:
            lines.append(line)
            line = "  "
        line += token + "  "
    if line.strip():
        lines.append(line)
    lines.append("")
    lines.append("  (newest first — most game UIs show the newest on the left too,")
    lines.append("   but check which end yours starts from before comparing.)")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    from . import config
    parser = argparse.ArgumentParser(description="Analyse collected crash rounds.")
    parser.add_argument("--db", default=config.DB_PATH)
    parser.add_argument("--json", action="store_true", help="emit raw JSON instead")
    parser.add_argument("--thresholds", type=str, default=None,
                        help="comma-separated, e.g. 2,5,10")
    parser.add_argument("--recent", type=int, metavar="N", default=None,
                        help="show the last N rounds instead of the full report")
    args = parser.parse_args(argv)

    thresholds = DEFAULT_THRESHOLDS
    if args.thresholds:
        thresholds = [float(t) for t in args.thresholds.split(",") if t.strip()]

    conn = db.connect(args.db)
    if args.recent:
        print(render_recent(conn, max(1, args.recent)))
        return 0
    result = analyse(load(conn), thresholds)
    if args.json:
        result.pop("crashes", None)
        print(json.dumps(result, indent=2, default=str))
    else:
        print(render(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
