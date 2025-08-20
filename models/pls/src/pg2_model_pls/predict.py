from typing import Any
import pickle
from pathlib import Path
from pg2_model_pls.preprocess import encode
from pg2_benchmark.manifest import Manifest
import logging

logger = logging.getLogger(__name__)


def predict_model(
    test_X: list[list[Any]],
    manifest: Manifest,
    model_path: Path,
) -> list[Any]:
    """Load a trained model and generate predictions on test sequences.

    This function loads a previously trained and saved model, encodes the test
    sequences using the same hyperparameters used during training, and generates
    predictions for the input sequences.

    Args:
        test_X (list[list[Any]]): Test feature data containing protein sequences.
            Each inner list represents a single sequence to predict on.
        model_toml_file (Path): Path to the TOML configuration file containing model
            hyperparameters used for consistent encoding of test sequences.
        model_path (Path): File path to the saved pickled model to load for prediction.

    Returns:
        list[Any]: List of predictions corresponding to each sequence in test_X.
            The format and type of predictions depend on the trained model's output.

    Example:
        >>> test_sequences = [["A", "C", "G"], ["G", "C", "A"]]
        >>> predictions = predict_model(test_sequences, "model_config.toml", "model.pkl")
        >>> len(predictions) == len(test_sequences)
        True

    Note:
        - The model configuration file must contain the same hyperparameters used
            during training for consistent sequence encoding
        - The model file must be a pickled model compatible with scikit-learn's
            predict() method
        - Input sequences must be compatible with the encoding parameters
        - Logs prediction progress and completion messages
    """
    logger.info(f"Testing the model with {len(test_X)} records.")

    with model_path.open(mode="rb") as file:
        model = pickle.load(file)

    encodings = encode(spit_X=test_X, hyper_params=manifest.hyper_params)

    preds = model.predict(encodings)
    logger.info("Generated predictions.")

    return preds.tolist()
