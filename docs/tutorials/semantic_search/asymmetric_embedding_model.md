# Tutorial: Generating Embeddings Using a Local Asymmetric Embedding Model in OpenSearch

This tutorial demonstrates how to generate text embeddings using an asymmetric embedding model in OpenSearch, implemented within a Docker container. The example model used in this tutorial is the multilingual `intfloat/multilingual-e5-small` model from Hugging Face. You will learn how to prepare the model, register it in OpenSearch, and run inference to generate embeddings.

> **Note**: Make sure to replace all placeholders (e.g., `your_`) with your specific values.

---

## Prerequisites

- Docker Desktop installed and running on your local machine.
- Basic familiarity with Docker and OpenSearch.
- Access to the Hugging Face model `intfloat/multilingual-e5-small` (or another model of your choice).
---

## Step 1: Spin Up a Docker OpenSearch Cluster

To run OpenSearch in a local development environment, you can use Docker and a pre-configured `docker-compose` file.

### a. Update Cluster Settings

Before proceeding, ensure your cluster is configured to allow registering models. You can do this by updating the cluster settings via the following request:

```
PUT _cluster/settings
{
  "persistent": {
    "plugins.ml_commons.allow_registering_model_via_url": "true",
    "plugins.ml_commons.only_run_on_ml_node": "false",
    "plugins.ml_commons.model_access_control_enabled": "true",
    "plugins.ml_commons.native_memory_threshold": "99"
  }
}
```

This configuration ensures that OpenSearch can accept machine learning models from external URLs and can run models across non-ML nodes.

### b. Use a Docker Compose File

You can use this sample [file](https://opensearch.org/docs/latest/install-and-configure/install-opensearch/docker/#sample-docker-compose-file-for-development) as an example.
Once your `docker-compose.yml` file is ready, run the following command to start OpenSearch in the background:

```
docker-compose up -d
```

---

## Step 2: Prepare the Model for OpenSearch

In this tutorial, we’ll use the Hugging Face model `intfloat/multilingual-e5-small`, which is capable of generating multilingual embeddings. Follow these steps to prepare and zip the model for use in OpenSearch.

### a. Clone the Model from Hugging Face

To download the model, use the following steps:

1. Install Git Large File Storage (LFS) if you haven’t already:

   ```
   git lfs install
   ```

2. Clone the model repository:

   ```
   git clone https://huggingface.co/intfloat/multilingual-e5-small
   ```

This will download the model files into a directory on your local machine.

### b. Zip the Model Files

In order to upload the model to OpenSearch, you must zip the necessary model files (`model.onnx`, `sentencepiece.bpe.model`, and `tokenizer.json`). The `model.onnx` file is located in the `onnx` directory of the cloned repository.

Run the following command in the directory containing these files:

```
zip -r intfloat-multilingual-e5-small-onnx.zip model.onnx tokenizer.json sentencepiece.bpe.model
```

This command will create a zip file named `intfloat-multilingual-e5-small-onnx.zip`, with the previous mentioned files.

### c. Calculate the Model File Hash

Before registering the model, you need to calculate the SHA-256 hash of the zip file. Run this command to generate the hash:

```
shasum -a 256 intfloat-multilingual-e5-small-onnx.zip
```

Make a note of the hash value, as you will need it during the model registration process.

### d. Serve the Model File Using a Python HTTP Server

To allow OpenSearch to access the model file, you need to serve it via HTTP. Since this is a local development environment, you can use Python's built-in HTTP server:

Navigate to the directory containing the zip file and run the following command:

```
python3 -m http.server 8080 --bind 0.0.0.0
```

This will serve the zip file at `http://0.0.0.0:8080/intfloat-multilingual-e5-small-onnx.zip`. After registering the model, you can stop the server by pressing `Ctrl + C`.

---

## Step 3: Register a Model Group

Before registering the model itself, you need to create a model group. This helps organize models in OpenSearch. Run the following request to create a new model group:

```
POST /_plugins/_ml/model_groups/_register
{
  "name": "Asymmetric Model Group",
  "description": "A model group for local asymmetric models"
}
```

Take note of the `model_group_id` returned in the response, as it will be required when registering the model.

---

## Step 4: Register the Model

Now that you have the model zip file and the model group ID, you can register the model in OpenSearch. Run the following request:

```
POST /_plugins/_ml/models/_register
{
    "name": "e5-small-onnx",
    "version": "1.0.0",
    "description": "Asymmetric multilingual-e5-small model",
    "model_format": "ONNX",
    "model_group_id": "your_group_id",
    "model_content_hash_value": "your_model_zip_content_hash_value",
    "model_config": {
        "model_type": "bert",
        "embedding_dimension": 384,
        "framework_type": "sentence_transformers",
        "query_prefix": "query: ",
        "passage_prefix": "passage: ",
        "all_config": "{ \"_name_or_path\": \"intfloat/multilingual-e5-small\", \"architectures\": [ \"BertModel\" ], \"attention_probs_dropout_prob\": 0.1, \"hidden_size\": 384, \"num_attention_heads\": 12, \"num_hidden_layers\": 12, \"tokenizer_class\": \"XLMRobertaTokenizer\" }"
    },
    "url": "http://host.docker.internal:8080/intfloat-multilingual-e5-small-onnx.zip"
}
```

Replace `your_group_id` and `your_model_zip_content_hash_value` with the actual values from earlier. This will initiate the model registration process, and you’ll receive a task ID in the response.

To check the status of the registration, run:

```
GET /_plugins/_ml/tasks/your_task_id
```

Once successful, note the `model_id` returned, as you'll need it for deployment and inference.

---

## Step 5: Deploy the Model

After the model is registered, you can deploy it by running:

```
POST /_plugins/_ml/models/your_model_id/_deploy
```

Check the status of the deployment using the task ID:

```
GET /_plugins/_ml/tasks/your_task_id
```

When the model is successfully deployed, it will be in the **DEPLOYED** state, and you can use it for inference.

---

## Step 6: Run Inference

Now that your model is deployed, you can use it to generate text embeddings for both queries and passages.

### a. Generating Passage Embeddings

To generate embeddings for a passage, use the following request:

```
POST /_plugins/_ml/_predict/text_embedding/your_model_id
{
  "parameters": {
    "content_type": "passage"
  },
  "text_docs": [
    "Today is Friday, tomorrow will be my break day. After that, I will go to the library. When is lunch?"
  ],
  "target_response": ["sentence_embedding"]
}
```

The response will include a sentence embedding of size 384:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [384],
          "data": [0.0419328, 0.047480892, ..., 0.31158513, 0.21784715]
        }
      ]
    }
  ]
}
```

### b. Generating Query Embeddings

Similarly, you can generate embeddings for a query:

```
POST /_plugins/_ml/_predict/text_embedding/your_model_id
{
  "parameters": {
    "content_type": "query"
  },
  "text_docs": ["What day is it today?"],
  "target_response": ["sentence_embedding"]
}
```

The response will look like this:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [384],
          "data": [0.2338349, -0.13603798, ..., 0.37335885, 0.10653384]
        }
      ]
    }
  ]
}
```

---

## Next Steps

- Create an ingest pipeline for processing documents using asymmetric embeddings.
- Run a query using KNN (k-nearest neighbors) to search with your asymmetric model.

---

## References

- Wang, Liang, et al. (2024). *Multilingual E5 Text Embeddings: A Technical Report*. arXiv preprint arXiv:2402.05672. [Link](https://arxiv.org/abs/2402.05672)

---

