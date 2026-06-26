import pytest

from pathlib import Path
import polars as pl

from proteingym.base.dataset import Assay, Dataset, Subsets, Field
from proteingym.base.sequence import Sequence, SequenceType, SequenceAlphabet
from Bio.Seq import Seq


@pytest.fixture
def dummy_dataset_path() -> Path:
    return (
        Path(__file__).resolve().parent.parent
        / "datasets/splits/Dummy_test_P0DX94.splits.pgdata"
    )


@pytest.fixture
def dummy_subsets(dummy_dataset_path: Path) -> Subsets:
    return Subsets.from_path(dummy_dataset_path)


@pytest.fixture
def dataset_with_assay() -> Dataset:
    """A dataset containing a single assay."""
    sequence1 = Sequence(
        name="seq1",
        value=Seq("ACDEFG"),
        type=SequenceType.WILD_TYPE,
        alphabet=SequenceAlphabet.AA,
    )
    sequence2 = Sequence(
        name="seq2",
        value=Seq("GFEDCA"),
        type=SequenceType.WILD_TYPE,
        alphabet=SequenceAlphabet.AA,
    )
    assay = Assay(
        name="assay1",
        records=[
            (sequence1, 1.0, 1.5),
            (sequence2, 2.0, 2.5),
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
    predictions_df = pl.DataFrame({
        "sequence": ["ACDEFG", "GFEDCA"],
        "DMS Score": [1.1, 2.1],
    })

    return dataset_with_assay.predictions_delta(
        predictions_df,
        target="DMS Score"
    )
