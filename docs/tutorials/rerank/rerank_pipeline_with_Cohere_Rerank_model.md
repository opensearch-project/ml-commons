# Topic

[Reranking pipeline](https://opensearch.org/docs/latest/search-plugins/search-relevance/reranking-search-results/) is a feature released in OpenSearch 2.12.
It can rerank search results, providing a relevance score for each document in the search results with respect to the search query.
The relevance score is calculated by a cross-encoder model. 

This tutorial explains how to use the [Cohere Rerank](https://docs.cohere.com/reference/rerank-1) model in a reranking pipeline. 

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 1. Create Cohere Rerank model

Create a connector:
```
POST /_plugins/_ml/connectors/_create
{
    "name": "cohere-rerank",
    "description": "The connector to Cohere reanker model",
    "version": "1",
    "protocol": "http",
    "credential": {
        "cohere_key": "your_cohere_api_key"
    },
    "parameters": {
        "model": "rerank-english-v2.0"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://api.cohere.ai/v1/rerank",
            "headers": {
                "Authorization": "Bearer ${credential.cohere_key}"
            },
            "request_body": "{ \"documents\": ${parameters.documents}, \"query\": \"${parameters.query}\", \"model\": \"${parameters.model}\", \"top_n\": ${parameters.top_n} }",
            "pre_process_function": "connector.pre_process.cohere.rerank",
            "post_process_function": "connector.post_process.cohere.rerank"
        }
    ]
}
```
Use the connector ID from the response to create a model:
```
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "cohere rerank model",
    "function_name": "remote",
    "description": "test rerank model",
    "connector_id": "your_connector_id"
}
```
Note the model ID in the response; you will use it in the following steps.

Test the model using the Predict API:
```
POST _plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "query": "What is the capital of the United States?",
    "documents": [
      "Carson City is the capital city of the American state of Nevada.",
      "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
      "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
      "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
    ],
    "top_n": 4
  }
}
```

The value `top_n` must be the size of `documents` list. This is to keep compatible with Rerank Pipeline.
You can customize the number of top document returns in the response using the OpenSearch query parameter size. Refer to step 2.2 for more details.

Sample response:

Explanation of the response:
1. The response contains 4 `similarity` outputs. For each `similarity` output, the `data` array contains a relevance score between each document and the query.
2. The `similarity` outputs are provided in the order of the input documents; the first result of similarity pertains to the first document.
This differs from the default output of the Cohere Rerank model, which orders documents by relevance scores.
The document order is changed in the `connector.post_process.cohere.rerank` post-processing function in order to make the output compatible with a reranking pipeline.
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.10194652
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.0721122
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.98005307
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.27904198
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
## 2. Reranking pipeline
### 2.1 Ingest test data
```
POST _bulk
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "Carson City is the capital city of the American state of Nevada." }
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan." }
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district." }
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states." }

```
### 2.2 Create reranking pipeline
```
PUT /_search/pipeline/rerank_pipeline_cohere
{
    "description": "Pipeline for reranking with Cohere Rerank model",
    "response_processors": [
        {
            "rerank": {
                "ml_opensearch": {
                    "model_id": "your_model_id_created_in_step1"
                },
                "context": {
                    "document_fields": ["passage_text"]
                }
            }
        }
    ]
}
```
### 2.2 Test reranking

You can tune `size` if you want to return less result. For example, set `"size": 2` if you want to return top 2 documents.

```
GET my-test-data/_search?search_pipeline=rerank_pipeline_cohere
{
  "query": {
    "match_all": {}
  },
  "size": 4,
  "ext": {
    "rerank": {
      "query_context": {
         "query_text": "What is the capital of the United States?"
      }
    }
  }
}
```
Response:
```
{
  "took": 0,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": 0.98005307,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "zbUOw40B8vrNLhb9vBif",
        "_score": 0.98005307,
        "_source": {
          "passage_text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
        }
      },
      {
        "_index": "my-test-data",
        "_id": "zrUOw40B8vrNLhb9vBif",
        "_score": 0.27904198,
        "_source": {
          "passage_text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
        }
      },
      {
        "_index": "my-test-data",
        "_id": "y7UOw40B8vrNLhb9vBif",
        "_score": 0.10194652,
        "_source": {
          "passage_text": "Carson City is the capital city of the American state of Nevada."
        }
      },
      {
        "_index": "my-test-data",
        "_id": "zLUOw40B8vrNLhb9vBif",
        "_score": 0.0721122,
        "_source": {
          "passage_text": "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
        }
      }
    ]
  },
  "profile": {
    "shards": []
  }
}
```
Test without reranking pipeline:
```
GET my-test-data/_search
{
  "query": {
    "match_all": {}
  },
  "ext": {
    "rerank": {
      "query_context": {
         "query_text": "What is the capital of the United States?"
      }
    }
  }
}
```
The first document in the response is `Carson City is the capital city of the American state of Nevada`, which is incorrect.
```
{
  "took": 0,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": 1,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "y7UOw40B8vrNLhb9vBif",
        "_score": 1,
        "_source": {
          "passage_text": "Carson City is the capital city of the American state of Nevada."
        }
      },
      {
        "_index": "my-test-data",
        "_id": "zLUOw40B8vrNLhb9vBif",
        "_score": 1,
        "_source": {
          "passage_text": "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
        }
      },
      {
        "_index": "my-test-data",
        "_id": "zbUOw40B8vrNLhb9vBif",
        "_score": 1,
        "_source": {
          "passage_text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
        }
      },
      {
        "_index": "my-test-data",
        "_id": "zrUOw40B8vrNLhb9vBif",
        "_score": 1,
        "_source": {
          "passage_text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
        }
      }
    ]
  }
}
```