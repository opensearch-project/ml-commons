# AWS SageMaker Asymmetric Embedding Model Standard Blueprint

This blueprint demonstrates how to deploy an asymmetric embedding model (multilingual-e5-small) using AWS SageMaker and integrate it with OpenSearch for semantic search. The asymmetric model uses different prefixes for queries and passages to optimize search performance.

## Overview
### Asymmetric Embeddings
The E5 model uses different embeddings for different content types:

#### Passage Embeddings (Ingestion)
```
{
  "parameters": {
    "texts": ["Central Park is a large public park..."],
    "content_type": "passage"
  }
}
```
* Adds "passage: " prefix internally
* Optimized for being found/retrieved
* Used during document indexing

#### Query Embeddings (Search)
```
{
  "parameters": {
    "texts": ["parks and green spaces"],
    "content_type": "query"
  }
}
```
* Adds "query: " prefix internally
* Optimized for finding relevant content
* Used during search queries


## Prerequisites

1. AWS account with SageMaker access
2. SageMaker endpoint deployed with the multilingual-e5-small model (https://github.com/opensearch-project/opensearch-py-ml/pull/587)
3. AWS credentials with appropriate permissions

## Remote Connector Preparation
### Model Configuration Details

The asymmetric model configuration includes:

- **embedding_dimension**: 384 (multilingual-e5-small dimension)
- **is_asymmetric**: true (enables different processing for queries vs passages)
- **query_prefix**: "query: " (prefix added to search queries)
- **passage_prefix**: "passage: " (prefix added to document passages)
- **space_type**: "l2" (distance metric for similarity calculation)

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

## Neural Search with Asymmetric Model

### 1. Create Index with KNN Vector Field

```bash
curl -XPUT "http://localhost:9200/nyc_facts" \
-H 'Content-Type: application/json' \
-d'{
  "settings": {
    "index": {
      "knn": true
    }
  },
  "mappings": {
    "properties": {
      "description": {
        "type": "text"
      },
      "description_embedding": {
        "type": "knn_vector",
        "dimension": 384,
        "method": {
          "name": "hnsw",
          "space_type": "l2",
          "engine": "lucene"
        }
      }
    }
  }
}'
```

### 2. Create Ingest Pipeline

```bash
curl -XPUT "http://localhost:9200/_ingest/pipeline/nyc_facts_pipeline" \
-H 'Content-Type: application/json' \
-d'{
  "description": "Ingest pipeline for NYC facts with remote model",
  "processors": [
    {
      "ml_inference": {
        "model_id": "<MODEL_ID>",
        "function_name": "remote",
        "model_input": "{\"parameters\":{\"texts\":[\"{{description}}\"], \"content_type\":\"passage\"}}",
        "input_map": [
          {
            "description": "description"
          }
        ]
      }
    },
    {
      "script": {
        "source": "ctx.description_embedding = ctx.inference_results.response[0]; ctx.remove(\"inference_results\");"
      }
    }
  ]
}'
```

### 3. Bulk Ingest Documents

```bash
curl -XPOST "http://localhost:9200/_bulk?pipeline=nyc_facts_pipeline" \
-H 'Content-Type: application/json' \
-d'{ "index": { "_index": "nyc_facts" } }
{ "title": "Central Park", "description": "A large public park in the heart of New York City, offering a wide range of recreational activities." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Yankee Stadium", "description": "Home to the New York Yankees, this baseball stadium is a historic landmark in the Bronx." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Citi Field", "description": "The home stadium of the New York Mets, located in Queens, known for its modern design and fan-friendly atmosphere." }
'
```

### 4. Neural Search Query

```bash
curl -XGET "http://localhost:9200/nyc_facts/_search" \
-H 'Content-Type: application/json' \
-d'{
  "_source": ["title", "description"],
  "query": {
    "neural": {
      "description_embedding": {
        "query_text": "What are some places for sports in NYC?",
        "model_id": "<MODEL_ID>",
        "k": 3
      }
    }
  }
}'
```


## References

- [Multilingual E5 Model Documentation](https://huggingface.co/intfloat/multilingual-e5-small)
- [OpenSearch ML Commons Documentation](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/index/)
- [SageMaker Endpoints Documentation](https://docs.aws.amazon.com/sagemaker/latest/dg/deploy-model.html)
- [Asymmetric Embedding Models Guide](https://opensearch.org/docs/latest/search-plugins/neural-search/)
