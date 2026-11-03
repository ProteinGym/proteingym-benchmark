# Contributing

This file contains documentation about contributing to this project.

After cloning the repository, you can start developing locally for both the benchmarking system and the static website to show the model cards and benchmarking results.

## Model Submission Policy

We welcome public PRs adding new models to the benchmark. The goal of the
benchmark is to advance the field, not to accumulate leaderboard entries, so
submissions are evaluated on usability and novelty, not on score alone.

To keep the benchmark useful, every model submission must satisfy the
criteria below. If you are in doubt, please open an issue to confirm
suitability before working on your pull-request. These criteria apply to new
submissions; some existing models do not fully meet them yet and are being
brought into line.

New models use the [evedesign](https://github.com/evedesignbio/evedesign)
runner described in [models/README.md](models/README.md).

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
  steps, as in [models/esmc/Dockerfile](models/esmc/Dockerfile)), never at
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
- **Complete model card:** see [models/README.md](models/README.md) for the
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

### Running locally

```shell
docker build -t <model-folder>:latest models/<model-folder>
dvc repro benchmark/zero_shot/dvc.yaml --single-item
```

### CI

The [cml.yaml](.github/workflows/cml.yaml) workflow runs on pull requests to `main`
that touch `models/`. It:

1. works out which models changed (all models if `models.json`, a `dvc.yaml` or
   `scripts/` changed),
2. builds their images, or pulls them from the GitHub Container Registry cache
   when the model folder's content hash is unchanged (fork PRs always build),
3. narrows `models.json` to those models and replaces `datasets.json` with the
   dummy dataset in [ci_datasets.json](.github/workflows/ci_datasets.json),
4. runs `dvc repro` for both games and posts `benchmark/metrics.csv` as a PR comment.

A model only runs in CI if it is listed in `models.json`.

### How to pass CI

Reproduce the CI run locally before pushing:

```shell
docker build -t <model-folder>:latest models/<model-folder>

# limit models.json to your model, then use the CI dataset
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
- [ ] The model is listed in `benchmark/<game>/models.json`

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
- `static/manifests.json` and `static/histograms.json` – dataset metadata, synced
  from Hugging Face by [sync-datasets.yml](.github/workflows/sync-datasets.yml).

Unit tests for the statistics code live in `scripts/*.test.ts` and run with
`node scripts/<name>.test.ts`.

### Technology Stack

- **Framework:** SvelteKit 2.x with Svelte 5.x
- **Language:** TypeScript 5.x
- **Styling:** Tailwind CSS 4.x with Typography plugin
- **Build Tool:** Vite 7.x
- **Markdown:** [Marked](https://github.com/markedjs/marked) for parsing model card content
- **Code Quality:** ESLint, Prettier, svelte-check
