import numpy as np
from typing import Any
import pickle
from sklearn.cross_decomposition import PLSRegression
from pg2_dataset.dataset import Manifest
from pg2_dataset.backends.assays import SPLIT_STRATEGY_MAPPING
from pg2_dataset.splits.abstract_split_strategy import TrainTestValid
from pg2_model_pls.manifest import Manifest as ModelManifest
import logging

logger = logging.getLogger(__name__)


def load_x_and_y(
    dataset_toml_file: str,
    split: TrainTestValid,
) -> tuple[list[list[Any]], list[Any]]:
    """Load feature and target data from a dataset configuration file for a specified split.

    This function reads a dataset configuration from a TOML file, ingests the dataset,
    applies the configured split strategy, and returns the features (X) and targets (Y)
    for the requested data split.

    Args:
        dataset_toml_file (str): Path to the TOML configuration file containing dataset
            specifications and metadata.
        split (TrainTestValid): The data split to load (train, validation, or test).

    Returns:
        tuple[list[list[Any]], list[Any]]: A tuple containing:
            - split_X (list[list[Any]]): Feature data for the specified split, where each
                inner list represents features for a single sample.
            - split_Y (list[Any]): Target values for the specified split.

    Example:
        >>> X_train, y_train = load_x_and_y("config.toml", TrainTestValid.train)
        >>> X_test, y_test = load_x_and_y("config.toml", TrainTestValid.test)

    Note:
        - Currently only supports single target and single feature column (index 0).
        - The split strategy is determined by the dataset's metadata configuration.
        - Multiple targets and features support is planned for future implementation.
    """

    logger.info(f"Loading the dataset from {dataset_toml_file}.")
    dataset = Manifest.from_path(dataset_toml_file).ingest()

    targets = list(dataset.assays.meta.assays.keys())

    dataset.assays.add_split(
        split_strategy=SPLIT_STRATEGY_MAPPING[dataset.assays.meta.split_strategy](),
        targets=targets,
    )

    match split:
        case TrainTestValid.train:
            split_X = dataset.assays.train(targets=targets).x.iloc[:, 0].tolist()
            split_Y = dataset.assays.train(targets=targets).y.iloc[:, 0].tolist()

        case TrainTestValid.valid:
            split_X = dataset.assays.valid(targets=targets).x.iloc[:, 0].tolist()
            split_Y = dataset.assays.valid(targets=targets).y.iloc[:, 0].tolist()

        case TrainTestValid.test:
            split_X = dataset.assays.test(targets=targets).x.iloc[:, 0].tolist()
            split_Y = dataset.assays.test(targets=targets).y.iloc[:, 0].tolist()

    logger.info("Loaded the dataset with splits X and Y.")

    return split_X, split_Y


def encode(spit_X: list[Any], hyper_params: dict[str, Any]) -> np.ndarray:
    """Encode protein sequences into one-hot encoded numerical arrays.

    This function converts a list of protein sequences into a numerical representation
    using one-hot encoding, where each amino acid is represented as a binary vector
    based on its position in the amino acid alphabet.

    Args:
        spit_X (list[Any]): List of protein sequences to encode. Each sequence should
            be a string or iterable of amino acid residues.
        hyper_params (dict[str, Any]): Dictionary containing encoding parameters:
            - "sequence_length" (int): Expected length of each sequence
            - "aa_alphabet_length" (int): Number of amino acids in the alphabet
            - "aa_alphabet" (list or str): Ordered amino acid alphabet used for encoding

    Returns:
        np.ndarray: 2D numpy array of shape (n_sequences, sequence_length * aa_alphabet_length)
            containing one-hot encoded sequences. Each row represents one sequence,
            flattened from its original (sequence_length, aa_alphabet_length) matrix form.

    Example:
        >>> sequences = ["ACG", "AGC"]
        >>> params = {
        ...     "sequence_length": 3,
        ...     "aa_alphabet_length": 20,
        ...     "aa_alphabet": "ACDEFGHIKLMNPQRSTVWY"
        ... }
        >>> encoded = encode(sequences, params)
        >>> encoded.shape
        (2, 60)  # 2 sequences, each 3 * 20 = 60 features

    Note:
        - Sequences are assumed to match the specified sequence_length
        - All amino acids in the sequences must be present in the aa_alphabet
        - The output is flattened; each sequence becomes a 1D array of length
            sequence_length * aa_alphabet_length
    """
    encodings = np.empty(
        (
            len(spit_X),
            hyper_params["sequence_length"] * hyper_params["aa_alphabet_length"],
        )
    )

    for idx, sequence in enumerate(spit_X):
        encoding = np.concatenate(
            [
                np.eye(hyper_params["aa_alphabet_length"])[
                    hyper_params["aa_alphabet"].index(res)
                ]
                for res in sequence
            ]
        )

        encodings[idx] = encoding

    return encodings


def train_model(
    train_X: list[list[Any]],
    train_Y: list[Any],
    model_toml_file: str,
    model_path: str,
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
        model_toml_file (str): Path to the TOML configuration file containing model
            hyperparameters, including encoding parameters and n_components for PLS.
        model_path (str): File path where the trained model will be saved as a
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

    hyper_params = ModelManifest.from_path(model_toml_file).hyper_params

    encodings = encode(spit_X=train_X, hyper_params=hyper_params)

    model = PLSRegression(hyper_params["n_components"])
    model.fit(encodings, train_Y)

    with open(model_path, "wb") as file:
        pickle.dump(model, file)

    logger.info(f"Saved the model to {model_path}")


def predict_model(
    test_X: list[list[Any]],
    model_toml_file: str,
    model_path: str,
) -> list[Any]:
    """Load a trained model and generate predictions on test sequences.

    This function loads a previously trained and saved model, encodes the test
    sequences using the same hyperparameters used during training, and generates
    predictions for the input sequences.

    Args:
        test_X (list[list[Any]]): Test feature data containing protein sequences.
            Each inner list represents a single sequence to predict on.
        model_toml_file (str): Path to the TOML configuration file containing model
            hyperparameters used for consistent encoding of test sequences.
        model_path (str): File path to the saved pickled model to load for prediction.

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

    with open(model_path, "rb") as file:
        model = pickle.load(file)

    hyper_params = ModelManifest.from_path(model_toml_file).hyper_params

    encodings = encode(spit_X=test_X, hyper_params=hyper_params)

    preds = model.predict(encodings)
    logger.info("Generated predictions.")

    return preds.tolist()
