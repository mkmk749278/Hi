"""Verification tests, anchored on a real round captured from the live stream."""

import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from crashmon import verify

# Captured 2026-08-25 from wss://crash-gateway-grm-cr.gamedev-tech.cc
REAL_CHECK = "27fd92be-caf2-4e01-a47d-9ddc2046d450+[2.72]+faece86941fea92abc8799b39719f674"
REAL_HASH = (
    "a11cd73d8874c36f24deac0214219eddc7dc51cc69d1173c7a074264cc06506f"
    "23ed8c45e926bd8ea178dd635b1d352ddd1e28a5c242ef061101916306b375c5"
)


def test_real_round_verifies():
    assert verify.verify_round(REAL_CHECK, REAL_HASH) is True


def test_tampered_outcome_fails():
    tampered = REAL_CHECK.replace("[2.72]", "[27.2]")
    assert verify.verify_round(tampered, REAL_HASH) is False


def test_missing_inputs_are_unknown_not_false():
    # startGame publishes the hash with the salt withheld; that is not a failure.
    assert verify.verify_round(None, REAL_HASH) is None
    assert verify.verify_round(REAL_CHECK, None) is None


def test_parse_check_string():
    parsed = verify.parse_check_string(REAL_CHECK)
    assert parsed["round_id"] == "27fd92be-caf2-4e01-a47d-9ddc2046d450"
    assert parsed["values"] == [2.72]
    assert parsed["salt"] == "faece86941fea92abc8799b39719f674"


def test_parse_multi_slot():
    cs = "27fd92be-caf2-4e01-a47d-9ddc2046d450+[2.72, 5.10]+abc"
    assert verify.parse_check_string(cs)["values"] == [2.72, 5.10]


def test_cross_check_clean():
    assert verify.cross_check(REAL_CHECK, "27fd92be-caf2-4e01-a47d-9ddc2046d450", 2.72) == []


def test_cross_check_catches_wrong_round():
    problems = verify.cross_check(REAL_CHECK, "00000000-0000-0000-0000-000000000000", 2.72)
    assert any("names round" in p for p in problems)


def test_cross_check_catches_value_disagreement():
    # A valid hash over a pre-image describing a different outcome than the one
    # broadcast live is exactly the substitution this check exists to catch.
    problems = verify.cross_check(REAL_CHECK, "27fd92be-caf2-4e01-a47d-9ddc2046d450", 9.99)
    assert any("stream said" in p for p in problems)


def test_malformed_check_string_reported():
    assert verify.cross_check("nonsense", None, None) == ["check string is malformed"]
