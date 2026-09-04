from pathlib import Path

import numpy as np

from proteingym.models.cytolexmuta.runtime import (
    Substitution,
    _relative_variants,
    _write_gemme_inputs,
    fuse_scores,
    robust_z,
    substitutions_from_sequence,
)


def test_substitutions_preserve_external_coordinates():
    variants = substitutions_from_sequence("ACDE", "AFDG", first_index=11)
    assert variants == (
        Substitution("C", 12, "F"),
        Substitution("E", 14, "G"),
    )
    assert _relative_variants((variants,), 11) == (
        (Substitution("C", 2, "F"), Substitution("E", 4, "G")),
    )


def test_fixed_fusion_matches_the_registered_formula():
    esmc = np.array([-2.0, 0.0, 3.0])
    prosst = np.array([-1.0, 1.0, 5.0])
    gemme = np.array([4.0, 2.0, -3.0])
    esm_if1 = np.array([0.1, 0.4, 0.2])
    expected = (
        0.5 * robust_z(0.5 * esmc + 0.5 * prosst)
        + 0.25 * robust_z(gemme)
        + 0.25 * robust_z(esm_if1)
    )
    np.testing.assert_allclose(fuse_scores(esmc, prosst, gemme, esm_if1), expected)


def test_gemme_inputs_are_query_first_and_use_local_coordinates(tmp_path: Path):
    variants = (
        (),
        (Substitution("C", 12, "F"), Substitution("E", 14, "G")),
    )
    msa_path, mutation_path, active = _write_gemme_inputs(
        tmp_path,
        ("ACDE", "A-DE"),
        variants,
        first_index=11,
    )
    assert active == [1]
    assert msa_path.read_text() == ">seq0/11-14\nACDE\n>seq1/11-14\nA-DE\n"
    assert mutation_path.read_text() == "C2F,E4G\n"
