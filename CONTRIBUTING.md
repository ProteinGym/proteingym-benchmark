# Contributing

This file contains documentation about contributing to this project.

After cloning the repository, you can start developing locally for both the benchmarking system and the static website to show the model cards and benchmarking results.

## Model Submission Policy

> [!IMPORTANT]
> Models are submitted to [proteingym-models](https://github.com/ProteinGym/proteingym-models), which this
> repository mounts as a git submodule at `models/`. Open your pull request
> there; nothing in this repository needs to change for your model to be
> validated by CI.

We welcome public PRs adding new models to the benchmark. The goal of the
benchmark is to advance the field, not to accumulate leaderboard entries, so
submissions are evaluated on usability and novelty, not on score alone.

To keep the benchmark useful, every model submission must satisfy the
criteria below. If you are in doubt, please open an issue to confirm
suitability before working on your pull-request. These criteria apply to new
submissions; some existing models do not fully meet them yet and are being
brought into line.

New models use the shared `evedesign-proteingym` entrypoint from
[evedesign](https://github.com/evedesignbio/evedesign), described in the
[proteingym-models README](https://github.com/ProteinGym/proteingym-models#readme). The runner is not vendored
into model folders.

### 1. Contribute something the field can learn from

In your PR description or model card, briefly explain what is novel or
interesting about the model (a new architecture, a new use of
data/features, a reproduction of a notable published method,
etc.). Minor hyperparameter sweeps or re-submissions of an
already-present architecture with cosmetic changes or straight-forward
ensembles are not sufficient for inclusion.

### 2. Ship small weights for a sanity check

Every submission must include a small (e.g. a few MB, and no more than
500MB) set of trained weights, or a fast, seeded training recipe that
produces one, so that reviewers and CI can run an end-to-end sanity
check without depending on the contributor's original training run or
infrastructure. Large pretrained backbones (ESM, MSA Transformer,
etc.) are fine to depend on, but the *model-specific* weights (the
part you trained) should be small enough to check into the repo (via
[DVC](https://dvc.org/)) or to reproduce quickly during the build.

### 3. No external network access

Models must run fully offline at inference/training time inside their
container. The benchmark is executed in an isolated environment (this is a
security requirement, not just a reproducibility one), and containers may be
run with networking disabled (e.g. `docker run --network none`).

Concretely:

- All weights, tokenizers, and other assets must be fetched and baked into
  the Docker image at **build time** (e.g. in the `Dockerfile`, via `RUN`
  steps, as in [esmc/Dockerfile](https://github.com/ProteinGym/proteingym-models/blob/main/esmc/Dockerfile)), never at
  `train`/`embed` runtime.
- No calls to external APIs, model hubs, license servers, telemetry/analytics
  endpoints, or any other network resource once the container is running.
- In your model card, list every URL your Dockerfile or code touches during
  the build, along with the HTTP method (GET/POST) and why it's needed (e.g.
  "GET https://huggingface.co/... to download pretrained ESM2 weights").
  This makes the review straightforward and lets us catch anything that
  should instead be vendored or pinned.

### Other requirements

- **License:** declare the license of your code and of any pretrained
  weights you depend on, and confirm it permits inclusion in and
  benchmarking by this project. Flag anything non-commercial or otherwise
  restrictive.
- **Reproducibility:** pin dependency versions (`pyproject.toml`/lockfile)
  and avoid non-deterministic behavior where practical (fixed seeds).
- **Resource budget:** keep image size, memory, and runtime reasonable so CI
  stays fast and affordable — this is enforced by CI timeouts and image size
  checks, not fixed numbers.
- **Attribution:** credit the original paper/repo for any borrowed code or
  architecture, both in the model card and in code comments where relevant.
- **No malicious or obfuscated code:** the entrypoint and its dependencies
  are read as part of review; anything that tries to execute arbitrary
  commands, exfiltrate data, or otherwise behave outside the documented
  `train`/`embed` contract will be rejected.
- **Complete model card:** see the [proteingym-models README](https://github.com/ProteinGym/proteingym-models#readme) for the
  required structure and model card fields. The card should describe the
  architecture, training data, and hyperparameters, not just report a score.
- **One model per PR**, following the [PR checklist](.github/pull_request_template.md).

## Benchmarking

The benchmark uses [DVC](https://dvc.org/) to orchestrate reproducible pipelines.
Each model is a Docker image, and DVC runs it on every dataset of the supervised
or zero-shot game, then calculates metrics. The same pipelines run locally and in
CI (GitHub Actions).

### Installation

```shell
$ python -m venv .venv
$ source .venv/bin/activate
$ pip install -r requirements.txt
```

This installs DVC and [proteingym-base](https://github.com/ProteinGym/proteingym-base),
which provides the dataset format and the `proteingym-base` CLI used for metrics.

Relative paths are resolved from the game folder.

**models.json** – the model `name` and the local Docker `image`, tagged
`<model-folder>:latest`:

```json
{
  "models": [
    {
      "name": "pls",
      "image": "pls:latest"
    }
  ]
}
```

You never write this by hand: it is generated from the model cards.

```shell
python -m scripts.generate_models_list              # every model, both games
python -m scripts.generate_models_list --only pls   # restrict to one folder
```

### Running locally

```shell
git submodule update --init models
docker build -t <model-folder>:latest models/<model-folder>
python -m scripts.generate_models_list --only <model-folder>
dvc repro benchmark/zero_shot/dvc.yaml --single-item
```

### CI

The [validate-model.yml](.github/workflows/validate-model.yml) workflow is a
reusable workflow. A pull request in
[proteingym-models](https://github.com/ProteinGym/proteingym-models) calls it;
it also runs on pull requests here. It:

1. checks this repository out at `main` and overlays the submitted models at
   `models/` (for a pull request here, it uses the `models` submodule instead),
2. works out which model folders the pull request changed. A pull request here
   that touches `benchmark/`, `scripts/`, `requirements.txt` or the submodule pin
   validates a representative pair (`blosum62` + `pls`) instead; the
   `validate-all-models` label forces the full sweep,
3. replaces `datasets.json` with the dummy dataset in
   [ci_datasets.json](.github/workflows/ci_datasets.json),
4. then, **for one model at a time**: builds its image (or pulls it from the
   GitHub Container Registry cache when the folder's content hash is unchanged;
   fork PRs always build), **synthesises a single-model `models.json` from its
   card** with [scripts/generate_models_list.py](scripts/generate_models_list.py),
   runs `dvc repro`, and deletes the image before the next model. Building every
   image before running anything exhausts the runner's disk,
5. posts `benchmark/metrics.csv` as a PR comment, failing if any model failed.

Because step 4 reads the card rather than a checked-in list, a new model is
picked up automatically — you do not add it to `models.json` anywhere. The card
must carry a `name` and a `zero-shot` or `supervised` tag; CI fails loudly if it
does not.

Scores on the dummy dataset are meaningless. CI only proves the model builds,
runs and produces a prediction set the metric step can score.

### How to pass CI

Reproduce the CI run locally before pushing. Clone this repository alongside
your model work and point the submodule at your branch, or just copy your model
folder into `models/`.

```shell
git submodule update --init models

docker build -t <model-folder>:latest models/<model-folder>

# generate models.json for just your model, then use the CI dataset
python -m scripts.generate_models_list --only <model-folder>
cp .github/workflows/ci_datasets.json benchmark/zero_shot/datasets.json
cp datasets/splits/Dummy_test_P0DX94.splits.pgdata datasets/
dvc repro benchmark/zero_shot/dvc.yaml --single-item
```

Use `benchmark/supervised/` for a supervised model. Don't commit the modified
`datasets.json`/`models.json` (`git checkout benchmark/` restores them).

#### Common failure scenarios

**Docker build failures**
- Run `docker build -t <model-folder>:latest models/<model-folder>` directly.
- Check that every `COPY` path exists and that dependencies are pinned in `pyproject.toml`/`uv.lock`.

**Model execution failures**
- Run the container by hand:
  ```shell
  docker run --rm \
    -v $(pwd)/datasets/splits/Dummy_test_P0DX94.splits.pgdata:/data.pgdata \
    -v $(pwd)/output:/opt/program/output \
    <model-folder>:latest train \
    --dataset-file /data.pgdata --target charge --split random --test-fold 0
  ```
- Check that `output/Dummy_test_P0DX94_predictions.pgdata` was written.

**Metric calculation failures**
- Inspect the predictions under `benchmark/<game>/prediction/` and check that the
  model scored every variant of the requested target.

#### Checklist

Before pushing:
- [ ] The image builds: `docker build -t <model-folder>:latest models/<model-folder>`
- [ ] The local pipeline completes: `dvc repro benchmark/<game>/dvc.yaml --single-item`
- [ ] Metric files exist: `ls benchmark/<game>/metric/`
- [ ] The model card has `name`, `model_class` and the right `tags` (`zero-shot` or `supervised`)
- [ ] The model card sets `device: "cpu"` — CI has no GPU
- [ ] `ENTRYPOINT` is `["uv", "run", "evedesign-proteingym"]`; no `__main__.py` is vendored

You do **not** need to touch `benchmark/<game>/models.json`; CI derives it from
your model card.

## Static website

The static website is built with [SvelteKit](https://svelte.dev/docs/kit/introduction) and displays model cards and benchmarking results. It uses TypeScript, [Tailwind CSS](https://tailwindcss.com/) for styling, and is configured as a static site adapter for deployment.

### Installation

To install the development environment for the static website:

```shell
$ npm install
```

This will install all dependencies including Svelte, SvelteKit, TypeScript, Tailwind CSS, and development tools.

### Development

Start the development server with hot module replacement:

```shell
$ npm run dev
```

The development server will start at `http://localhost:5173` by default. The site will automatically reload when you make changes to files in the `src/` directory.

### Building

To create a production build of the static site:

```shell
$ npm run build
```

This generates static files in the `build/` directory that can be deployed to any static hosting service.

### Preview

To preview the production build locally:

```shell
$ npm run preview
```

This serves the built static files so you can test the production build before deployment.

### Code Quality

Before committing changes, ensure your code passes all quality checks:

```shell
# Type check with TypeScript and Svelte
$ npm run check

# Type check in watch mode
$ npm run check:watch

# Format code with Prettier (auto-fix)
$ npm run format

# Lint code with Prettier and ESLint
$ npm run lint
```

**Note:** Prettier is configured to only format files in the `src/` directory and specific config files. Python, YAML, and benchmark-related files are excluded. See [.prettierignore](.prettierignore) for details.

### Project Structure

```
src/
├── routes/                      # SvelteKit routes (pages)
│   ├── +page.svelte             # Homepage
│   ├── +layout.svelte           # Root layout
│   ├── model/                   # Model list and model card pages (model/[slug])
│   ├── datasets/                # Dataset list and dataset pages (datasets/[slug])
│   └── benchmark/[setting]/     # Leaderboard per setting, with plots/
├── lib/                         # Shared code: components, stores, types, stats
│   ├── components/
│   ├── stores/                  # Svelte stores (models, datasets, benchmark)
│   ├── types/
│   └── assets/                  # Favicon and logo
├── app.html                     # HTML template
└── app.css                      # Global styles
```

The site reads:
- `models/*/README.md` – model cards (front matter + Markdown),
- `benchmark/metrics.csv` – benchmark results,
- `static/manifests.json` – dataset metadata, synced from Hugging Face by
  [sync-datasets.yml](.github/workflows/sync-datasets.yml),
- `static/histograms.json` – per-dataset score histograms. **Not synced, and not
  generated by anything**: the dataset repo's `generate_metadata.py` writes
  `manifests.json` and `metadata.json` only, so this file is a hand-committed
  sample covering 5 of the 211 datasets. The dataset page treats a miss as "no
  histogram", so the other 206 show none. Filling it in means computing the
  histograms where the scores are: the `.pgdata` archives in the dataset repo,
  4.85 GB of them, which rules out doing it from here. The computation is in
  [scripts/histograms.py](scripts/histograms.py), written to drop into that
  repo's `generate_metadata.py` — it already opens every archive and parses the
  `manifest.lock` that names the assay CSV and its target columns. Once it
  publishes `histograms.json`, restore the two-file fetch and the `commit_hash`
  check in [sync-datasets.yml](.github/workflows/sync-datasets.yml).

Unit tests for the statistics code live in `scripts/*.test.ts` and run with
`node scripts/<name>.test.ts`.

### Technology Stack

- **Framework:** SvelteKit 2.x with Svelte 5.x
- **Language:** TypeScript 5.x
- **Styling:** Tailwind CSS 4.x with Typography plugin
- **Build Tool:** Vite 7.x
- **Markdown:** [Marked](https://github.com/markedjs/marked) for parsing model card content
- **Code Quality:** ESLint, Prettier, svelte-check
