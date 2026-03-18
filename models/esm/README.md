---
# Model identifier used for referencing this model in the benchmark system
name: esm

tags: ["zero-shot"]

# Multi-target support flag (accepted because ModelCard allows extras)
multi_y: false

hyper_parameters:
    # HuggingFace model checkpoint identifier for the specific ESM-2 variant
    location: "esm2_t30_150M_UR50D"
    # Scoring method: pseudo-ppl computes sequence likelihood via masked position prediction
    # Other options: "wt-marginals" (wildtype probabilities), "masked-marginals" (position-specific masking)
    scoring_strategy: "pseudo-ppl"
    # Whether to disable accelerator usage (false = use MPS/CUDA if available)
    # Device priority: MPS (macOS Metal) > CUDA (NVIDIA) > CPU
    nogpu: false
    # Offset index for sequence position alignment in tokenization
    offset_idx: 24
---

# Model Card for ESM-2

ESM-2 is a state-of-the-art protein model trained on a masked language modelling objective. It is suitable for fine-tuning on a wide range of tasks that take protein sequences as input. For detailed information on the model architecture and training data, please refer to the [accompanying paper](https://www.biorxiv.org/content/10.1101/2022.07.20.500902v2). You may also be interested in some demo notebooks ([PyTorch](https://colab.research.google.com/github/huggingface/notebooks/blob/main/examples/protein_language_modeling.ipynb), [TensorFlow](https://colab.research.google.com/github/huggingface/notebooks/blob/main/examples/protein_language_modeling-tf.ipynb)) which demonstrate how to fine-tune ESM-2 models on your tasks of interest.

Several ESM-2 checkpoints are available in the Hub with varying sizes. Larger sizes generally have somewhat better accuracy, but require much more memory and time to train:

| Checkpoint name | Num layers | Num parameters |
|------------------------------|----|----------|
| [esm2_t48_15B_UR50D](https://huggingface.co/facebook/esm2_t48_15B_UR50D) | 48 | 15B     |
| [esm2_t36_3B_UR50D](https://huggingface.co/facebook/esm2_t36_3B_UR50D) | 36 | 3B      | 
| [esm2_t33_650M_UR50D](https://huggingface.co/facebook/esm2_t33_650M_UR50D) | 33 | 650M    | 
| [esm2_t30_150M_UR50D](https://huggingface.co/facebook/esm2_t30_150M_UR50D) | 30 | 150M    | 
| [esm2_t12_35M_UR50D](https://huggingface.co/facebook/esm2_t12_35M_UR50D) | 12 | 35M     | 
| [esm2_t6_8M_UR50D](https://huggingface.co/facebook/esm2_t6_8M_UR50D)  | 6  | 8M      | 