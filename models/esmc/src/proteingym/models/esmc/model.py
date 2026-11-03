"""
Wrapper class around ESMC (https://huggingface.co/biohub/esmc-model-family)
"""
from os import PathLike
from typing import Literal, Self, Sequence

from loguru import logger

from evedesign.model import assign_scores_to_instances
from evedesign.models.esm2 import ESM2
from evedesign.system import Mutant, Mutation, System, SystemInstance
from evedesign.types import DeviceType, StatusCallback, BatchSize

try:
    import torch  # noqa
    from transformers import AutoModelForMaskedLM, AutoTokenizer  # noqa
    IMPORT_AVAILABLE = True
except ImportError:
    IMPORT_AVAILABLE = False


ESMCModelName = Literal["ESMC-300M", "ESMC-600M", "ESMC-6B"]


class ESMC(ESM2):
    """
    Wrapper class around ESMC, reusing ESM2's masked-marginal scoring and scanning.
    Warnings upon loading can be ignored (https://github.com/huggingface/transformers/issues/39405).
    """
    available = IMPORT_AVAILABLE
    name: str = "ESMC"
    # Language Modeling Materializes a World Model of Protein Biology
    citations: list[str] = ["https://biohub.ai/papers/esm_protein.pdf"]

    # core properties
    requires_target: bool = True
    # score() diffs instances against the reference by position, so no indels
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = True
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = []
    optional_entity_attributes: list[str] | None = []

    # Rotary embeddings, so no hard length cap (overrides ESM2's 1022)
    MAX_SEQ_LENGTH: int | None = None

    def __init__(
        self,
        model_name: ESMCModelName = "ESMC-300M",
        model_dir_path: str | PathLike | None = None,
        batch_size: BatchSize = 64,
        keep_model_after_build: bool = False,
        device: DeviceType = "cpu",
    ):
        """
        Instantiate an ESMC model wrapper.

        Parameters
        ----------
        model_name
            ESMC checkpoint on the HuggingFace Hub (`biohub/{model_name}`), used if
            model_dir_path is not set
        model_dir_path
            Local checkpoint directory, takes precedence over model_name
        batch_size
            Number of sequences per forward pass
        keep_model_after_build
            Accepted for parity with ESM2 (unused there)
        device
            Device to run the model on ("cpu", "cuda", or "mps").
        """
        if not self.available:
            raise ImportError(
                "transformers package could not be imported. Is it installed already?"
            )

        # ESM2.__init__ only stores model_name, so ESMC names are safe to pass
        super().__init__(
            model_name=model_name,  # type: ignore[arg-type]
            model_dir_path=model_dir_path,
            batch_size=batch_size,
            keep_model_after_build=keep_model_after_build,
            device=device,
        )

        # Overwrite ESM2's hardcoded 1022-token cap
        self.max_seq_length = self.MAX_SEQ_LENGTH

    @classmethod
    def can_model(cls, system: System, data: None = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support data parameter (must be None)"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have defined rep sequence"

        if cls.MAX_SEQ_LENGTH is not None and len(target.rep) > cls.MAX_SEQ_LENGTH:
            return False, (
                f"Sequence length ({len(target.rep)}) exceeds maximum allowed "
                f"({cls.MAX_SEQ_LENGTH})"
            )

        return True, ""

    def _load_model(self):
        """
        Load the ESMC model and tokenizer, from model_dir_path or the HuggingFace Hub.

        Raises
        ------
        ValueError
            If the model or tokenizer cannot be loaded
        """
        if self.model is not None:
            return

        if self.model_dir_path is None:
            name_to_load = f"biohub/{self.model_name}"
        else:
            name_to_load = str(self.model_dir_path)

        try:
            self.model = AutoModelForMaskedLM.from_pretrained(
                name_to_load
            ).to(self.device)
            self.tokenizer = AutoTokenizer.from_pretrained(name_to_load)
        except Exception as e:
            logger.error(f"Error loading ESMC model from {name_to_load}: {e}")
            raise ValueError(
                f"Failed to load ESMC model from {name_to_load}: {e}"
            )

        self.model.eval()

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None
    ) -> Self:
        """
        Attach the system; the model itself is loaded lazily at scoring time.

        Parameters
        ----------
        system
            Single-entity protein system with a defined target sequence
        data
            Unused, must be None
        status_callback
            Unused

        Returns
        -------
        Self
            The built model
        """
        self.can_model_or_raise(system, data)
        self._system = system

        target = system[0]
        if self.max_seq_length is not None and len(target.rep) > self.max_seq_length:
            raise ValueError(
                f"Sequence length ({len(target.rep)}) exceeds maximum allowed by "
                f"ESMC ({self.max_seq_length})"
            )

        self.encoding = None
        self.token_ids = None

        return self

    def _validate_instances_and_max_length(
        self,
        instances: Sequence[SystemInstance],
    ) -> None:
        """
        Validate that instances are fixed-length substitutions of the target.

        Parameters
        ----------
        instances
            Instances to validate

        Raises
        ------
        ValueError
            If an instance is invalid or exceeds the maximum sequence length
        """
        for instance in instances:
            self.system.valid_instance(
                instance,
                validate_reps=True,
                require_reps=True,
                fixed_length=True,
                allow_deletions=False,
                raise_invalid=True,
            )

            seq_len = len(instance[0].rep)
            if self.max_seq_length is not None and seq_len > self.max_seq_length:
                raise ValueError(
                    f"Sequence length ({seq_len}) exceeds maximum allowed by ESMC "
                    f"({self.max_seq_length})"
                )

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None
    ) -> list[SystemInstance]:
        """
        Score instances by masked marginals relative to the reference sequence.

        Substitutions are recovered by diffing against `self.system[0].rep` and
        scored with the inherited `ESM2.score_mutants()` (additive across positions).

        Parameters
        ----------
        instances
            Instances to score
        status_callback
            Optional progress callback

        Returns
        -------
        list[SystemInstance]
            Scored copies of the instances
        """
        self.ready_or_raise()
        self._validate_instances_and_max_length(instances)

        target = self.system[0]
        ref_seq = "".join(target.rep)

        mutants: list[Mutant] = [
            [
                Mutation(entity=0, pos=target.first_index + i, ref=ref_aa, to=aa)
                for i, (ref_aa, aa) in enumerate(zip(ref_seq, "".join(instance[0].rep)))
                if aa != ref_aa
            ]
            for instance in instances
        ]

        ref_instance = self.system.rep_to_instance()
        scored = self.score_mutants(ref_instance, mutants, status_callback)
        scores = [inst.score for inst in scored]

        return assign_scores_to_instances(instances, scores)
