# CI and automation

Maintainer notes on the GitHub Actions workflows in this repository. You need
none of this to submit a model: see [CONTRIBUTING.md](CONTRIBUTING.md), and its
"How to pass CI" section for reproducing a CI run locally.

## How CI validates a model

The [validate-model.yml](.github/workflows/validate-model.yml) workflow is a
reusable workflow. A pull request in
[proteingym-models](https://github.com/ProteinGym/proteingym-models) calls it;
it also runs on pull requests here. It:

1. checks this repository out at `main` and overlays the submitted models at
   `models/` (for a pull request here, it uses the `models` submodule instead),
2. works out which model folders the pull request changed. A pull request here
   that touches `benchmark/`, `scripts/`, `requirements.txt` or the submodule pin
   validates a representative pair (`blosum62` + `pls`) instead; a manual
   `workflow_dispatch` validates whichever folders its `models` input names,
   and reads every model card either way so a clashing or untagged card
   cannot reach `main`,
3. replaces `datasets.json` with the dummy dataset in
   [ci_datasets.json](.github/workflows/ci_datasets.json),
4. then, **for one model at a time**: builds its image (or pulls it from the
   GitHub Container Registry cache when the folder's content hash is unchanged;
   fork PRs always build), **synthesises a single-model `models.json` from its
   card** with [scripts/generate_models_list.py](scripts/generate_models_list.py),
   runs `dvc repro`, and deletes the image before the next model. Building every
   image before running anything exhausts the runner's disk,
5. writes `benchmark/metrics.csv` to the job summary and, when the token can
   write (so not for a fork's pull request), also posts it as a PR comment.
   It reports either way, and fails the run if any model failed.

Because step 4 reads the card rather than a checked-in list, a new model is
picked up automatically — you do not add it to `models.json` anywhere. The card
must carry a `name` and a `zero-shot` or `supervised` tag; CI fails loudly if it
does not.

Scores on the dummy dataset are meaningless. CI only proves the model builds,
runs and produces a prediction set the metric step can score.

## Keeping the models pin up to date

`models/` is a git submodule, so it records one **exact commit** of
proteingym-models — a submodule cannot point at a branch. A merge there
therefore never reaches this repository on its own.

[bump-models-pin.yml](.github/workflows/bump-models-pin.yml) closes that gap. It
fast-forwards the pin to proteingym-models' `main`, regenerates
`benchmark/*/models.json` from the new cards, and opens (or updates) a single
pull request on `__ci_bump_models_pin__` — a deliberately unlovely name, so
nobody mistakes the branch for someone's work. It deliberately does **not**
push to `main`: as a pull request the bump touches the submodule path, which makes
`validate-model.yml` build and run the representative pair against the new zoo
before it lands — and before [deploy.yml](.github/workflows/deploy.yml)
republishes the website from it. A card the bump would break (no `name`, no game
tag, an id another card already uses) fails the run and opens nothing.

It runs daily at 07:00 UTC, on `workflow_dispatch`, and on a
`models-main-updated` [`repository_dispatch`](https://docs.github.com/en/actions/reference/events-that-trigger-workflows#repository_dispatch)
event. The first two need no setup beyond the workflow being on `main` — GitHub
fires neither `schedule` nor `repository_dispatch` from any other branch, so it
does nothing until then. Opening the pull request, and getting CI to run on it,
do need setup:

| What | Where | Why |
| --- | --- | --- |
| `MODELS_BUMP_TOKEN` secret | this repository | A pull request opened with the default `GITHUB_TOKEN` cannot trigger other workflows — GitHub blocks that to stop workflows looping — so the bump would arrive with no CI. A PAT gets around it. |
| *Allow GitHub Actions to create and approve pull requests* | this repository, Settings → Actions → General | Only needed if you skip `MODELS_BUMP_TOKEN`: with neither, `gh pr create` is refused and the bump opens nothing. With the checkbox but no token the pull request opens unvalidated — push an empty commit to it to run CI. |
| `BENCHMARK_DISPATCH_TOKEN` secret + `notify-benchmark.yml` | proteingym-models | Turns the daily poll into a bump within seconds of a merge there. |

`MODELS_BUMP_TOKEN` needs **Contents** and **Pull requests**, both read and
write. `BENCHMARK_DISPATCH_TOKEN` needs only **Contents: read and write**, which
is what the dispatch API checks. Both are scoped to `proteingym-benchmark`, so
one fine-grained PAT carrying both permissions can serve as either.

The proteingym-models side is
[`.github/workflows/notify-benchmark.yml`](https://github.com/ProteinGym/proteingym-models/blob/main/.github/workflows/notify-benchmark.yml)
there: on a push to its `main` it posts `event_type=models-main-updated` to this
repository's `dispatches` endpoint, authorised by `BENCHMARK_DISPATCH_TOKEN`.
That event type is the whole interface between the two repositories — change it
in one place and the other stops listening.

To bump by hand instead, from a checkout of this repository:

```shell
git submodule update --remote models      # follows submodule.models.branch
python -m scripts.generate_models_list    # the lists come from the new cards
git add models benchmark/*/models.json
```

> [!NOTE]
> A plain `git pull` does not move submodule work trees. After a merge that
> bumped the pin, run `git submodule update --init models`, or pull with
> `git pull --recurse-submodules`.
