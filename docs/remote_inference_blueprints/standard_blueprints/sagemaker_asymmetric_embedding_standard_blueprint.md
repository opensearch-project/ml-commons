# AWS SageMaker Asymmetric Embedding Model Standard Blueprint

This blueprint demonstrates how to deploy an asymmetric embedding model (multilingual-e5-small) using AWS SageMaker and integrate it with OpenSearch for semantic search. The asymmetric model uses different prefixes for queries and passages to optimize search performance.

## Overview

The asymmetric embedding model provides separate embeddings for queries and passages, improving semantic search accuracy. This blueprint shows how to:

1. Create a SageMaker connector
2. Register a model group
3. Register and deploy the model
4. Test the model inference with query and passage embeddings

## Prerequisites

1. AWS account with SageMaker access
2. SageMaker endpoint deployed with the multilingual-e5-small model
3. AWS credentials with appropriate permissions

## Steps

### 1. Create SageMaker Connector

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "sagemaker-e5-asymmetric-connector",
  "description": "Connector for multilingual-e5-small asymmetric model",
  "version": "1",
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "<YOUR_AWS_REGION>",
    "service_name": "sagemaker"
  },
  "credential": {
    "access_key": "<YOUR_AWS_ACCESS_KEY>",
    "secret_key": "<YOUR_AWS_SECRET_KEY>",
    "session_token": "<YOUR_AWS_SESSION_TOKEN>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://runtime.sagemaker.<YOUR_AWS_REGION>.amazonaws.com/endpoints/<YOUR_SAGEMAKER_ENDPOINT>/invocations",
      "headers": {
        "content-type": "application/json"
      },
      "request_body": "{ \"texts\": ${parameters.texts}, \"content_type\": \"${parameters.content_type}\" }"
    }
  ]
}
```

Replace the placeholders:
- `<YOUR_AWS_REGION>`: Your AWS region (e.g., us-east-1)
- `<YOUR_AWS_ACCESS_KEY>`: Your AWS access key
- `<YOUR_AWS_SECRET_KEY>`: Your AWS secret key
- `<YOUR_AWS_SESSION_TOKEN>`: Your AWS session token (if using temporary credentials)
- `<YOUR_SAGEMAKER_ENDPOINT>`: Your SageMaker endpoint name

### 2. Create Model Group

```json
POST /_plugins/_ml/model_groups/_register
{
  "name": "e5_asymmetric_group",
  "description": "Model group for asymmetric E5 embedding model"
}
```

### 3. Register and Deploy Model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "e5-asymmetric-remote",
  "function_name": "remote",
  "model_group_id": "<MODEL_GROUP_ID>",
  "description": "Asymmetric E5 embedding model for semantic search",
  "connector_id": "<CONNECTOR_ID>",
  "model_config": {
    "model_type": "text_embedding",
    "embedding_dimension": 384,
    "framework_type": "SENTENCE_TRANSFORMERS",
    "additional_config": {
      "space_type": "l2",
      "is_asymmetric": true,
      "model_family": "e5",
      "query_prefix": "query: ",
      "passage_prefix": "passage: "
    }
  }
}
```

Replace:
- `<MODEL_GROUP_ID>`: The model group ID from step 2
- `<CONNECTOR_ID>`: The connector ID from step 1

### 4. Test Query Embedding

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "texts": ["What is machine learning?"],
    "content_type": "query"
  }
}
```

### 5. Test Passage Embedding

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "texts": ["Machine learning is a subset of artificial intelligence that focuses on algorithms and statistical models."],
    "content_type": "passage"
  }
}
```

### 6. Test Bulk Embedding

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "texts": [
      "What is machine learning?",
      "How does neural search work?",
      "What are embedding models?"
    ],
    "content_type": "query"
  }
}
```

Replace `<MODEL_ID>` with your deployed model ID.

## Example Response

### Query Embedding Response

```json
{
  "inference_results": [
    {
      "output": [
        {
          "dataAsMap": {
            "response": [
              [-0.123, 0.456, -0.789, ...]
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### Passage Embedding Response

```json
{
  "inference_results": [
    {
      "output": [
        {
          "dataAsMap": {
            "response": [
              [0.321, -0.654, 0.987, ...]
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### Bulk Embedding Response

```json
{
  "inference_results": [
    {
      "output": [
        {
          "dataAsMap": {
            "response": [
              [0.123, -0.456, 0.789, ...],
              [0.321, -0.654, 0.987, ...],
              [0.111, -0.222, 0.333, ...]
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Model Configuration Details

The asymmetric model configuration includes:

- **embedding_dimension**: 384 (multilingual-e5-small dimension)
- **is_asymmetric**: true (enables different processing for queries vs passages)
- **query_prefix**: "query: " (prefix added to search queries)
- **passage_prefix**: "passage: " (prefix added to document passages)
- **space_type**: "l2" (distance metric for similarity calculation)

## References

- [Multilingual E5 Model Documentation](https://huggingface.co/intfloat/multilingual-e5-small)
- [OpenSearch ML Commons Documentation](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/index/)
- [SageMaker Endpoints Documentation](https://docs.aws.amazon.com/sagemaker/latest/dg/deploy-model.html)
- [Asymmetric Embedding Models Guide](https://opensearch.org/docs/latest/search-plugins/neural-search/)
