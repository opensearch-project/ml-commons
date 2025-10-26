# Topic

A [reranking pipeline](https://opensearch.org/docs/latest/search-plugins/search-relevance/reranking-search-results/) can rerank search results, providing a relevance score for each document in the search results with respect to the search query. The relevance score is calculated by a cross-encoder model.

This tutorial illustrates using the [Amazon Bedrock Rerank API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_Rerank.html) to rerank search results using a model hosted on Amazon Bedrock.

Note: Replace the placeholders beginning with the prefix your_ with your own values.

# Steps

## Prerequisite 1: Test the model on Amazon Bedrock
Before using your model, test it on Amazon Bedrock. For supported reranker models, see [Supported Regions and models for reranking in Amazon Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/rerank-supported.html). For model IDs, see [Supported foundation models in Amazon Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html). To perform a reranking test, use the following code:

```python
import json
import boto3
bedrock_region = "your_bedrock_model_region_like_us-west-2"
bedrock_agent_runtime_client = boto3.client("bedrock-agent-runtime", region_name=bedrock_region)

model_id = "amazon.rerank-v1:0"

response = bedrock_agent_runtime_client.rerank(
    queries=[
        {
            "textQuery": {
                "text": "What is the capital city of America?",
            },
            "type": "TEXT"
        }
    ],
    rerankingConfiguration={
        "bedrockRerankingConfiguration": {
            "modelConfiguration": {
                "modelArn": f"arn:aws:bedrock:{bedrock_region}::foundation-model/{model_id}"
            },
        },
        "type": "BEDROCK_RERANKING_MODEL"
    },
    sources=[
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "Carson City is the capital city of the American state of Nevada.",
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },        
    ]
)

results = response["results"]
print(json.dumps(results, indent=2))
```

The reranked results are ordered by the highest score:
```
[
  {
    "index": 2,
    "relevance_score": 0.7711548724998493
  },
  {
    "index": 0,
    "relevance_score": 0.0025114635138098534
  },
  {
    "index": 1,
    "relevance_score": 2.4876490010363496e-05
  },
  {
    "index": 3,
    "relevance_score": 6.339210403977635e-06
  }
]
```


To sort the results by index, use the following code:
```python
print(json.dumps(sorted(results, key=lambda x: x['index']),indent=2))
```

The following are the results sorted by index:
```
[
  {
    "index": 0,
    "relevance_score": 0.0025114635138098534
  },
  {
    "index": 1,
    "relevance_score": 2.4876490010363496e-05
  },
  {
    "index": 2,
    "relevance_score": 0.7711548724998493
  },
  {
    "index": 3,
    "relevance_score": 6.339210403977635e-06
  }
]
```

If you see an authorization error when running the test code, check the rerank model access settings on Amazon Bedrock. To check and add model access settings, see [Add or remove access to Amazon Bedrock foundation models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access-modify.html).

## Step 1: Create a connector and register the model

To create a connector and register the model, use the following steps.

### Step 1.1: Create a connector for the model

First, create a connector for the model.

If you are using self-managed OpenSearch, supply your AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Rerank API",
  "description": "Test connector for Amazon Bedrock Rerank API",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "your_access_key",
    "secret_key": "your_secret_key",
    "session_token": "your_session_token"
  },
  "parameters": {
    "service_name": "bedrock",
    "endpoint": "bedrock-agent-runtime",
    "region": "your_bedrock_model_region_like_us-west-2",
    "api_name": "rerank",
    "model_id": "amazon.rerank-v1:0"
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "POST",
      "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/${parameters.api_name}",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "pre_process_function": "connector.pre_process.bedrock.rerank",
      "post_process_function": "connector.post_process.bedrock.rerank",
      "request_body": """
        {
          "queries": ${parameters.queries},
          "rerankingConfiguration": {
            "bedrockRerankingConfiguration": {
              "modelConfiguration": {
                "modelArn": "arn:aws:bedrock:${parameters.region}::foundation-model/${parameters.model_id}"
              }
            },
            "type": "BEDROCK_RERANKING_MODEL"
          },
          "sources": ${parameters.sources}
        }
      """
    }
  ]
}
```

If you are using Amazon OpenSearch Service, you can provide an AWS Identity and Access Management (IAM) role Amazon Resource Name (ARN) that allows access to Amazon Bedrock. For more information, see the [AWS documentation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html). Use the following request to create a connector:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Rerank API",
  "description": "Test connector for Amazon Bedrock Rerank API",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "roleArn": "your_role_arn_which_allows_access_to_bedrock_agent_runtime_rerank_api"
  },
  "parameters": {
    "service_name": "bedrock",
    "endpoint": "bedrock-agent-runtime",
    "region": "your_bedrock_model_region_like_us-west-2",
    "api_name": "rerank",
    "model_id": "amazon.rerank-v1:0"
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "POST",
      "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/${parameters.api_name}",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "pre_process_function": "connector.pre_process.bedrock.rerank",
      "post_process_function": "connector.post_process.bedrock.rerank",
      "request_body": """
        {
          "queries": ${parameters.queries},
          "rerankingConfiguration": {
            "bedrockRerankingConfiguration": {
              "modelConfiguration": {
                "modelArn": "arn:aws:bedrock:${parameters.region}::foundation-model/${parameters.model_id}"
              }
            },
            "type": "BEDROCK_RERANKING_MODEL"
          },
          "sources": ${parameters.sources}
        }
      """
    }
  ]
}
```

### Step 1.2: Register and deploy the model

