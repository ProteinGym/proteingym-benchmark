from typing import Any
import pickle
from pathlib import Path
from sklearn.cross_decomposition import PLSRegression
from pg2_model_pls.preprocess import encode
from pg2_benchmark.manifest import Manifest
import logging

logger = logging.getLogger(__name__)


def train_model(
    train_X: list[list[Any]],
    train_Y: list[Any],
    manifest: Manifest,
    model_path: Path,
) -> None:
    """Train a PLS regression model on encoded protein sequences and save it to disk.

    This function loads model hyperparameters from a configuration file, encodes the
    training sequences using one-hot encoding, trains a Partial Least Squares (PLS)
    regression model, and saves the trained model to the specified path.

    Args:
        train_X (list[list[Any]]): Training feature data containing protein sequences.
            Each inner list represents a single sequence.
        train_Y (list[Any]): Training target values corresponding to the sequences
            in train_X.
        model_toml_file (Path): Path to the TOML configuration file containing model
            hyperparameters, including encoding parameters and n_components for PLS.
        model_path (Path): File path where the trained model will be saved as a
            pickled object.

    Returns:
        None

    Example:
        >>> train_sequences = [["A", "C", "G"], ["A", "G", "C"]]
        >>> train_targets = [0.5, 0.8]
        >>> train_model(train_sequences, train_targets, "model_config.toml", "model.pkl")

    Note:
        - The model configuration file must contain hyperparameters compatible with
            the encode() function (sequence_length, aa_alphabet_length, aa_alphabet)
        - The trained model uses PLSRegression from scikit-learn
        - Model is saved using pickle serialization
    """
    logger.info(f"Training the model with {len(train_X)} records.")

    encodings = encode(spit_X=train_X, hyper_params=manifest.hyper_params)

    model = PLSRegression(manifest.hyper_params["n_components"])
    model.fit(encodings, train_Y)

    with model_path.open(mode="wb") as file:
        pickle.dump(model, file)

    logger.info(f"Saved the model to {model_path}")
