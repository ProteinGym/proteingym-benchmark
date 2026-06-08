import logging
from typing import Sequence

import numpy as np
from evedesign.models.esm2 import ESM2
from evedesign.system import System, SystemInstance
from evedesign.types import StatusCallback
from proteingym.base.model import ModelCard

logger = logging.getLogger(__name__)


def load(model_card: ModelCard) -> ESM2:
    """Instantiate the evedesign ESM2 wrapper from a model card.

    Args:
        model_card: Model card whose hyper_parameters map onto ESM2.__init__.

    Returns:
        An unbuilt ESM2 wrapper.
    """
    return ESM2(**model_card.hyper_parameters)


def build(
    model: ESM2,
    system: System,
    data: None = None,
    status_callback: StatusCallback | None = None,
) -> ESM2:
    """Build the ESM2 wrapper on a system.

    Args:
        model: An ESM2 wrapper
        system: evedesign System describing the modeling task (single protein).
        data: None for the (unsupervised) ESM2 wrapper.
        status_callback: Optional progress callback.

    Returns:
        The built ESM2 wrapper, ready to score sequences.
    """
    return model.build(system, data=data, status_callback=status_callback)


def score(
    model: ESM2,
    instances: Sequence[SystemInstance],
    status_callback: StatusCallback | None = None,
) -> np.ndarray[tuple[int], np.dtype[float]]:
    """Score sequences using the ESM2 wrapper.

    Args:
        model: A built ESM2 wrapper
        instances: System instances to score.
        status_callback: Optional progress callback.

    Returns:
        Array of per-instance log-likelihood scores.
    """
    return model.score(instances, status_callback=status_callback)
