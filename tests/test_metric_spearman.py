import pytest
import polars as pl

from scripts.metric import (
    metric_spearman,
)


class TestMetricSpearman:
    """Test metric_spearman function."""

    def test_perfect_correlation(self, dataset_with_assay):
        """Test Spearman correlation with perfectly correlated predictions."""
        perfect_predictions_df = pl.DataFrame(
            {
                "sequence": ["ACDEFG", "GFEDCA"],
                "DMS Score": [10.0, 20.0],
            }
        )

        perfect_preds = dataset_with_assay.predictions_delta(
            perfect_predictions_df, target="DMS Score"
        )

        corr = metric_spearman(
            ground_truth=dataset_with_assay,
            predicted=perfect_preds,
            target="DMS Score",
        )

        assert corr == pytest.approx(1.0)

    def test_realistic_correlation(self, dataset_with_assay, predicted_dataset):
        """Test Spearman correlation with realistic predictions."""
        corr = metric_spearman(
            ground_truth=dataset_with_assay,
            predicted=predicted_dataset,
            target="DMS Score",
        )

        assert isinstance(corr, float)
        assert -1.0 <= corr <= 1.0
