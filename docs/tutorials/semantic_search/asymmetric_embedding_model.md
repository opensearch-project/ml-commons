# Topic

This tutorial shows how to generate embeddings using a local asymmetric embedding model in OpenSearch implemented in a Docker container .

Note: Replace the placeholders that start with `your_` with your own values.

# Steps
## 1. Spin up a docker OpenSearch Cluster

  ###  a. Use a docker compose file

## 2. Prepare the model for OpenSearch

  ###  a. Clone the model
  ###  b. Zip the contents
  ###  c. Calculate hash
  ###  d. service the zip file using a python server
 
   -  can cancel the server now

## 3. Register a model group
## 4. Register the model
## 5. Deploy The model
## 6. Run Inference
## Next steps

- Create an ingest pipeline for your documents with assymetric embeddings
- Run a query using KNN with your asymmetric model


# References

Wang, Liang, et al. (2024). *Multilingual E5 Text Embeddings: A Technical Report*. arXiv preprint arXiv:2402.05672. [Link](https://arxiv.org/abs/2402.05672)
