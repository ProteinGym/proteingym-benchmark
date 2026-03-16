from importlib import resources

# HYDRA_CONFIG_PATH = resources.files("kermut").joinpath("hydra_configs")
# HYDRA_TEMP_CONFIG_PATH = resources.files("pg_model").joinpath("hydra_configs")
HYDRA_CONFIG_PATH = resources.files("proteingym.models.pkermut").joinpath("kermut/hydra_configs")
HYDRA_TEMP_CONFIG_PATH = resources.files("proteingym.models.pkermut").joinpath("pg_model/hydra_configs")
