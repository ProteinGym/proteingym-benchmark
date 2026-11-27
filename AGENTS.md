# AGENTS.md

ProteinGym2 benchmark: DVC pipelines that benchmark the model zoo, and a website. Read these before changing things:

- [README.md](README.md) – overview, datasets, running the benchmark, CI, website.
- [CONTRIBUTING.md](CONTRIBUTING.md) – submission policy, pipeline details, local CI reproduction, website development.
- [CI.md](CI.md) – how the GitHub Actions workflows and the models-pin automation work, and the tokens they need.
- [proteingym-models README](https://github.com/ProteinGym/proteingym-models#readme) – model structure, model card fields, the evedesign runner, Dockerfile.
- [docs/metric.md](docs/metric.md) – how metrics are computed and added.
- [scripts/README.md](scripts/README.md) – helper scripts and website unit tests.

## Key facts

- The model zoo is a **separate repository**,
  [proteingym-models](https://github.com/ProteinGym/proteingym-models), mounted here as a
  git submodule at `models/`. Models are submitted there, never in this repository. Run
  `git submodule update --init models` after cloning.
- Models run through [evedesign](https://github.com/evedesignbio/evedesign) (read its
  `evedesign/model.py` and `evedesign/proteingym.py` for the model API and dataset
  conversion). Every model uses the same `evedesign-proteingym` console script as its
  container entrypoint; the model card's `model_class` and `hyper_parameters` select and
  configure the model. No model vendors its own runner.
- Dataset format, model cards and metrics come from
  [proteingym-base](https://github.com/ProteinGym/proteingym-base), pinned to an exact
  PyPI release in `requirements.txt` (the models pin the same one).
- Pipelines: `benchmark/{supervised,zero_shot}/dvc.yaml`, configured by `default.yaml`,
  `datasets.json` and `models.json`. Run with `dvc repro benchmark/<game>/dvc.yaml --single-item`.
- `models.json` is **generated from the model cards** by
  `scripts/generate_models_list.py` — don't hand-edit it. A card's `name` is the model id
  and its `tags` pick the game. Regenerate after bumping the `models` submodule; CI
  synthesises an ephemeral list limited to the models a pull request touched, which is why
  a model submission needs no change here.
- CI is `.github/workflows/validate-model.yml`, a reusable workflow. proteingym-models
  calls it on its own pull requests (scoped to the model folders that PR changed); on
  pull requests here it validates a representative pair (`blosum62` + `pls`) unless the
  `validate-all-models` label is set. It builds, runs and prunes one model at a time —
  all 24 images at once exhaust a runner's disk.
- The `models` pin is an exact commit (a submodule cannot hold a branch).
  `.github/workflows/bump-models-pin.yml` fast-forwards it to proteingym-models' `main`,
  regenerates `models.json` and opens a pull request — never a push to `main`, so the new
  zoo is validated before the website is rebuilt from it. `.gitmodules` carries
  `branch = main` for `git submodule update --remote`.
- Images are tagged `<model-folder>:latest`; the model card is baked into the image at
  `/opt/program/README.md` and predictions are written to `/opt/program/output`.
- Models must run offline: weights are downloaded at Docker build time.
- Model cards ship `device: "cpu"`: CI has no GPU. `poet` and `progen3` are the
  exceptions — both are GPU-only by construction and cannot pass CPU-only CI.

## Commands

```shell
pip install -r requirements.txt                       # Python env (DVC, proteingym-base)
git submodule update --init models                    # fetch the model zoo
python -m scripts.generate_models_list                # regenerate models.json from cards
python -m scripts.generate_models_list --check        # fail if the lists have drifted
cd models/<model> && uv run pytest                    # model unit tests
npm install && npm run dev                            # website
npm run check && npm run lint                         # type check + lint (lint is red on main)
node scripts/normalize.test.ts && node scripts/stats.test.ts   # website stats tests
```

Keep documentation in sync when changing pipelines, the runner, or the website's data sources.
