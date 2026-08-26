# crash-monitor

Passively records every round of the 1win crash game and measures what the game
is actually doing: how often it crashes below any multiplier you care about, what
the real house edge is, and whether each round's outcome was genuinely committed
in advance.

It listens. It never places, cancels, or cashes out a bet, and it never touches a
real account.

## What it answers

- **"How often does it crash below 2x / 5x / anything?"** — measured, with
  confidence intervals, against the theoretical curve.
- **"What is the real RTP?"** — the game advertises `rtp: 0.97` in its own event
  stream. This measures whether that is true.
- **"Is it fair?"** — every round is published as a SHA-512 commitment *before*
  betting opens and opened afterwards. The collector checks every one.

## How it gets the data, without an account

The casino mints a demo session for anyone who asks:

```
POST https://1wmhsu.life/api/CASINO-3/games-one   {"id":"v_1wingames:crash","isDemo":true}
  -> gameUrl?b=<launch token>                     (authorization is "optional")
POST <gateway>/user/auth   Auth-Token: <token>    -> demo_user, sessionId, customerId
POST <gateway>/user/token  Session-Id/Customer-Id -> Centrifugo JWT for channel "crash-97"
wss://<gateway>/websocket/lifecycle               -> the live round stream
```

`crash-97` is the **global** round channel, confirmed two ways:

- Two independently created demo sessions receive identical round ids and
  identical crash values, 12 samples out of 12. So demo mode is not a per-user
  sandbox.
- More decisively, real-money bets appear on the very rounds a demo session
  reads. `history/last` returns the round's bets, and they carry real currencies
  and real account ids — e.g. a round crashing at 1.86x carried bets of 1019 VES
  (US$1.30) and 20 VES from numeric user ids like `45242093`, structurally unlike
  the demo user's UUID. One of those players cashed out at 1.7x and was paid,
  because 1.7 < 1.86.

Demo mode is therefore a read credential with play-money chips attached, not a
separate RNG. **The multipliers recorded here are the exact ones deciding whether
real players' money pays out.**

## Why a websocket and not polling

High multipliers take longer to play out than low ones — a 1.01x round is over in
about a second, a 100x round runs well past twenty. Any sampler slower than the
round cadence therefore catches long rounds preferentially and **inflates the
measured high-multiplier rate**. That is classic length-biased sampling, and it
would silently corrupt the exact number this project exists to measure. The push
stream delivers every round exactly once, so the sample is unbiased.

## Install

Requires Python 3.9+, systemd, and a working `venv`. On Debian and Ubuntu the
stock `python3` ships without `ensurepip`, so `venv` creation fails until
`python3-venv` is present — the installer detects this and installs the right
package for your Python version itself.

On the VPS, as root:

```bash
git clone https://github.com/mkmk749278/hi.git
cd hi/crash-monitor
sudo ./deploy/install.sh
```

The installer creates a service user, a virtualenv, **runs a live selftest, and
refuses to enable the service if that fails**. Then:

```bash
journalctl -u crash-monitor -f          # watch it collect
```

To run it by hand instead:

```bash
pip install -r requirements.txt
python -m crashmon.collector --selftest         # prove the chain works
python -m crashmon.collector --db crash.sqlite3 # collect
```

## The report

### Comparing against the live game

To check the collector against what the game itself shows, print the most recent
rounds and hold them up against the game's own history strip:

```bash
crashmon-recent          # last 20
crashmon-recent 10       # last 10
```

It prints a timestamped table with each round's verification status, then the
same multipliers as one compact row for eyeballing. Newest first — check which
end the game's strip starts from before comparing.

### The full report

After a systemd install, `crashmon-report` wraps this up with the right
interpreter and database already filled in:

```bash
crashmon-report
crashmon-report --json                  # machine-readable
crashmon-report --thresholds 1.5,2,5,50
```

Running the module directly works too, but `python -m` resolves the package
from the current directory, so name it explicitly rather than relying on where
you happen to be standing:

```bash
PYTHONPATH=/opt/crash-monitor /opt/crash-monitor/venv/bin/python \
    -m crashmon.analyze --db /var/lib/crash-monitor/crash.sqlite3
```

It prints the sample summary, the fairness verification, the threshold table
(observed vs. model), the estimated edge with a 95% interval, a parameter-free
goodness-of-fit test on the tail, and the observed maximum.

## How much data you need

At roughly 20 seconds per round, about **4,300 rounds a day**.

| Collected | Buys you |
|---|---|
| ~5,000 rounds (a day) | the below-2x rate to about ±0.7% |
| ~20,000 rounds (five days) | the edge to about ±1% |
| ~200,000 rounds (seven weeks) | the edge to about ±0.3% |

Rounds are independent, so a *random subsample* is as good as a contiguous one —
if the VPS is down for a while, the data stays valid. The `uptime` table records
exactly when the collector was connected, so gaps are visible rather than assumed.

## Streaks

The report includes run-length analysis for below-2x and below-5x: longest run
observed, how many runs of each length occurred, and how many the model expects.

