# Model Validation

Please check and update the following values for your own models:

- [ ] category: supervised
- [ ] model-index: model0
- [ ] model-name: pls

> [!TIP]
> `category` can be either `supervised` or `zero_shot`, which defines different games to benchmark. `category` is used in [params.yaml](../params.yaml) as prefix for datasets and models, and it is also used to configure which data are used in [data](../data) folder.
>
> `model-index` comes in this format `model0` with 0 starting from the first model's `path` in [params.yaml](../params.yaml).
>
> `model-name` comes from [params.yaml](../params.yaml) as well, it is the `name` of the above model `path`.
