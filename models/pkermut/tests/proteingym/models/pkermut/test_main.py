from proteingym.models.pkermut.__main__ import train

from tempfile import TemporaryDirectory


def test_train(dummy_data_path, model_card_path, monkeypatch):
    with TemporaryDirectory() as temp_dir:
        monkeypatch.setattr("proteingym.models.pkermut.__main__.ContainerTrainingJobPath.OUTPUT_PATH", temp_dir)
        train(
            dataset_file=dummy_data_path,
            split="random",
            target="charge",
            test_fold=0,
            model_card_file=model_card_path
        )
