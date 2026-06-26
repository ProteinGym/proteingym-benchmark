import pytest
import polars as pl

from proteingym.base.dataset import Dataset, Field, Assay, Subsets, DatasetSlice
from proteingym.base.sequence import Sequence, SequenceType, SequenceAlphabet
from Bio.Seq import Seq

from scripts.metric import (
    metric_recovery,
    _get_top_k_from_slice,
    calculate_selected_metrics,
)


class TestGetTopKFromSlice:
    """Test _get_top_k_from_slice helper function."""

    @pytest.fixture
    def simple_dataset(self) -> Dataset:
        """Create a minimal dataset for testing."""
        return Dataset(
            name="test",
            assay_targets=[Field(name="target")],
            assay_variables=[],
            assays=[],
        )

    def test_returns_none_for_dataset(self, simple_dataset):
        """Test that function returns None for Dataset (not Subsets)."""
        result = _get_top_k_from_slice(
            ground_truth=simple_dataset,
            split="test",
            fold=0,
        )
        assert result is None

    def test_returns_none_without_split(self, simple_dataset):
        """Test that function returns None when split is None."""
        subsets = Subsets(
            dataset=simple_dataset,
            slices={"test": [DatasetSlice(metadata={"top_k": 10})]},
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split=None,
            fold=0,
        )
        assert result is None

    def test_returns_none_without_fold(self, simple_dataset):
        """Test that function returns None when fold is None."""
        subsets = Subsets(
            dataset=simple_dataset,
            slices={"test": [DatasetSlice(metadata={"top_k": 10})]},
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split="test",
            fold=None,
        )
        assert result is None

    def test_returns_none_for_list_fold(self, simple_dataset):
        """Test that function returns None when fold is a list."""
        subsets = Subsets(
            dataset=simple_dataset,
            slices={"test": [DatasetSlice(metadata={"top_k": 10})]},
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split="test",
            fold=[0, 1],
        )
        assert result is None

    def test_returns_none_without_metadata(self, simple_dataset):
        """Test that function returns None when slice has no metadata."""
        subsets = Subsets(
            dataset=simple_dataset, slices={"test": [DatasetSlice(metadata=None)]}
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split="test",
            fold=0,
        )
        assert result is None

    def test_returns_none_without_top_k_in_metadata(self, simple_dataset):
        """Test that function returns None when metadata doesn't contain top_k."""
        subsets = Subsets(
            dataset=simple_dataset,
            slices={"test": [DatasetSlice(metadata={"other_key": "value"})]},
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split="test",
            fold=0,
        )
        assert result is None

    def test_extracts_top_k_successfully(self, simple_dataset):
        """Test that function successfully extracts top_k from metadata."""
        subsets = Subsets(
            dataset=simple_dataset,
            slices={"test": [DatasetSlice(metadata={"top_k": 10})]},
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split="test",
            fold=0,
        )
        assert result == 10

    def test_converts_float_top_k_to_int(self, simple_dataset):
        """Test that function converts float top_k to int."""
        subsets = Subsets(
            dataset=simple_dataset,
            slices={"test": [DatasetSlice(metadata={"top_k": 10.0})]},
        )

        result = _get_top_k_from_slice(
            ground_truth=subsets,
            split="test",
            fold=0,
        )
        assert result == 10
        assert isinstance(result, int)


