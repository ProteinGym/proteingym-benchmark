# AGENTS.md

ProteinGym2 benchmark: a model zoo, DVC pipelines that benchmark the models, and a website. Read these before changing things:

- [README.md](README.md) – overview, datasets, running the benchmark, CI, website.
- [CONTRIBUTING.md](CONTRIBUTING.md) – model submission policy, pipeline details, local CI reproduction, website development.
- [models/README.md](models/README.md) – model structure, model card fields, the evedesign runner, Dockerfile.
- [docs/metric.md](docs/metric.md) – how metrics are computed and added.
- [scripts/README.md](scripts/README.md) – helper scripts and website unit tests.

## Key facts

- Models run through [evedesign](https://github.com/evedesignbio/evedesign) (installed as a
  dependency; read its `evedesign/model.py` and `evedesign/proteingym.py` for the model API
  and dataset conversion). Every model shares the same `__main__.py` runner; the model card's
  `model_class` and `hyper_parameters` select and configure the model. The supervised models
  (`pls`, `kermut`, `pkermut`, `huggingface-regressor`) still have custom entrypoints and are
  due to be migrated; don't copy them as templates.
- Dataset format, model cards and metrics come from
  [proteingym-base](https://github.com/ProteinGym/proteingym-base), installed from `main`.
- Pipelines: `benchmark/{supervised,zero_shot}/dvc.yaml`, configured by `default.yaml`,
  `datasets.json` and `models.json`. Run with `dvc repro benchmark/<game>/dvc.yaml --single-item`.
- Images are tagged `<model-folder>:latest`; the model card is baked into the image at
  `/opt/program/README.md` and predictions are written to `/opt/program/output`.
- Models must run offline: weights are downloaded at Docker build time.

## Commands

```shell
pip install -r requirements.txt                       # Python env (DVC, proteingym-base)
cd models/<model> && uv run pytest                    # model unit tests
npm install && npm run dev                            # website
npm run check && npm run lint                         # website type check + lint
node scripts/normalize.test.ts && node scripts/stats.test.ts   # website stats tests
```

Keep documentation in sync when changing pipelines, the runner, or the website's data sources.
