import importlib.util
import inspect
import json
import sys


def validate_model(
    model_name: str,
    package_prefix: str,
    app_name: str,
    command_name: str,
    command_params: list[str],
):
    """Validate a model's structure and entrypoint.

    Args:
        model_name: Name of the model to validate
        package_prefix: Package prefix for the model
        app_name: Expected Typer app name in the model
        command_name: Expected command name in the model
        command_params: Expected parameter names for the command
    """
    try:
        spec = importlib.util.spec_from_file_location(
            f"{package_prefix}_{model_name}.__main__",
            f"{package_prefix}_{model_name}/__main__.py",
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        app = getattr(module, app_name)

        entrypoint_command_found = False
        entrypoint_params_found = False

        for command in app.registered_commands:
            if command_name == command.callback.__name__:
                entrypoint_command_found = True

                sig = inspect.signature(command.callback)

                if command_params == list(sig.parameters.keys()):
                    entrypoint_params_found = True

                break

        validation_result = {
            "success": True,
            "entrypoint_command_found": entrypoint_command_found,
            "entrypoint_params_found": entrypoint_params_found,
            "module_loaded": True,
        }

        print(json.dumps(validation_result))

    except Exception as e:
        error_result = {
            "success": False,
            "entrypoint_command_found": False,
            "entrypoint_params_found": False,
            "module_loaded": False,
            "error": str(e),
        }
        print(json.dumps(error_result))
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 6:
        print(
            "Usage: python model_validator.py <model_name> <package_prefix> <command_name> <command_params_json>"
        )
        sys.exit(1)

    model_name = sys.argv[1]
    package_prefix = sys.argv[2]
    app_name = sys.argv[3]
    command_name = sys.argv[4]
    command_params = json.loads(sys.argv[5])

    validate_model(model_name, package_prefix, app_name, command_name, command_params)
