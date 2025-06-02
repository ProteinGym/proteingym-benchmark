import json
from typing import Any


def save_data(data: list[Any], data_path: str):
    with open(data_path, "w") as file:
        json.dump(data, file)


def load_data(data_path: str):
    with open(data_path, "r") as file:
        data = json.load(file)

    return data