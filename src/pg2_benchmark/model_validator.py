import inspect
import json
import sys
from importlib.metadata import entry_points


def validate_model_entrypoint(package_name: str):
    """Validate if a model package has the required 'train' entrypoint."""

    try:
        # Get all entry points and filter by package name
        eps = entry_points()

        package_entrypoints = [ep for ep in eps if ep.dist.name == package_name]

        # Look for console_scripts entry points (where typer apps are typically registered)
        entry_points_found = []
        for ep in package_entrypoints:
            if ep.group == "console_scripts":
                # Load the entry point to get the typer app
                app = ep.load()

                if hasattr(app, "registered_commands"):
                    for command in app.registered_commands:
                        sig = inspect.signature(command.callback)

                        found_entry_point = {
                            "name": command.callback.__name__,
                            "params": list(sig.parameters.keys()),
                        }

                        entry_points_found.append(found_entry_point)

        validation_result = {
            "module_loaded": True,
            "entry_points_found": entry_points_found,
        }

        print(json.dumps(validation_result), flush=True)

    except Exception as e:
        validation_result = {
            "module_loaded": False,
            "entry_points_found": [],
            "error": str(e),
        }
        print(json.dumps(validation_result), flush=True)
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python model_validator.py <package_name>")
        sys.exit(1)

    package_name = sys.argv[1]

    validate_model_entrypoint(package_name)
