from sklearn.cross_decomposition import PLSRegression
import polars as pl

from pg2_dataset.dataset import Dataset
from pg2_dataset.splits.abstract_split_strategy import TrainTestValid
from pg2_benchmark.model_card import ModelCard
from pg2_model_pls.preprocess import encode, load_x_and_y
import logging


logger = logging.getLogger(__name__)


def train(
    dataset: Dataset,
    model_card: ModelCard,
) -> PLSRegression:
    """Train a PLS regression model on protein sequence data from the dataset.

    This function loads training data from the dataset, encodes the protein sequences
    using hyperparameters from the model card, fits a Partial Least Squares (PLS)
    regression model, and returns the trained model.

    Args:
        dataset: Dataset object containing protein sequences and targets
        model_card: Configuration object containing model hyperparameters
            including encoding parameters and n_components for PLS regression

    Returns:
        PLSRegression: Trained scikit-learn PLS regression model ready for prediction
    """
    train_X, train_Y = load_x_and_y(
        dataset=dataset,
        split=TrainTestValid.train,
    )

    logger.info(f"Loaded {len(train_Y)} training records and start the training...")

    encodings = encode(spit_X=train_X, hyper_params=model_card.hyper_params)

    model = PLSRegression(model_card.hyper_params["n_components"])
    model.fit(encodings, train_Y)

    logger.info("Finished the training.")

    return model


def infer(
    dataset: Dataset,
    model_card: ModelCard,
    model: PLSRegression,
) -> pl.DataFrame:
    """Generate predictions using a trained PLS regression model on test data.

    This function loads test data from the dataset, encodes the protein sequences
    using the same hyperparameters from the model card used during training, and generates
    predictions using the provided trained PLS regression model.

    Args:
        dataset: Dataset object containing protein sequences and targets
        model_card: Configuration object containing model hyperparameters
            used for consistent sequence encoding
        model: Trained scikit-learn PLS regression model

    Returns:
        pl.DataFrame: DataFrame containing test sequences, actual targets, and predictions
            with columns 'sequence', 'test' (actual values), and 'pred' (predicted values)
    """
    test_X, test_Y = load_x_and_y(
        dataset=dataset,
        split=TrainTestValid.valid,
    )

    logger.info(f"Loaded {len(test_Y)} test records and start the scoring...")

    encodings = encode(spit_X=test_X, hyper_params=model_card.hyper_params)

    preds = model.predict(encodings)

    df = pl.DataFrame(
        {
            "sequence": test_X,
            "test": test_Y,
            "pred": preds.tolist(),
        }
    )

    logger.info("Finished the scoring.")

    return df
