from typing import Annotated
from pathlib import Path
import torch
import typer
from rich.console import Console
from pg2_dataset.dataset import Dataset
from tqdm import tqdm
from esm import pretrained
from pg2_model_esm.utils import compute_pppl, label_row
from pg2_benchmark.manifest import Manifest


app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

err_console = Console(stderr=True)
console = Console()


class SageMakerTrainingJobPath:
    PREFIX = Path("/opt/ml")
    TRAINING_JOB_PATH = PREFIX / "input" / "data" / "training" / "dataset.zip"
    MANIFEST_PATH = PREFIX / "input" / "data" / "manifest" / "manifest.toml"
    PARAMS_PATH = PREFIX / "input" / "config" / "hyperparameters.json"
    OUTPUT_PATH = PREFIX / "model"

    MODEL_PATH = Path("/model.pkl")


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ],
    model_toml_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model TOML file",
        ),
    ],
):
    # Reference: https://typer.tiangolo.com/tutorial/parameter-types/path/#path-validations
    # Cannot use default in Typer.Option for pathlib's Path object,
    # Otherwise the error: 'AttributeError: 'PosixPath' object has no attribute 'isidentifier'

    console.print(f"Loading {dataset_file} and {model_toml_file}...")

    dataset_file = dataset_file or SageMakerTrainingJobPath.TRAINING_JOB_PATH
    dataset = Dataset.from_path(dataset_file)

    assays = dataset.assays.meta.assays
    targets = list(dataset.assays.meta.assays.keys())

    sequence = assays[targets[0]].constants["sequence"]
    mutation_col = assays[targets[0]].constants["mutation_col"]

    df = dataset.assays.data_frame

    console.print(f"Loaded {len(df)} records.")

    model_toml_file = model_toml_file or SageMakerTrainingJobPath.MANIFEST_PATH
    manifest = Manifest.from_path(model_toml_file)

    model, alphabet = pretrained.load_model_and_alphabet(
        manifest.hyper_params["location"]
    )
    model.eval()

    console.print(
        f"Loaded the model from {manifest.hyper_params['location']} with scoring strategy {manifest.hyper_params['scoring_strategy']}."
    )

    if torch.cuda.is_available() and not manifest.hyper_params["nogpu"]:
        model = model.cuda()
        print("Transferred model to GPU")

    batch_converter = alphabet.get_batch_converter()

    data = [
        ("protein1", sequence),
    ]

    batch_labels, batch_strs, batch_tokens = batch_converter(data)

    match manifest.hyper_params["scoring_strategy"]:
        case "wt-marginals":
            with torch.no_grad():
                token_probs = torch.log_softmax(model(batch_tokens)["logits"], dim=-1)

            df["pred"] = df.apply(
                lambda row: label_row(
                    row[mutation_col],
                    sequence,
                    token_probs,
                    alphabet,
                    manifest.hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case "masked-marginals":
            all_token_probs = []

            for i in tqdm(range(batch_tokens.size(1))):
                batch_tokens_masked = batch_tokens.clone()
                batch_tokens_masked[0, i] = alphabet.mask_idx

                with torch.no_grad():
                    token_probs = torch.log_softmax(
                        model(batch_tokens_masked)["logits"], dim=-1
                    )

                all_token_probs.append(token_probs[:, i])  # vocab size

            token_probs = torch.cat(all_token_probs, dim=0).unsqueeze(0)

            df["pred"] = df.apply(
                lambda row: label_row(
                    row[mutation_col],
                    sequence,
                    token_probs,
                    alphabet,
                    manifest.hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case "pseudo-ppl":
            tqdm.pandas()

            df["pred"] = df.progress_apply(
                lambda row: compute_pppl(
                    row[mutation_col],
                    sequence,
                    model,
                    alphabet,
                    manifest.hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case _:
            err_console.print(
                f"Error: Invalid scoring strategy: {manifest.hyper_params['scoring_strategy']}"
            )

    df.rename(columns={targets[0]: "test"}, inplace=True)
    df.to_csv(
        f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv",
        index=False,
    )

    console.print(
        f"Saved the metrics in CSV in {SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv"
    )
    console.print("Done.")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