Use the connector ID from the response to register and deploy the model:
```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "Amazon Bedrock Rerank API",
  "function_name": "remote",
  "description": "test Amazon Bedrock Rerank API",
  "connector_id": "your_connector_id"
}
```
Note the model ID in the response; you’ll use it in the following steps.

### Step 1.3: Test the model

Test the model by using the Predict API:
```json
POST _plugins/_ml/_predict/text_similarity/your_model_id
{
  "query_text": "What is the capital city of America?",
  "text_docs": [
    "Carson City is the capital city of the American state of Nevada.",
    "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
    "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
    "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
  ]
}
```

Alternatively, you can test the model using the following query. This query bypasses the pre_process_function and calls the Rerank API directly:
```json
POST _plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "queries": [
      {
        "textQuery": {
            "text": "What is the capital city of America?"
        },
        "type": "TEXT"
      }
    ],
    "sources": [
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "Carson City is the capital city of the American state of Nevada."
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        },
        {
            "inlineDocumentSource": {
                "textDocument": {
                    "text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
                },
                "type": "TEXT"
            },
            "type": "INLINE"
        }
    ]
  }
}
```

The connector `pre_process_function` transforms the input into the format required by the Predict API `parameters`.

By default, the Amazon Bedrock Rerank API output is formatted as follows:
```json
[
  {
    "index": 2,
    "relevanceScore": 0.7711548724998493
  },
  {
    "index": 0,
    "relevanceScore": 0.0025114635138098534
  },
  {
    "index": 1,
    "relevanceScore": 2.4876490010363496e-05
  },
  {
    "index": 3,
    "relevanceScore": 6.339210403977635e-06
  }
]
```

The connector `post_process_function` transforms the model’s output into a format that the [Reranker processor](https://opensearch.org/docs/latest/search-plugins/search-pipelines/rerank-processor/) can interpret and orders the results by index.

The response contains four `similarity` outputs. For each similarity output, the `data` array contains a relevance score for each document against the query. The similarity outputs are provided in the order of the input documents; the first `similarity` result pertains to the first document:

```json
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
            0.0025114636
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            2.487649e-05
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.7711549
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            6.3392104e-06
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Step 2: Create a reranking pipeline

To create a reranking pipeline, use the following steps.

### Step 2.1: Ingest test data

Use the following request to ingest data into your index:

```json
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
### Step 2.2: Create a reranking pipeline

Create a reranking pipeline using the Amazon Bedrock reranking model:

```json
PUT /_search/pipeline/rerank_pipeline_bedrock
{
  "description": "Pipeline for reranking with Bedrock rerank model",
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

Note: If you provide multiple field names in `document_fields`, the values of all fields are first concatenated, after which reranking is performed.

### Step 2.3: Test reranking

First, test the query without using the reranking pipeline:

```json
POST my-test-data/_search
{
  "query": {
    "match": {
      "passage_text": "What is the capital city of America?"
    }
  },
  "_source": false,
  "fields": ["passage_text"]
}
```

The first document in the response is `Carson City is the capital city of the American state of Nevada`, which is incorrect:
```json
{
  "took": 2,
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
    "max_score": 2.5045562,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "1",
        "_score": 2.5045562,
        "fields": {
          "passage_text": [
            "Carson City is the capital city of the American state of Nevada."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "2",
        "_score": 0.5807494,
        "fields": {
          "passage_text": [
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "3",
        "_score": 0.5261191,
        "fields": {
          "passage_text": [
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "4",
        "_score": 0.5083029,
        "fields": {
          "passage_text": [
            "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
          ]
        }
      }
    ]
  }
}
```

Next, test the query using the reranking pipeline:

```json
POST my-test-data/_search?search_pipeline=rerank_pipeline_bedrock
{
  "query": {
    "match": {
      "passage_text": "What is the capital city of America?"
    }
  },
  "ext": {
    "rerank": {
      "query_context": {
         "query_text": "What is the capital city of America?"
      }
    }
  },
  "_source": false,
  "fields": ["passage_text"]
}
```

The first document in the response is `"Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."`, which is correct:

```json
{
  "took": 2,
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
    "max_score": 0.7711549,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "3",
        "_score": 0.7711549,
        "fields": {
          "passage_text": [
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "1",
        "_score": 0.0025114636,
        "fields": {
          "passage_text": [
            "Carson City is the capital city of the American state of Nevada."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "2",
        "_score": 02.487649e-05,
        "fields": {
          "passage_text": [
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "4",
        "_score": 6.3392104e-06,
        "fields": {
          "passage_text": [
            "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
          ]
        }
      }
    ]
  },
  "profile": {
    "shards": []
  }
}
```

You can reuse the same query by specifying the query_text_path instead of query_text:

```json
POST my-test-data/_search?search_pipeline=rerank_pipeline_bedrock
{
  "query": {
    "match": {
      "passage_text": "What is the capital city of America?"
    }
  },
  "ext": {
    "rerank": {
      "query_context": {
         "query_text_path": "query.match.passage_text.query"
      }
    }
  },
  "_source": false,
  "fields": ["passage_text"]
}
```

Note: If you don't use score calculated by OpenSearch, you can optimize query latency to use filter context instead. It skips score calculation on OpenSearch side:

```json
POST my-test-data/_search?search_pipeline=rerank_pipeline_bedrock
{
  "query": {
    "bool": {
      "filter": [
        {
          "match": {
            "passage_text": "What is the capital city of America?"
          }
        }
      ]
    }
  },
  "ext": {
    "rerank": {
      "query_context": {
         "query_text_path": "query.bool.filter.0.match.passage_text.query"
      }
    }
  },
  "_source": false,
  "fields": ["passage_text"]
}
```