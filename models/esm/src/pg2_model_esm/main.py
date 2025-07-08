import torch
import typer
from rich.console import Console
from pg2_dataset.dataset import Manifest
from tqdm import tqdm
from esm import pretrained
from pg2_model_esm.utils import compute_pppl, label_row
from pg2_model_esm.manifest import Manifest as ModelManifest


app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

err_console = Console(stderr=True)
console = Console()


@app.command()
def predict(
    dataset_toml_file: str = typer.Option(help="Path to the dataset TOML file"),
    model_toml_file: str = typer.Option(help="Path to the model TOML file"),
    nogpu: bool = typer.Option(False, help="GPUs available"),
):
    console.print(f"Loading {dataset_toml_file} and {model_toml_file}...")

    manifest = Manifest.from_path(dataset_toml_file)
    dataset_name = manifest.name
    dataset = manifest.ingest()

    assays = dataset.assays.meta.assays
    targets = list(dataset.assays.meta.assays.keys())

    sequence = assays[targets[0]].constants["sequence"]
    mutation_col = assays[targets[0]].constants["mutation_col"]

    df = dataset.assays.data_frame

    console.print(f"Loaded {len(df)} records.")

    model_manifest = ModelManifest.from_path(model_toml_file)

    model_name = model_manifest.name
    location = model_manifest.location
    scoring_strategy = model_manifest.scoring_strategy
    hyper_params = model_manifest.hyper_params

    model, alphabet = pretrained.load_model_and_alphabet(location)
    model.eval()

    console.print(
        f"Loaded the model from {location} with scoring strategy {scoring_strategy}."
    )

    if torch.cuda.is_available() and not nogpu:
        model = model.cuda()
        print("Transferred model to GPU")

    batch_converter = alphabet.get_batch_converter()

    data = [
        ("protein1", sequence),
    ]

    batch_labels, batch_strs, batch_tokens = batch_converter(data)

    match scoring_strategy:
        case "wt-marginals":
            with torch.no_grad():
                token_probs = torch.log_softmax(model(batch_tokens)["logits"], dim=-1)

            df["pred"] = df.apply(
                lambda row: label_row(
                    row[mutation_col],
                    sequence,
                    token_probs,
                    alphabet,
                    hyper_params["offset_idx"],
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
                    hyper_params["offset_idx"],
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
                    hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case _:
            err_console.print(f"Error: Invalid scoring strategy: {scoring_strategy}")

    df.rename(columns={targets[0]: "test"}, inplace=True)
    df.to_csv(f"/output/{dataset_name}_{model_name}.csv", index=False)

    console.print(f"Saved the metrics in CSV in output/{dataset_name}_{model_name}.csv")
    console.print("Done.")


@app.command()
def ping():
    typer.echo("pong")


if __name__ == "__main__":
    app()
