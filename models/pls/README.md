---
# Model identifier used for referencing this model in the benchmark system
name: pls

tags: ["supervised"]

hyper_parameters:
    # Number of PLS components to extract (dimensionality of the reduced space)
    n_components: 2
    # Standard 20 amino acid single-letter codes
    aa_alphabet: ["A", "C", "D", "E", "F", "G", "H", "I", "K", "L", "M", "N", "P", "Q", "R", "S", "T", "V", "W", "Y"]
    # Total number of amino acids in the alphabet (must match aa_alphabet length)
    aa_alphabet_length: 20
---

# Model Card for PLS Regression

Partial Least Squares (PLS) regression is a dimensionality reduction technique that finds linear combinations of input variables that best explain the variance in both predictors and response variables. This model uses `PLSRegression` from `sklearn.cross_decomposition` to build predictive models for protein fitness prediction tasks.

PLS is particularly useful when dealing with high-dimensional data where the number of features exceeds the number of observations, making it well-suited for protein sequence analysis where amino acid features can be numerous relative to available experimental data.
