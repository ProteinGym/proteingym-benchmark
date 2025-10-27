---
# Model identifier used for referencing this model in the benchmark system
name: "ritaregressor"

tags: ["supervised"]

hyper_parameters:
    # Which embedder class to use
    huggingface_model_name: RITA
    # Which of the models supported by the embedder class to load from huggingface
    embedder_model_name: RITA_s
    # What method to use to pool per residue embeddings across the sequence
    embedder_pooling: mean
    # Alpha regularization parameter for the ridge regression model
    alpha: 1.0
    # Backend for producing embeddings
    device: cuda
    # Where to save embeddings, setting to "TEMP" uses a temporary directory as cache
    cache_dir: TEMP
---
> [!WARNING]
> Just like pls, this model does not read data splitting instructions and uses dummy logic for generating train and test splits. 
> This model currently also does not yet take into account extra features.

> [!NOTE]
> To fit within the compute budget of the CI runner, this model uses RITA_s instead of the more powerful RITA_xl. 
# Model Card for ritaregressor

The ritaregressor is a supervised VEP model that produces sequence embeddings and fits those to properties by ridge regression. The sequence embeddings are obtained using [RITA_xl](https://huggingface.co/lightonai/RITA_xl), which was first described in [RITA: a Study on Scaling Up Generative Protein Sequence Models](https://arxiv.org/abs/2205.05789).