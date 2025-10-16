# Manifest

The manifest defines the configuration for a model.

## Schema

The manifest schema is defined in this section. Let's start with an example followed by the schema definition.

``` TOML
name = "esm"

[hyper_parameters]
location = "esm2_t30_150M_UR50D"
scoring_strategy = "wt-marginals"
nogpu = false
offset_idx = 24
```

### Top-level

The top-level of the manifest contains the model metadata and its hyper parameters.

| **Field**          | **Type**                              | **Required** | **Default** | **Description**                     |
|--------------------|---------------------------------------|--------------|-------------|-------------------------------------|
| `name`             | `string`                              | Yes          | N/A         | The name of the model.              |
| `hyper_parameters` | `map[str, bool | int | float | str ]` | No           | Empty dict  | The hyper parameters of the model.  |
