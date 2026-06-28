import logging

import polars as pl
from proteingym.base import Dataset, Subsets
from proteingym.base.model import ModelCard
from sklearn.cross_decomposition import PLSRegression

from .preprocess import encode, load_x_and_y

logger = logging.getLogger(__name__)


def train(
    split_dataset: Subsets,
    split: str,
    test_fold: int,
    target: str,
    model_card: ModelCard,
) -> PLSRegression:
    """Train a PLS regression model on protein sequence data from the dataset.

    This function loads training data from the dataset, encodes the protein sequences
    using hyperparameters from the model card, fits a Partial Least Squares (PLS)
    regression model, and returns the trained model.

    Args:
        split_dataset: Dataset object containing protein sequences and targets
        split: Name of the split to use
        test_fold: Which fold to use as test set
        target: Target column name
        model_card: Configuration object containing model hyperparameters
            including encoding parameters and n_components for PLS regression

    Returns:
        PLSRegression: Trained scikit-learn PLS regression model ready for prediction
    """
    train_X, train_Y, test_X, test_Y = load_x_and_y(
        subset=split_dataset,
        split=split,
        test_fold=test_fold, 
        target=target
    )

    logger.info(f"Loaded {len(train_Y)} training records and start the training...")

    encodings = encode(split_X=train_X, hyper_params=model_card.hyper_parameters)
    
    model = PLSRegression(n_components=model_card.hyper_parameters["n_components"])
    model.fit(encodings, train_Y)

    logger.info("Finished the training.")

    return model


def infer(
    split_dataset: Subsets,
    split: str,
    target: str,
    model_card: ModelCard,
    model: PLSRegression,
) -> Dataset:
    """Generate predictions using a trained PLS regression model on all sequences.

    This function encodes all protein sequences in the dataset using the same
    hyperparameters from the model card used during training, generates predictions
    using the provided trained PLS regression model, and returns a Dataset with
    the predictions merged in.

    Args:
        split_dataset: Dataset object containing protein sequences and targets
        split: Name of the split to use
        target: Target column name
        model_card: Configuration object containing model hyperparameters
            used for consistent sequence encoding
        model: Trained scikit-learn PLS regression model

    Returns:
        Dataset: Dataset with predictions added for all sequences
    """
    dataset = split_dataset[split].dataset

    all_sequences_df = dataset.to_df(target_names=target)
    all_sequences = all_sequences_df["sequence"].to_list()

    logger.info(f"Loaded {len(all_sequences)} sequences and start the scoring...")

    encodings = encode(split_X=all_sequences, hyper_params=model_card.hyper_parameters)
    predictions = model.predict(encodings)

    if len(predictions.shape) > 1:
        predictions = predictions.flatten()

    predictions_df = pl.DataFrame({
        "sequence": all_sequences,
        target: predictions.tolist(),
    })

    predictions_dataset = dataset.predictions_delta(
        predictions_df,
        target=target,
        allow_extra_predictions=True
    )

    logger.info("Finished the scoring.")

    return predictions_dataset