Under `P(X >= x) = c/x`, a round falls below `t` with probability `p = 1 - c/t`
independently of every other round, so a run of `k` has probability `p**k`. At the
advertised 97% RTP, below-2x has `p = 0.515`:

| Run of | Chance of starting | Expect per day (~4,300 rounds) |
|---|---|---|
| 5 | 1 in 28 | ~76 |
| 7 | 1 in 104 | ~20 |
| 10 | 1 in 762 | ~2.7 |
| 12 | 1 in 2,873 | ~0.7 |
| 15 | 1 in 21,033 | ~0.1 |
| 20 | 1 in 580,571 | once every ~9 months |

The longest run grows only *logarithmically* with how long you watch:

| Watching | Expected longest below-2x run |
|---|---|
| 1 hour | ~7 |
| 1 day | ~11–12 |
| 1 week | ~14–15 |
| 1 month | ~17 |

Below 5x the runs are far longer, because `p = 0.806`: expect a longest run
around 31 in a day and around 47 in a month.

So a streak of a dozen below 2x is not a glitch or a rigged patch — it is a
roughly daily event. **And it is not predictive.** After twelve consecutive
losses the next round is still 51.5% to fall below 2x, because the crash point
was committed to a hashed seed before the round opened. There is no debt and no
correction due. Doubling after losses (martingale) does not escape this either:
it converts many small wins into a rare catastrophic loss with the same expected
return, `c`, and the game enforces a maximum bet that caps the doubling well
before the streak does.

Streaks are never counted across a collection gap. If the collector was
disconnected, two adjacent rows are not adjacent rounds, and joining them would
invent runs that never happened — so the sequence is split wherever the spacing
implies a missed round, and the report says how many segments it used.

## Reading the results honestly

**The maximum is not a property of the game.** The tail is `1/x` and unbounded,
so the largest multiplier you observe grows in proportion to how long you watch
(expected maximum over *n* rounds is about `0.97 × n`). Watching for a month and
seeing 4,000x tells you that you watched for a month.

**No cash-out target changes the expected return.** A player cashing out at `t`
wins `t` with probability `c/t`, so the expected return is `c` for every `t`.
That is why measuring `c` *is* measuring the RTP, and why strategy tuning cannot
move it.

**Nothing here predicts the next round.** Each crash point is an independent
draw from a pre-committed seed. A run of ten low rounds leaves the eleventh at
exactly the same odds. Any pattern found in the history is noise, and the
analysis deliberately reports no predictive quantity.

**The fairness check is the part that could actually surprise you.** The
distribution will almost certainly match `0.97/x`, because that is what the game
says it is. A hash mismatch would be a real finding, which is why every round is
verified and any mismatch is logged as an error and surfaced at the top of the
report.

## What it verifies, exactly

`startGame` publishes `hash` with `salt` and `checkString` withheld; `endGame`
reveals them, where

```
checkString = "<round id>+[<crash value>]+<salt>"
hash        = SHA512(checkString)
```

Because the hash is published before betting opens and the crash value is inside
the pre-image, a round whose hash checks out cannot have been altered after bets
were placed. The collector additionally cross-checks that the revealed pre-image
names *this* round and *this* crash value — a correct hash over a pre-image
describing some other round would otherwise pass — and that the hash promised at
`startGame` is the one opened at `endGame`.

## Layout

```
crashmon/config.py      endpoints and tunables, all env-overridable
crashmon/session.py     demo launch -> auth -> Centrifugo token
crashmon/collector.py   websocket -> SQLite, with reconnect and token refresh
crashmon/verify.py      provably-fair commitment checking
crashmon/analyze.py     distribution, edge estimate, goodness of fit
crashmon/db.py          schema and storage
deploy/                 systemd unit and installer
tests/                  unit tests + statistical tests on simulated data
```

Tests: `python -m pytest tests/ -q`. The statistical tests plant a known edge in
simulated data and require the estimator to recover it — including a negative
case where a game shorting players at 90% RTP must *not* be reported as 97%, and
one where a secretly capped tail must be detected.

## Operational notes

- Centrifugo tokens last about 3 days; the collector re-authenticates well
  before expiry and on every reconnect.
- Reconnects use exponential backoff with jitter (2s → 5min).
- Rounds are keyed by the provider's own round id, so a reconnect that replays a
  round cannot double count.
- The database is WAL-mode, so the report can be run while collection continues.
- Storage is roughly 5 MB per month.

### If it stops working

The endpoints were discovered from the site's own JavaScript and can move. Every
one is overridable without touching code:

```bash
CRASHMON_GATEWAY=... CRASHMON_LAUNCH_URL=... CRASHMON_GAME_ID=... \
    python -m crashmon.collector --selftest
```

`--selftest` reports which step of the chain broke. The gateway hostname comes
from `https://1play.gamedev-tech.cc/crash/vgs-1play/config.json` (`services.gateway`),
and the launch endpoint from the casino's `use-game-view-model` bundle.

## Scope

This records public game output through the operator's own demo mode. It does not
automate real-money play, does not use or store account credentials, and does not
attempt to predict outcomes — which, for an independent draw against a committed
seed, is not possible.
