---
name: "pls"

hyper_params:
    n_components: 2
    aa_alphabet: ["A", "C", "D", "E", "F", "G", "H", "I", "K", "L", "M", "N", "P", "Q", "R", "S", "T", "V", "W", "Y"]
    aa_alphabet_length: 20
---

# Model Card for PLS Regression

Partial Least Squares (PLS) regression is a dimensionality reduction technique that finds linear combinations of input variables that best explain the variance in both predictors and response variables. This model uses `PLSRegression` from `sklearn.cross_decomposition` to build predictive models for protein fitness prediction tasks.

PLS is particularly useful when dealing with high-dimensional data where the number of features exceeds the number of observations, making it well-suited for protein sequence analysis where amino acid features can be numerous relative to available experimental data.
