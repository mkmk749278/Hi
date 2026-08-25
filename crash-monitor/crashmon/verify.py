"""Provably-fair verification.

The game commits to each round before it is played and reveals the secret after:

  ``startGame``  publishes ``hash``, with ``salt`` and ``checkString`` still null
  ``endGame``    reveals ``salt`` and ``checkString``

where::

    checkString = "<round id>+[<crash values>]+<salt>"
    hash        = SHA512(checkString)

Because the hash is published before anyone can bet and the crash value is baked
into the pre-image, a round whose hash checks out cannot have been altered after
bets were placed. A mismatch is the single most important thing this project can
find, so it is recorded per round rather than assumed.
"""

from __future__ import annotations

import hashlib
import re

_ALGOS = {
    "SHA512": hashlib.sha512,
    "SHA256": hashlib.sha256,
}

# "<uuid>+[2.72]+<salt>" or multi-slot "<uuid>+[2.72, 5.1]+<salt>"
_CHECK_RE = re.compile(r"^(?P<round>[0-9a-fA-F-]{36})\+\[(?P<values>[^\]]*)\]\+(?P<salt>.+)$")


def verify_round(check_string: str | None, published_hash: str | None,
                 algorithm: str = "SHA512") -> bool | None:
    """Return True if the commitment holds, False if it is violated, None if
    there is not enough information to judge."""
    if not check_string or not published_hash:
        return None
    func = _ALGOS.get((algorithm or "SHA512").upper())
    if func is None:
        return None
    return func(check_string.encode()).hexdigest() == published_hash.lower()


def parse_check_string(check_string: str | None) -> dict | None:
    """Pull the round id, crash values and salt back out of the pre-image."""
    if not check_string:
        return None
    match = _CHECK_RE.match(check_string.strip())
    if not match:
        return None
    raw = match.group("values").strip()
    values = []
    if raw:
        for part in raw.split(","):
            try:
                values.append(float(part.strip()))
            except ValueError:
                return None
    return {
        "round_id": match.group("round"),
        "values": values,
        "salt": match.group("salt"),
    }


def cross_check(check_string: str | None, round_id: str | None,
                crash: float | None) -> list[str]:
    """Confirm the revealed pre-image agrees with what was announced live.

    A correct hash only proves the pre-image was committed; it does not prove the
    pre-image describes *this* round. These checks close that gap.
    """
    problems: list[str] = []
    parsed = parse_check_string(check_string)
    if parsed is None:
        if check_string:
            problems.append("check string is malformed")
        return problems
    if round_id and parsed["round_id"] != round_id:
        problems.append(
            f"check string names round {parsed['round_id']}, expected {round_id}"
        )
    if crash is not None and parsed["values"]:
        if abs(parsed["values"][0] - crash) > 1e-9:
            problems.append(
                f"check string says crash={parsed['values'][0]}, stream said {crash}"
            )
    return problems
