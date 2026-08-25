from proteingym.models.cytolexmuta.model import _diagnostic_score, _mutation_candidates


def test_mutation_candidates_and_fallback_are_deterministic():
    candidates = _mutation_candidates("ACDE", "ACFE")
    assert "D3F" in candidates
    assert _diagnostic_score("ACDE", "ACFE") == _diagnostic_score("ACDE", "ACFE")
