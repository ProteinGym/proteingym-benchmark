import pytest
import polars as pl

from proteingym.base.dataset import Dataset
from proteingym.base.assay import SEQUENCE

from scripts.metric import (
    prepare_and_validate_scoring_df,
    calculate_selected_metrics,
    calculate_metrics_by_mode,
    evaluate,
)


class TestPrepareAndValidateScoringDf:
    """Test prepare_and_validate_scoring_df function."""

    def test_with_dataset(self, dataset_with_assay, predicted_dataset):
        """Test scoring dataframe preparation with Dataset objects."""
        df = prepare_and_validate_scoring_df(
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        assert isinstance(df, pl.DataFrame)
        assert SEQUENCE in df.columns
        assert "DMS Score" in df.columns
        assert "DMS Score_pred" in df.columns
        assert len(df) == 2

    def test_missing_predictions_raises_error(self, dataset_with_assay):
        """Test that missing predictions raise ValueError."""
        incomplete_predictions_df = pl.DataFrame({
            "sequence": ["ACDEFG"],
            "DMS Score": [1.1],
        })

        incomplete_preds = dataset_with_assay.predictions_delta(
            incomplete_predictions_df,
            target="DMS Score"
        )

        with pytest.raises(ValueError, match="Missing 1 prediction"):
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

    def test_subsets_without_split_raises_error(self, dummy_subsets, predicted_dataset):
        """Test that Subsets without split/fold parameters raises ValueError."""
        with pytest.raises(ValueError, match="Both 'split' and 'fold' must be provided"):
            prepare_and_validate_scoring_df(
                ground_truth=dummy_subsets,
                predicted=predicted_dataset,
                target="DMS_score",
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

        assert isinstance(results, dict)
        assert "spearman" in results
        assert isinstance(results["spearman"], float)

    def test_calculate_multiple_metrics(self, dataset_with_assay, predicted_dataset):
        """Test calculating multiple metrics (when more are available)."""
        results = calculate_selected_metrics(
            selected_metrics=["spearman"],
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        assert len(results) >= 1

    def test_unknown_metric_warning(self, dataset_with_assay, predicted_dataset, capsys):
        """Test that unknown metrics generate a warning."""
        results = calculate_selected_metrics(
            selected_metrics=["unknown_metric"],
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        captured = capsys.readouterr()
        assert "Warning: Metric 'unknown_metric' not found" in captured.out
        assert "unknown_metric" not in results


class TestMetricsIntegration:
    """Integration tests using real dataset fixtures."""

    def test_with_dummy_subsets(self, dummy_subsets):
        """Test metrics calculation with dummy subsets fixture."""
        dataset = dummy_subsets.dataset
        target_name = dataset.assay_targets[0].name

        predictions = Dataset(
            name="test_predictions",
            assay_targets=dataset.assay_targets,
            assay_variables=dataset.assay_variables,
            assays=dataset.assays,
        )

        split_name = list(dummy_subsets.slices.keys())[0]
        fold_idx = 0

        results = calculate_selected_metrics(
            selected_metrics=["spearman"],
            ground_truth=dummy_subsets,
            predicted=predictions,
            target=target_name,
            split=split_name,
            fold=fold_idx,
        )

        assert "spearman" in results
        assert results["spearman"] == pytest.approx(1.0)

    def test_multi_mode_scoring(self, dummy_subsets):
        """Test metrics calculation with multiple scoring modes."""
        dataset = dummy_subsets.dataset
        target_name = dataset.assay_targets[0].name

        predictions = Dataset(
            name="test_predictions",
            assay_targets=dataset.assay_targets,
            assay_variables=dataset.assay_variables,
            assays=dataset.assays,
        )

        split_name = list(dummy_subsets.slices.keys())[0]
        test_fold = 0

        results = calculate_metrics_by_mode(
            selected_metrics=["spearman"],
            ground_truth=dummy_subsets,
            predicted=predictions,
            target=target_name,
            split=split_name,
            test_fold=test_fold,
            score_modes=["test", "train_available", "per_fold"],
        )

        assert set(results.keys()) == {"test", "train_available", "per_fold", "metadata"}

        assert results["test"]["spearman"] == pytest.approx(1.0)
        assert results["train_available"]["spearman"] == pytest.approx(1.0)
        for fold_metrics in results["per_fold"].values():
            assert fold_metrics["spearman"] == pytest.approx(1.0)

        assert results["metadata"]["test_folds"] == [test_fold]
        assert test_fold not in results["metadata"]["train_available_folds"]
        assert results["metadata"]["total_folds"] == len(results["per_fold"])


class TestEvaluateValidation:
    """Test validation in the evaluate function."""

    def test_evaluate_requires_parameters_for_subsets(self, tmp_path, dummy_subsets):
        """Test that evaluate raises error when required parameters are missing for Subsets."""
        metric_path = tmp_path / "metrics.json"

        dataset_path = dummy_subsets.dump(path=tmp_path)
        pred_path = dummy_subsets.dataset.dump(path=tmp_path)
        target_name = dummy_subsets.dataset.assay_targets[0].name
        split_name = list(dummy_subsets.slices.keys())[0]

        with pytest.raises(ValueError, match="--split, --fold, and --target are required"):
            evaluate(
                prediction_path=pred_path,
                metric_path=metric_path,
                dataset_path=dataset_path,
                split=split_name,
                fold="0",
                target=None,
            )

        with pytest.raises(ValueError, match="--split, --fold, and --target are required"):
            evaluate(
                prediction_path=pred_path,
                metric_path=metric_path,
                dataset_path=dataset_path,
                split=None,
                fold="0",
                target=target_name,
            )

        with pytest.raises(ValueError, match="--split, --fold, and --target are required"):
            evaluate(
                prediction_path=pred_path,
                metric_path=metric_path,
                dataset_path=dataset_path,
                split=split_name,
                fold=None,
                target=target_name,
            )