class TestMetricRecovery:
    """Test metric_recovery function."""

    @pytest.fixture
    def recovery_dataset(self) -> Dataset:
        """Create a dataset with known values for testing recovery."""
        sequences = [
            Sequence(
                name=f"seq{i}",
                value=Seq(f"SEQ{i:03d}"),
                type=SequenceType.ENGINEERED_SEQUENCE,
                alphabet=SequenceAlphabet.AA,
            )
            for i in range(10)
        ]

        # Ground truth values: 0.1, 0.2, 0.3, ..., 1.0
        # Top 3 are: seq9 (1.0), seq8 (0.9), seq7 (0.8)
        assay = Assay(
            name="assay1",
            records=[(sequences[i], (i + 1) / 10.0) for i in range(10)],
            fields=[
                Field(name="sequence"),
                Field(name="fitness"),
            ],
        )

        dataset = Dataset(
            name="recovery_test",
            description="Dataset for recovery testing",
            assay_variables=[],
            assay_targets=[Field(name="fitness", description="Fitness score")],
            assays=[assay],
            sequences=sequences,
            structures=[],
            msas=[],
        )
        return dataset

    @pytest.fixture
    def recovery_subsets(self, recovery_dataset) -> Subsets:
        """Create Subsets with top_k metadata.

        Creates a slice that includes all records (full boolean mask).
        """
        # Boolean mask including all 10 records
        all_records_mask = [True] * 10

        return Subsets(
            dataset=recovery_dataset,
            slices={
                "test": [DatasetSlice(assays=[all_records_mask], metadata={"top_k": 3})]
            },
        )

    def test_perfect_recovery(self, recovery_subsets):
        """Test recovery with perfect predictions (100% recovery)."""
        # Create perfect predictions
        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(i + 1) / 10.0 for i in range(10)],
            }
        )

        predictions = recovery_subsets.dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        recovery = metric_recovery(
            ground_truth=recovery_subsets,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=0,
        )

        assert recovery == pytest.approx(1.0)

    def test_zero_recovery(self, recovery_subsets):
        """Test recovery with worst predictions (0% recovery)."""
        # Create predictions that rank bottom 3 as top 3
        # Reverse the order: lowest values get highest predictions
        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(10 - i) / 10.0 for i in range(10)],  # Reversed
            }
        )

        predictions = recovery_subsets.dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        recovery = metric_recovery(
            ground_truth=recovery_subsets,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=0,
        )

        assert recovery == pytest.approx(0.0)

    def test_partial_recovery(self, recovery_subsets):
        """Test recovery with partial overlap."""
        # Predictions: top 3 should be seq9, seq8, seq6 (2/3 correct)
        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [
                    0.1,
                    0.2,
                    0.3,
                    0.4,
                    0.5,
                    0.6,
                    0.95,
                    0.75,
                    0.9,
                    1.0,
                    # indices: 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
                    # top 3 by prediction: seq9 (1.0), seq6 (0.95), seq8 (0.9)
                    # top 3 by ground truth: seq9 (1.0), seq8 (0.9), seq7 (0.8)
                    # overlap: seq9, seq8 = 2/3
                ],
            }
        )

        predictions = recovery_subsets.dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        recovery = metric_recovery(
            ground_truth=recovery_subsets,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=0,
        )

        assert recovery == pytest.approx(2.0 / 3.0)

    def test_recovery_with_dataset_returns_none(self, recovery_dataset):
        """Test that recovery returns None for plain Dataset."""
        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(i + 1) / 10.0 for i in range(10)],
            }
        )

        predictions = recovery_dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        recovery = metric_recovery(
            ground_truth=recovery_dataset,
            predicted=predictions,
            target="fitness",
        )

        assert recovery is None

    def test_recovery_without_metadata_returns_none(self, recovery_dataset):
        """Test that recovery returns None when metadata is missing."""
        subsets_no_metadata = Subsets(
            dataset=recovery_dataset, slices={"test": [DatasetSlice(metadata=None)]}
        )

        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(i + 1) / 10.0 for i in range(10)],
            }
        )

        predictions = recovery_dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        recovery = metric_recovery(
            ground_truth=subsets_no_metadata,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=0,
        )

        assert recovery is None

    def test_recovery_with_top_k_larger_than_dataset(self, recovery_dataset):
        """Test that recovery handles top_k larger than dataset size and warns."""
        all_records_mask = [True] * 10

        subsets_large_k = Subsets(
            dataset=recovery_dataset,
            slices={
                "test": [
                    DatasetSlice(assays=[all_records_mask], metadata={"top_k": 100})
                ]
            },
        )

        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(i + 1) / 10.0 for i in range(10)],
            }
        )

        predictions = recovery_dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        # Should raise a warning about top_k being larger than dataset
        with pytest.warns(
            UserWarning,
            match=r"top_k \(100\) is larger than the number of samples \(10\)",
        ):
            recovery = metric_recovery(
                ground_truth=subsets_large_k,
                predicted=predictions,
                target="fitness",
                split="test",
                fold=0,
            )

        # Should clamp to dataset size and get perfect recovery
        assert recovery == pytest.approx(1.0)

    def test_recovery_in_calculate_selected_metrics(self, recovery_subsets):
        """Test that recovery is discovered and calculated by calculate_selected_metrics."""
        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(i + 1) / 10.0 for i in range(10)],
            }
        )

        predictions = recovery_subsets.dataset.predictions_delta(
            predictions_df, target="fitness"
        )

        results = calculate_selected_metrics(
            selected_metrics=["recovery", "spearman"],
            ground_truth=recovery_subsets,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=0,
        )

        assert "recovery" in results
        assert "spearman" in results
        assert results["recovery"] == pytest.approx(1.0)
        assert results["spearman"] == pytest.approx(1.0)
