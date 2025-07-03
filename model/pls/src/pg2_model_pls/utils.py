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
    logger.info(f"Loading the dataset from {dataset_toml_file}.")
    dataset = Manifest.from_path(dataset_toml_file).ingest()

    # TODO: multiple targets
    targets = list(dataset.assays.meta.assays.keys())

    dataset.assays.add_split(
        split_strategy=SPLIT_STRATEGY_MAPPING[dataset.assays.meta.split_strategy](),
        targets=targets,
    )

    # TODO: multiple features
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
    logger.info(f"Testing the model with {len(test_X)} records.")

    with open(model_path, "rb") as file:
        model = pickle.load(file)

    hyper_params = ModelManifest.from_path(model_toml_file).hyper_params

    encodings = encode(spit_X=test_X, hyper_params=hyper_params)

    preds = model.predict(encodings)
    logger.info("Generated predictions.")

    return preds.tolist()
