import streamlit as st
import pandas as pd

df = pd.read_csv("data/metrics.csv")

st.title("ProteinGym2 Metrics")
st.dataframe(df)