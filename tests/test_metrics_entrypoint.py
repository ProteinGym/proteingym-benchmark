import pytest
import polars as pl

from proteingym.base.dataset import Dataset
from proteingym.base.assay import SEQUENCE

from scripts.metric import (
    calculate_metrics_by_mode,
    calculate_selected_metrics,
    evaluate,
)
from scripts.utils import prepare_and_validate_scoring_df


class TestPrepareAndValidateScoringDf:
    """Test prepare_and_validate_scoring_df function."""

    def test_with_dataset(self, dataset_with_assay, predicted_dataset):
        """Test scoring dataframe preparation with Dataset objects."""
        df = prepare_and_validate_scoring_df(
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        expected_properties = {
            "is_dataframe": isinstance(df, pl.DataFrame),
            "has_sequence": SEQUENCE in df.columns,
            "has_target": "DMS Score" in df.columns,
            "has_predictions": "DMS Score_pred" in df.columns,
            "correct_length": len(df) == 10,
        }

        assert all(expected_properties.values()), (
            f"Failed checks: {[k for k, v in expected_properties.items() if not v]}"
        )

    def test_missing_predictions_raises_error(self, dataset_with_assay):
        """Test that missing predictions raise ValueError."""
        incomplete_predictions_df = pl.DataFrame(
            {
                "sequence": ["ACDEFG"],
                "DMS Score": [1.1],
            }
        )

        incomplete_preds = dataset_with_assay.predictions_delta(
            incomplete_predictions_df, target="DMS Score"
        )

        with pytest.raises(ValueError, match="Missing 9 prediction"):
            prepare_and_validate_scoring_df(
                ground_truth=dataset_with_assay,
                predicted=incomplete_preds,
                target="DMS Score",
            )

    def test_invalid_ground_truth_type_raises_error(self, predicted_dataset):
        """Test that invalid ground_truth type raises TypeError."""
        with pytest.raises(TypeError, match="must be a Dataset or a Subsets object"):
            prepare_and_validate_scoring_df(
                ground_truth="invalid",  # type: ignore
                predicted=predicted_dataset,
                target="fitness",
            )

    def test_subsets_without_split_raises_error(
        self, subsets_with_assays, predicted_dataset
    ):
        """Test that Subsets without split/fold parameters raises ValueError."""
        with pytest.raises(
            ValueError, match="Both 'split' and 'fold' must be provided"
        ):
            prepare_and_validate_scoring_df(
                ground_truth=subsets_with_assays,
                predicted=predicted_dataset,
                target="DMS Score",
            )


class TestCalculateSelectedMetrics:
    """Test calculate_selected_metrics function."""

    def test_calculate_single_metric(self, dataset_with_assay, predicted_dataset):
        """Test calculating a single metric."""
        results = calculate_selected_metrics(
            selected_metrics=["spearman"],
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        expected_result = {
            "is_dict": isinstance(results, dict),
            "has_spearman": "spearman" in results,
            "spearman_is_float": isinstance(results.get("spearman"), float),
        }

        assert all(expected_result.values()), (
            f"Failed checks: {[k for k, v in expected_result.items() if not v]}"
        )

    def test_calculate_multiple_metrics(self, dataset_with_assay, predicted_dataset):
        """Test calculating multiple metrics (when more are available)."""
        results = calculate_selected_metrics(
            selected_metrics=["spearman"],
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        assert len(results) >= 1

    def test_unknown_metric_warning(
        self, dataset_with_assay, predicted_dataset, caplog
    ):
        """Test that unknown metrics generate a warning."""
        results = calculate_selected_metrics(
            selected_metrics=["unknown_metric"],
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        assert "Metric 'unknown_metric' not found" in caplog.text
        assert "unknown_metric" not in results


class TestMetricsIntegration:
    """Integration tests using real dataset fixtures."""

    def test_with_subsets(self, subsets_with_assays):
        """Test metrics calculation with subsets fixture."""
        dataset = subsets_with_assays.dataset
        target_name = dataset.assay_targets[0].name

        predictions = Dataset(
            name="test_predictions",
            assay_targets=dataset.assay_targets,
            assay_variables=dataset.assay_variables,
            assays=dataset.assays,
        )

        split_name = list(subsets_with_assays.slices.keys())[0]
        fold_idx = 0

        results = calculate_selected_metrics(
            selected_metrics=["spearman"],
            ground_truth=subsets_with_assays,
            predicted=predictions,
            target=target_name,
            split=split_name,
            fold=fold_idx,
        )

        assert "spearman" in results
        assert results["spearman"] == pytest.approx(1.0)

    def test_multi_mode_scoring(self, subsets_with_assays):
        """Test metrics calculation with multiple scoring modes."""
        dataset = subsets_with_assays.dataset
        target_name = dataset.assay_targets[0].name

        predictions = Dataset(
            name="test_predictions",
            assay_targets=dataset.assay_targets,
            assay_variables=dataset.assay_variables,
            assays=dataset.assays,
        )

        split_name = list(subsets_with_assays.slices.keys())[0]
        test_fold = 0

        results = calculate_metrics_by_mode(
            selected_metrics=["spearman"],
            ground_truth=subsets_with_assays,
            predicted=predictions,
            target=target_name,
            split=split_name,
            test_fold=test_fold,
            score_modes=["test", "train_available", "per_fold"],
        )

        expected_results = {
            "has_correct_keys": set(results.keys())
            == {"test", "train_available", "per_fold", "metadata"},
            "test_spearman_correct": results["test"]["spearman"] == pytest.approx(1.0),
            "train_available_spearman_correct": results["train_available"]["spearman"]
            == pytest.approx(1.0),
            "per_fold_spearman_correct": all(
                fold_metrics["spearman"] == pytest.approx(1.0)
                for fold_metrics in results["per_fold"].values()
            ),
            "metadata_test_folds_correct": results["metadata"]["test_folds"]
            == [test_fold],
            "metadata_train_folds_correct": test_fold
            not in results["metadata"]["train_available_folds"],
            "metadata_total_folds_correct": results["metadata"]["total_folds"]
            == len(results["per_fold"]),
        }

        assert all(expected_results.values()), (
            f"Failed checks: {[k for k, v in expected_results.items() if not v]}"
        )


class TestEvaluateValidation:
    """Test validation in the evaluate function."""

    @pytest.mark.parametrize(
        "split,fold,target",
        [
            ("split_name", "0", None),
            (None, "0", "target_name"),
            ("split_name", None, "target_name"),
        ],
    )
    def test_evaluate_requires_parameters_for_subsets(
        self, tmp_path, subsets_with_assays, split, fold, target
    ):
        """Test that evaluate raises error when required parameters are missing for Subsets."""
        metric_path = tmp_path / "metrics.json"

        dataset_path = subsets_with_assays.dump(path=tmp_path)
        pred_path = subsets_with_assays.dataset.dump(path=tmp_path)
        target_name = subsets_with_assays.dataset.assay_targets[0].name
        split_name = list(subsets_with_assays.slices.keys())[0]

        split_value = split_name if split == "split_name" else split
        target_value = target_name if target == "target_name" else target

        with pytest.raises(
            ValueError, match="--split, --fold, and --target are required"
        ):
            evaluate(
                prediction_path=pred_path,
                metric_path=metric_path,
                dataset_path=dataset_path,
                split=split_value,
                fold=fold,
                target=target_value,
            )
