---
# Model identifier used for referencing this model in the benchmark system
name: eve

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "evedesign.models.eve:EVE"

tags: ["zero-shot", "embeddable"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the evedesign EVE wrapper
# constructor (evedesign.models.eve.EVE), so every key here must be a valid
# EVE.__init__ argument.
hyper_parameters:
    # VAE encoder/decoder/training hyperparameters (path to a JSON file or a
    # dict). null uses EVE's default_model_params.json (400,000 training steps)
    model_hyperparameters: null
    # Path to an EVE VAE checkpoint. If it exists it is loaded instead of
    # training; otherwise the trained VAE is saved there. null keeps the
    # trained VAE in memory only (use the --model-file option to persist it)
    model_checkpoint_path: null
    # MSA sequence re-weighting identity threshold (EVE default 0.2)
    theta: 0.2
    # Use weights attached to the dataset MSA where available, otherwise
    # compute EVE weights
    use_weights: true
    # Location to load/save EVE-computed MSA weights (.npy). null keeps them
    # in a temporary working directory
    msa_weights_path: null
    # EVE MSA pre-processing (drop fragment sequences and low-occupancy
    # columns)
    preprocess_msa: true
    # Number of samples used to approximate the ELBO when scoring (EVE uses
    # 20000)
    num_samples: 20000
    # Maximum number of sequences scored concurrently
    batch_size: 2048
    # Random seed for the VAE
    random_seed: 42
    # Device to run the model on ("cpu", "cuda", or "mps"). Training EVE for
    # the default 400,000 steps is only practical on a GPU
    device: "cuda"
    # Maximum number of non-focus MSA sequences to train on; deeper MSAs are
    # randomly subsampled (re-weighting is O(depth^2)). null disables
    max_msa_depth: 200000
    # Fraction of gaps above which a wild-type-covered column is excluded from
    # training. 1.0 keeps every covered column; 0.3 is the original EVE value
    threshold_focus_cols_frac_gaps: 1.0
---

# Model Card for EVE (evedesign-wrapped)

This model scores protein sequences with the [evedesign](https://github.com/evedesignbio/evedesign)
`EVE` wrapper. EVE (Evolutionary model of Variant Effect) is a family-specific
Bayesian variational autoencoder trained on a multiple sequence alignment of
homologs of the target protein. Scoring is zero-shot: the VAE is trained on
the dataset's MSA, and each sequence is assigned its evidence lower bound
(ELBO), averaged over `num_samples` samples, as an approximation to its
log-likelihood under the model.

The wrapper uses EVE code vendored from
[OATML-Markslab/EVE](https://github.com/OATML-Markslab/EVE) (MIT license).
It also exposes `transform()` (latent embeddings) and `generate()` (sampling
from the VAE prior), so this card is tagged `embeddable`.

EVE is a fixed-length model: variants with insertions, deletions or
non-standard residues cannot be scored and are left without a prediction.

For detailed information on the method, please refer to the
[EVE paper](https://doi.org/10.1038/s41586-021-04043-8) (Frazer et al.,
*Nature* 2021).

The benchmark entrypoint converts the ProteinGym subset into evedesign
datatypes with `evedesign.proteingym.dataset_to_evedesign` (which uses the
first MSA of the dataset), builds the wrapper on the resulting `System` (MSA
processing, sequence re-weighting and VAE training), and scores all variants.

## Network access

The Docker/Singularity build fetches Python packages (PyPI, and
`https://github.com/ProteinGym/proteingym-base.git` via GET) and the `uv`
binary. Nothing is fetched at `train` time.
