import json
import pytest
import polars as pl

from proteingym.base.dataset import Dataset, Field, Assay, Subsets, DatasetSlice
from proteingym.base.sequence import Sequence, SequenceType, SequenceAlphabet
from Bio.Seq import Seq

from scripts.metric import (
    metric_recovery,
    _get_top_k_from_slice,
    calculate_selected_metrics,
    calculate_metrics_by_mode,
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
        all_records_mask = [True] * 10

        return Subsets(
            dataset=recovery_dataset,
            slices={
                "test": [DatasetSlice(assays=[all_records_mask], metadata={"top_k": 3})]
            },
        )

    def test_perfect_recovery(self, recovery_subsets):
        """Test recovery with perfect predictions (100% recovery)."""
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
        predictions_df = pl.DataFrame(
            {
                "sequence": [f"SEQ{i:03d}" for i in range(10)],
                "fitness": [(10 - i) / 10.0 for i in range(10)],
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

        assert "recovery" in results and "spearman" in results
        assert results["recovery"] == pytest.approx(1.0)
        assert results["spearman"] == pytest.approx(1.0)

    def test_recovery_none_for_training_folds(self, recovery_dataset):
        """Test that recovery returns None for training folds (no top_k metadata) and value for test fold."""
        all_records_mask = [True] * 10
        half_records_mask = [i < 5 for i in range(10)]

        subsets_with_mixed_metadata = Subsets(
            dataset=recovery_dataset,
            slices={
                "test": [
                    DatasetSlice(assays=[half_records_mask], metadata={"top_k": 2}),
                    DatasetSlice(assays=[half_records_mask], metadata=None),
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

        test_recovery = metric_recovery(
            ground_truth=subsets_with_mixed_metadata,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=0,
        )
        assert test_recovery == pytest.approx(1.0)

        train_recovery = metric_recovery(
            ground_truth=subsets_with_mixed_metadata,
            predicted=predictions,
            target="fitness",
            split="test",
            fold=1,
        )
        assert train_recovery is None

    def test_recovery_in_calculate_metrics_by_mode(self, recovery_dataset):
        """Test that recovery correctly returns None for train_available and values for test in multi-mode calculation."""
        all_records_mask = [True] * 10

        subsets = Subsets(
            dataset=recovery_dataset,
            slices={
                "cv": [
                    DatasetSlice(assays=[all_records_mask], metadata=None),
                    DatasetSlice(assays=[all_records_mask], metadata=None),
                    DatasetSlice(assays=[all_records_mask], metadata={"top_k": 3}),
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

        results = calculate_metrics_by_mode(
            selected_metrics=["recovery", "spearman"],
            ground_truth=subsets,
            predicted=predictions,
            target="fitness",
            split="cv",
            test_fold=2,
        )

        expected_structure = {
            "test_has_recovery": results["test"]["recovery"] == pytest.approx(1.0),
            "test_has_spearman": results["test"]["spearman"] == pytest.approx(1.0),
            "train_recovery_is_none": results["train_available"]["recovery"] is None,
            "train_has_spearman": results["train_available"]["spearman"]
            == pytest.approx(1.0),
            "fold_0_recovery_is_none": results["per_fold"]["fold_0"]["recovery"]
            is None,
            "fold_1_recovery_is_none": results["per_fold"]["fold_1"]["recovery"]
            is None,
            "fold_2_has_recovery": results["per_fold"]["fold_2"]["recovery"]
            == pytest.approx(1.0),
        }

        assert all(expected_structure.values()), (
            f"Failed checks: {[k for k, v in expected_structure.items() if not v]}"
        )

    def test_recovery_none_serializes_to_json_null(self, recovery_dataset):
        """Test that None values in recovery metric serialize correctly to JSON null."""
        all_records_mask = [True] * 10

        subsets = Subsets(
            dataset=recovery_dataset,
            slices={
                "cv": [
                    DatasetSlice(assays=[all_records_mask], metadata=None),
                    DatasetSlice(assays=[all_records_mask], metadata={"top_k": 3}),
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

        results = calculate_metrics_by_mode(
            selected_metrics=["recovery", "spearman"],
            ground_truth=subsets,
            predicted=predictions,
            target="fitness",
            split="cv",
            test_fold=1,
        )

        json_str = json.dumps(results)
        parsed = json.loads(json_str)

        expected_json_structure = {
            "test_recovery_is_float": isinstance(parsed["test"]["recovery"], float),
            "train_recovery_is_null": parsed["train_available"]["recovery"] is None,
            "fold_0_recovery_is_null": parsed["per_fold"]["fold_0"]["recovery"] is None,
            "fold_1_recovery_is_float": isinstance(
                parsed["per_fold"]["fold_1"]["recovery"], float
            ),
            "json_contains_null_string": '"recovery": null' in json_str,
        }

        assert all(expected_json_structure.values()), (
            f"Failed checks: {[k for k, v in expected_json_structure.items() if not v]}"
        )
