import torch
from esm.data import Alphabet
from tqdm import tqdm
import pandas as pd
from esm import pretrained
from pg2_model_esm.utils import compute_pppl, label_row
from pg2_benchmark.manifest import Manifest
from pg2_dataset.dataset import Dataset
from pg2_model_esm.preprocess import encode
import logging

logger = logging.getLogger(__name__)


def load_model(manifest: Manifest) -> tuple[torch.nn.Module, Alphabet]:
    model, alphabet = pretrained.load_model_and_alphabet(
        manifest.hyper_params["location"]
    )
    model.eval()

    if torch.cuda.is_available() and not manifest.hyper_params["nogpu"]:
        model = model.cuda()
        print("Transferred model to GPU")

    return model, alphabet


def predict_model(
    dataset: Dataset,
    manifest: Manifest,
    model: torch.nn.Module,
    alphabet: Alphabet,
) -> pd.DataFrame:
    assays = dataset.assays.meta.assays
    targets = list(dataset.assays.meta.assays.keys())

    sequence = assays[targets[0]].constants["sequence"]
    mutation_col = assays[targets[0]].constants["mutation_col"]

    df = dataset.assays.data_frame

    batch_tokens = encode(sequence, alphabet)

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
            raise ValueError(
                f"Unrecognized scoring strategy: {manifest.hyper_params['scoring_strategy']}"
            )

    df.rename(columns={targets[0]: "test"}, inplace=True)

    return df
