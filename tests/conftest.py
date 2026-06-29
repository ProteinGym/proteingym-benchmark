import pytest

import polars as pl

from proteingym.base.dataset import Assay, Dataset, Subsets, Field
from proteingym.base.sequence import Sequence, SequenceType, SequenceAlphabet
from Bio.Seq import Seq


@pytest.fixture
def dataset_with_assay() -> Dataset:
    """A dataset containing a single assay with 10 records."""
    seq1 = Sequence(
        name="seq1",
        value=Seq("ACDEFG"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq2 = Sequence(
        name="seq2",
        value=Seq("ACDEFH"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq3 = Sequence(
        name="seq3",
        value=Seq("ACDEFI"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq4 = Sequence(
        name="seq4",
        value=Seq("ACDEFK"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq5 = Sequence(
        name="seq5",
        value=Seq("ACDEFL"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq6 = Sequence(
        name="seq6",
        value=Seq("ACDEFM"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq7 = Sequence(
        name="seq7",
        value=Seq("ACDEFN"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq8 = Sequence(
        name="seq8",
        value=Seq("ACDEFP"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq9 = Sequence(
        name="seq9",
        value=Seq("ACDEFQ"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )
    seq10 = Sequence(
        name="seq10",
        value=Seq("ACDEFR"),
        type=SequenceType.ENGINEERED_SEQUENCE,
        alphabet=SequenceAlphabet.AA,
    )

    assay = Assay(
        name="assay1",
        records=[
            (seq1, 1.0, 1.5),
            (seq2, 2.0, 2.5),
            (seq3, 3.0, 3.5),
            (seq4, 4.0, 4.5),
            (seq5, 5.0, 5.5),
            (seq6, 6.0, 6.5),
            (seq7, 7.0, 7.5),
            (seq8, 8.0, 8.5),
            (seq9, 9.0, 9.5),
            (seq10, 10.0, 10.5),
        ],
        fields=[
            Field(name="sequence"),
            Field(name="DMS Score"),
            Field(name="stability"),
        ],
    )
    dataset = Dataset(
        name="dataset_with_single_assay",
        description="A dataset containing a single assay.",
        assay_variables=[Field(name="var1", description="A test variable")],
        assay_targets=[
            Field(name="DMS Score", description="The DMS score"),
            Field(name="stability", description="The resistance to temperature"),
        ],
        assays=[assay],
        sequences=[],
        structures=[],
        msas=[],
    )
    return dataset


@pytest.fixture
def predicted_dataset(dataset_with_assay: Dataset) -> Dataset:
    """Create a predictions dataset using predictions_delta on dataset_with_assay."""
    predictions_df = pl.DataFrame(
        {
            "sequence": [
                "ACDEFG",
                "ACDEFH",
                "ACDEFI",
                "ACDEFK",
                "ACDEFL",
                "ACDEFM",
                "ACDEFN",
                "ACDEFP",
                "ACDEFQ",
                "ACDEFR",
            ],
            "DMS Score": [1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 9.1, 10.1],
        }
    )

    return dataset_with_assay.predictions_delta(predictions_df, target="DMS Score")


@pytest.fixture
def subsets_with_assays(dataset_with_assay: Dataset) -> Subsets:
    """Create a Subsets object from dataset_with_assay with 5 folds.

    Creates a 'random' split with 5 folds, where each fold contains 2 records as test data.
    The 10 records are distributed as follows:
    - Fold 0: records 0-1 (seq1-seq2)
    - Fold 1: records 2-3 (seq3-seq4)
    - Fold 2: records 4-5 (seq5-seq6)
    - Fold 3: records 6-7 (seq7-seq8)
    - Fold 4: records 8-9 (seq9-seq10)
    """
    from proteingym.base.dataset import DatasetSlice, AssaySlice

    # Create 5 folds, each with 2 records
    folds = []
    for fold_idx in range(5):
        # Create a boolean mask for which records belong to this fold
        records_mask = [False] * 10
        records_mask[fold_idx * 2] = True  # First record in fold
        records_mask[fold_idx * 2 + 1] = True  # Second record in fold

        fold_slice = DatasetSlice(
            assays=[AssaySlice(records=records_mask)],
            metadata={"fold": float(fold_idx)},
        )
        folds.append(fold_slice)

    return Subsets(dataset=dataset_with_assay, slices={"random": folds})
