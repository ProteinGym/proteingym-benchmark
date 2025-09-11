import logging


def setup_logger(*, level: int = logging.INFO) -> None:
    """Set up the logger for the application.

    Args:
        level: The logging level to set. Defaults to logging.INFO.
    """
    logger = logging.getLogger("pg2_benchmark")
    logger.setLevel(level)

    # Avoid adding multiple handlers if setup is called multiple times
    if logger.handlers:
        return

    stream_handler = logging.StreamHandler()
    stream_handler.setLevel(level)

    formatter = logging.Formatter(
        "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)
