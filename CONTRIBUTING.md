# Contributing

This file contains documentation about contributing to this project.

After cloning the repository, you can start developing locally for both the benchmarking system and the static website to show the model cards and benchmarking results.

## Benchmarking

The benchmarking system evaluates protein machine learning models using [DVC (Data Version Control)](https://dvc.org/) to orchestrate reproducible machine learning pipelines. It tests models on different datasets for supervised and zero-shot games by containerizing each model with Docker, running predictions, and calculating performance metrics. The system supports three environments: local development, AWS cloud (using SageMaker), and CI/CD (GitHub Actions).

### Key workflows
1. **Local testing** - Fast iteration with Docker containers on your machine
2. **AWS deployment** - Scalable training jobs using SageMaker and ECR
3. **CI validation** - Automated testing on pull requests to validate model changes

The system uses DVC's matrix feature to automatically create pipeline stages for all dataset-model combinations, ensuring comprehensive testing across configurations.

### Installation

To install the local environment for the benchmarking system, you can do the following:

```shell
$ python -m venv .venv
$ source .venv/bin/activate
$ pip install -r requirements.txt
```

### DVC Pipeline Configuration

The benchmarking system uses DVC to manage reproducible pipelines. Each benchmark game (supervised/zero-shot) has separate configurations for different environments.

#### dvc.yaml Structure

Each [dvc.yaml](benchmark/supervised/local/dvc.yaml) file defines a pipeline with multiple stages:

1. **vars** - Declares configuration files to load:
   - `default.yaml` - Environment-specific parameters
   - `datasets.json` - List of datasets to test
   - `models.json` - List of models to evaluate

2. **stages** - Pipeline steps executed in order:
   - **setup** - Creates output directories
   - **create_training_job** - Builds Docker images and runs model training (local) or submits SageMaker jobs (AWS)
   - **calculate_metric** - Computes performance metrics from predictions
   - Additional AWS stages: `upload_to_s3`, `deploy_to_ecr`, `monitor_training_job`

The `matrix` feature allows DVC to automatically generate stages for all dataset-model combinations:

```yaml
create_training_job:
  matrix:
    dataset: ${datasets}
    model: ${models}
  cmd: ...
  deps:
    - ${item.dataset.input_filename}
    - ${item.model.input_filename}
```

#### Dataset and Model Configuration

**datasets.json** - Specifies which datasets to test:
```json
{
  "datasets": [
    {
      "name": "charge_ladder",
      "input_filename": "datasets/charge_ladder.pgdata"
    }
  ]
}
```

**models.json** - Specifies which models to evaluate:
```json
{
  "models": [
    {
      "name": "pls",
      "input_filename": "models/pls/README.md"
    }
  ]
}
```

You can generate your datasets and models JSON files by using the `proteingym-base` command as below:

* `proteingym-base list-datasets datasets` will list all datasets under the folder `datasets`.
* `proteingym-base list-models models` will list all models under the folder `models`. Pay attention that in order for a model to be listed, it needs to define its model card as `README.md` with YAML front matter in its root folder.
* `jq` is used to filter the datasets and models.

```shell
proteingym-base list-datasets datasets | jq ... > benchmark/supervised/local/datasets.json
proteingym-base list-models models | jq ... > benchmark/supervised/local/models.json
```

### Environment Comparison

The benchmarking system supports three execution environments, each with different purposes and configurations:

#### Local Environment

<ins>Purpose:</ins> Fast iteration and debugging during development

<ins>Location:</ins> `benchmark/{supervised,zero_shot}/local/`

<ins>Configuration files:</ins>
- `dvc.yaml`- Pipeline definition
- `default.yaml` - Output directories and metrics
- `datasets.json` - Datasets to test
- `models.json` - Models to evaluate

<ins>Pipeline stages:</ins>
1. `setup` - Creates local directories
2. `create_training_job` - Builds Docker images and runs containers locally
3. `calculate_metric` - Computes metrics from predictions

**How to run:**
```shell
# Generate dataset and model configurations
proteingym-base list-datasets datasets | jq 'map(select(.name == "charge_ladder")) | map({name: .name, input_filename: .input_filename}) | {datasets: .}' > benchmark/supervised/local/datasets.json
proteingym-base list-models models | jq '[.[] | select(.tags | contains(["supervised"]))] | map({name: .name, input_filename: .input_filename}) | {models: .}' > benchmark/supervised/local/models.json

# Run the pipeline
dvc repro benchmark/supervised/local/dvc.yaml --single-item
```

**Advantages:**
- Quick feedback loop
- No cloud costs
- Easy debugging with local logs
- Full control over execution

#### AWS Environment

**Purpose:** Scalable cloud training for resource-intensive models

**Location:** `benchmark/{supervised,zero_shot}/aws/`

**Configuration files:**
- `dvc.yaml` - Pipeline with AWS-specific stages
- `default.yaml` - AWS credentials and configuration
- Same dataset/model JSON files as local

**Pipeline stages:**
1. `setup` - Creates local directories
2. `upload_to_s3` - Uploads datasets/models to S3
3. `deploy_to_ecr` - Builds and pushes Docker images to ECR
4. `create_training_job` - Submits SageMaker training jobs
5. `monitor_training_job` - Polls job status until completion
6. `calculate_metric` - Downloads results from S3 and computes metrics

**How to run:**
```shell
# Set AWS credentials
export AWS_ACCOUNT_ID=your-account-id
export AWS_PROFILE=your-profile

# Run the pipeline
dvc repro benchmark/supervised/aws/dvc.yaml --single-item
```

**Requirements:**
- AWS credentials configured
- SageMaker execution role
- S3 buckets for data and outputs
- ECR repositories for Docker images

**Advantages:**
- Handles large-scale training
- Parallel execution on powerful instances
- Persistent storage in S3
- Production-grade infrastructure

#### CI Environment (GitHub Actions)

**Purpose:** Automated validation on pull requests

**Location:** [.github/workflows/cml.yaml](.github/workflows/cml.yaml)

**How it works:**
The CI environment closely mirrors the local environment, making local testing a reliable way to predict CI results. The workflow:

1. **Setup** - Installs Python and CML (Continuous Machine Learning)
2. **Generate configurations** - Creates minimal dataset/model lists for fast validation:
   - Supervised: `charge_ladder`, `NEIME_2019` datasets
   - Zero-shot: `ranganathan` dataset
3. **Run DVC pipelines** - Executes both supervised and zero-shot benchmarks using `dvc repro`
4. **Report results** - Posts metrics to PR as a comment using CML

**Key similarities to local:**
- Uses identical [dvc.yaml](benchmark/supervised/local/dvc.yaml) files
- Runs Docker containers the same way
- Produces the same metric outputs
- Uses `--single-item` flag for faster execution (tests subset of combinations)

**Key differences from local:**
- Automated trigger on PR creation/updates
- Limited dataset/model selection for speed
- Posts results as PR comments
- Runs in Ubuntu environment (vs. your local OS)

**How to replicate CI locally:**
```shell
# Use the exact commands from cml.yaml
proteingym-base list-datasets datasets | jq 'map(select(.name == "charge_ladder" or .name == "NEIME_2019")) | map({name: .name, input_filename: .input_filename}) | {datasets: .}' > benchmark/supervised/local/datasets.json
proteingym-base list-models models | jq '[.[] | select(.tags | contains(["supervised"]))] | map({name: .name, input_filename: .input_filename}) | {models: .}' > benchmark/supervised/local/models.json

dvc repro benchmark/supervised/local/dvc.yaml --single-item
```

This ensures your local tests match CI expectations, reducing failed checks.

### How to Pass CI Validation

The CI system validates that models work correctly before merging code. Follow these steps to ensure your changes pass:

#### 1. Test Locally First

Always run the same pipeline that CI uses before pushing:

```shell
# For supervised models
proteingym-base list-datasets datasets | jq 'map(select(.name == "charge_ladder" or .name == "NEIME_2019")) | map({name: .name, input_filename: .input_filename}) | {datasets: .}' > benchmark/supervised/local/datasets.json
proteingym-base list-models models | jq '[.[] | select(.tags | contains(["supervised"]))] | map({name: .name, input_filename: .input_filename}) | {models: .}' > benchmark/supervised/local/models.json
dvc repro benchmark/supervised/local/dvc.yaml --single-item

# For zero-shot models
proteingym-base list-datasets datasets | jq 'map(select(.name == "ranganathan")) | map({name: .name, input_filename: .input_filename}) | {datasets: .}' > benchmark/zero_shot/local/datasets.json
proteingym-base list-models models | jq '[.[] | select(.tags | contains(["zero-shot"]))] | map({name: .name, input_filename: .input_filename}) | {models: .}' > benchmark/zero_shot/local/models.json
dvc repro benchmark/zero_shot/local/dvc.yaml --single-item
```

#### 2. Common Failure Scenarios

**Docker build failures:**
- **Cause:** Missing dependencies, syntax errors in Dockerfile
- **Fix:** Test `docker build` manually with the same command from [dvc.yaml](benchmark/supervised/local/dvc.yaml)
- **Check:** Ensure base images are accessible and all COPY/ADD paths exist

**Model execution failures:**
- **Cause:** Runtime errors, missing files, incorrect file paths
- **Fix:** Run the Docker container manually and check logs:
  ```shell
  docker run --rm -v $(pwd)/datasets/charge_ladder/README.md:/README.md your-model:latest train --dataset-file /README.md --model-card-file /model.md
  ```
- **Check:** Verify output CSV has required columns (`test`, `pred`)

**Metric calculation failures:**
- **Cause:** Missing columns in prediction CSV, incorrect data format
- **Fix:** Inspect the prediction CSV file in `benchmark/*/local/prediction/`
- **Check:** Ensure columns match `--actual-vector-col` and `--predict-vector-col` parameters

**Permission errors:**
- **Cause:** Docker volume mount issues, file permissions
- **Fix:** Ensure output directories exist and are writable
- **Check:** The `setup` stage should create all necessary directories

#### 3. Quick Validation Checklist

Before pushing:
- [ ] Local pipeline completes: `dvc repro benchmark/*/local/dvc.yaml --single-item`
- [ ] Metric files exist: `ls benchmark/*/local/metric/*.json`
- [ ] Docker images build: `docker build -f models/your-model/Dockerfile .`
- [ ] Model runs successfully: `docker run ... your-model:latest train ...`
- [ ] Required tags are set in model README (e.g., `tags: ["supervised"]` or `tags: ["zero-shot"]`)

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
├── routes/             # SvelteKit routes (pages)
│   ├── +page.svelte    # Homepage
│   ├── +layout.svelte  # Root layout
│   └── [slug]/         # Dynamic routes for model cards
├── lib/                # Shared utilities and components
│   ├── stores/         # Svelte stores for state management
│   ├── types/          # TypeScript type definitions
│   └── assets/         # Favicon and log
├── app.html            # HTML template
└── app.css             # Global styles
```

### Technology Stack

- **Framework:** SvelteKit 2.x with Svelte 5.x
- **Language:** TypeScript 5.x
- **Styling:** Tailwind CSS 4.x with Typography plugin
- **Build Tool:** Vite 7.x
- **Markdown:** [Marked](https://github.com/markedjs/marked) for parsing model card content
- **Code Quality:** ESLint, Prettier, svelte-check 
