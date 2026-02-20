from importlib import resources

HYDRA_CONFIG_PATH = resources.files("kermut").joinpath("hydra_configs")
HYDRA_TEMP_CONFIG_PATH = resources.files("pg_model").joinpath("hydra_configs")
