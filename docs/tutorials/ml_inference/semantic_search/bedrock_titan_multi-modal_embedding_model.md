# Topic

This tutorial is compatible with version 2.17 and later.

This tutorial shows how to build multi-modal semantic search  [ML inference ingest processor](https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/) and [ML inference search request processor](https://opensearch.org/docs/latest/search-plugins/search-pipelines/ml-inference-search-request/).
By using ML inference processors in OpenSearch, you can effectively handle multimodal queries (text, image, or both) and leverage KNN search for semantic search tasks. This approach allows you to retrieve the most relevant documents based on both textual and visual content, enabling powerful search capabilities for rich, multimodal data.


## 1. Create embedding model
We will use [Bedrock Titan Multimodal Embedding model](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-multiemb-models.html) in this tutorial.
Note: As we are using [ML inference processor](https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/) in this tutorial, we don't need to specify pre/post process function in connector.

- Create connector

```
POST _plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Connector: bedrock Titan multi-modal embedding model",
    "description": "Test connector for Amazon Bedrockbedrock Titan multi-modal embedding model",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "{{access_key}}",
        "secret_key": "{{secret_key}}",
        "session_token": "{{session_token}}"
    },
    "parameters": {
    "region": "us-east-1",
    "service_name": "bedrock",
    "model": "amazon.titan-embed-image-v1",
    "input_docs_processed_step_size": 2
    },
    "actions": [
               {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\"inputText\": \"${parameters.inputText:-null}\", \"inputImage\": \"${parameters.inputImage:-null}\"}"
        }
    ]
}
```
Sample response
```
{
  "connector_id": "P8c_JZUB7judm8f4591w"
}
```

- Create model
```
POST _plugins/_ml/models/_register?deploy=true
{
    "name": "amazon.titan-embed-image-v1",
    "version": "1.0",
    "function_name": "remote",
    "description": "test amazon.titan-embed-image-v1",
    "connector_id": "P8c_JZUB7judm8f4591w",
    "interface": {
        "input": "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"parameters\": {\n            \"type\": \"object\",\n            \"properties\": {\n                \"inputText\": {\n                    \"type\": \"string\"\n                },\n                \"inputImage\": {\n                    \"type\": \"string\"\n                }\n            }\n        }\n    }\n}",
        "output": "{\n    \"inference_results\": [\n        {\n            \"output\": [\n                {\n                    \"name\": {\n                        \"type\": \"string\"\n                    },\n                    \"dataAsMap\": {\n                        \"type\": \"object\",\n                        \"properties\": {\n                            \"embedding\": {\n                                \"type\": \"array\",\n                                \"items\": {\n                                    \"type\": \"number\"\n                                }\n                            },\n                            \"inputTextTokenCount\": {\n                                \"type\": \"number\"\n                            }\n                        }\n                    }\n                }\n            ],\n            \"status_code\": {\n                \"type\": \"integer\"\n            }\n        }\n    ]\n}"
    }
}
```
Sample response
```
{
    "task_id": "nMdJJZUB7judm8f4HN29",
    "status": "CREATED",
    "model_id": "ncdJJZUB7judm8f4HN3P"
}
```

- Test predict
Get Image and Text Embedding. Note that Titan multi-modal embedding model required the model input, inputImage in String Base64 format. 

```
POST /_plugins/_ml/models/ncdJJZUB7judm8f4HN3P/_predict
{
    "parameters": {
        "inputText": "Say this is a test",
        "inputImage": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIB..."
}
```
Sample response
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "embedding": [
              0.017202578,,...
            ],
            "inputTextTokenCount": 7.0
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

Get Text Embedding.

```
POST /_plugins/_ml/models/ncdJJZUB7judm8f4HN3P/_predict
{
    "parameters": {
        "inputText": "Say this is a test"
    }
}
```

Sample response
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "embedding": [
                0.022338867,...
            ],
            "inputTextTokenCount": 7.0
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```


Get Image Embedding.

```
POST /_plugins/_ml/models/ncdJJZUB7judm8f4HN3P/_predict
{
    "parameters": {
        "inputImage": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIBwcHBw..."
    }
}
```

Sample response
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "embedding": [
                0.012066291,,...
            ],
            "inputTextTokenCount": 7.0
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 2. Create Ingest Pipeline
In the ingest pipeline, `model_id` is the required field, used the model id created in step 1.
In the input mapping, mapping the document field, `name` to be model input field, `inputText`, mapping the document field, `image` to be model input field, `inputImage`.
In the output mapping,  in the input mapping, mapping the model output field, `embedding` to be new document field, `multimodal_embeddings`.
For multi-modal model, both inputText and inputImage are optional model input fields, set ignoreMissing and ignoreFailure to true. When a field is missing from the document, the ingest process continue. 

```
PUT _ingest/pipeline/ml_inference_pipeline_multi_modal
{
  "processors": [
    {
      "ml_inference": {
        "tag": "ml_inference",
        "description": "This processor is going to run ml inference during search request",
        "model_id": "ncdJJZUB7judm8f4HN3P",
        "input_map": [
          {
            "inputText": "name",
            "inputImage":"image"
          }
        ],
        "output_map": [
          {
            "multimodal_embeddings": "embedding"
          }
        ],
        "ignore_missing":true,
        "ignore_failure": true
        
      }
    }
  ]
}

```

## 3. Load Test data 
Create KNN index
```
PUT test-index-area
{
  "settings": {
    "index": {
      "default_pipeline": "ml_inference_pipeline_multi_modal",
      "knn": true,
      "knn.algo_param.ef_search": 100
    }
  },
  "mappings": {
    "properties": {
      "multimodal_embeddings": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": {
          "name": "hnsw",
          "space_type": "l2",
          "engine": "nmslib",
          "parameters": {
            "ef_construction": 128,
            "m": 24
          }
        }
      }
    }
  }
}
```

Load a example doc with text only.

```
PUT test-index-area/_doc/1
{
  "name": "Central Park",
  "category": "Park"
}
```
Check doc/1

```
GET test-index-area/_doc/1
```
Sample Response
```
{
    "_index": "test-index-area",
    "_id": "1",
    "_version": 4,
    "_seq_no": 3,
    "_primary_term": 1,
    "found": true,
    "_source": {
        "multimodal_embeddings": [
            0.01171875,...],
      "name": "Central Park",
      "category": "Park"
    }
}
```

Load a example doc with text + image.

```
PUT test-index-area/_doc/2
{
  "name": "Time Sqaure",
  "category": "Sqaure",
  "image":"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"
}
```
Check doc/2

```
GET test-index-area/_doc/2
```
Sample Response
```
{
    "_index": "test-index-area",
    "_id": "2",
    "_version": 1,
    "_seq_no": 4,
    "_primary_term": 1,
    "found": true,
    "_source": {
        "image": "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",
        "multimodal_embeddings": [
            0.016407186,...],
        "name": "Time Sqaure",
        "category": "Sqaure"
    }
}
```
Load a example doc no text nor image.

```
PUT test-index-area/_doc/3
{
   "category": "Bridge"
}
```
Check doc/3, when no matching input mapping is provided, the processor is skipped and still ingest the document. 

```
GET test-index-area/_doc/3
```
Sample Response
```
{
    "_index": "test-index-area",
    "_id": "3",
    "_version": 1,
    "result": "created",
    "_shards": {
        "total": 2,
        "successful": 2,
        "failed": 0
    },
    "_seq_no": 5,
    "_primary_term": 1
}
```

For more documents, you can also use bulk api, 

```
POST _bulk
{ "index": { "_index": "test-index-area", "_id": "1" } }
{ "name": "Central Park", "category": "Park" }
{ "index": { "_index": "test-index-area", "_id": "2" } }
{ "name": "Time Square", "category": "Square", "image": "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" }
{ "index": { "_index": "test-index-area", "_id": "3" } }
{ "category": "Bridge" }
```
Search the index, can see the document contains chunks and embedding generated.
```
GET test-index-area/_search
```

## 4. Search with ML inference processor
Create a search pipeline with a ML inference search request processor to rewrite the query to a KNN query during search time.

The pipeline consists of an ML Inference processor, which takes optional model input fields mapping `ext.ml_inference.text` and `ext.ml_inference.image` in the search request to the `inputText` and `InputImage` fields into model input .

The processor outputs the `embedding` field from the model response as the `multimodal_embeddings` variable. It then runs a KNN query defined in the query_template using the `multimodal_embeddings` variable.


```
PUT _search/pipeline/multimodal_semantic_search_pipeline
{
  "request_processors": [
    {
      "ml_inference": {
        "tag": "ml_inference",
        "description": "This processor is to run knn query",
        "model_id": "ncdJJZUB7judm8f4HN3P",
        "query_template": "{\"size\": 2,\"query\": {\"knn\": {\"multimodal_embeddings\": {\"vector\": ${multimodal_embeddings},\"k\": 3}}}}",
         "optional_input_map": [
          {
            "inputText": "ext.ml_inference.text",
            "inputImage": "ext.ml_inference.image"
          } 
        ],
        "output_map": [
          {
            "multimodal_embeddings": "embedding"
          }
        ],
        "model_config":{},
        "ignore_missing":false,
        "ignore_failure": false
      }
      
    }
  ]
}
```

Search with text using search pipeline.
```
GET opensearch_docs/_search?search_pipeline=multimodal_semantic_search_pipeline
{
    "query": {
        "match_all": {}
    },
    "ext": {
        "ml_inference": {
            "text": "Central Park"
        }
    }
}
```

Sample Response:

``` 
{
    "took": 127,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 2,
            "relation": "eq"
        },
        "max_score": 1.0,
        "hits": [
            {
                "_index": "test-index-area",
                "_id": "1",
                "_score": 1.0,
                "_source": {
                    "multimodal_embeddings": [
                        0.01171875, ],
                    "name": "Central Park",
                    "category": "Park"
                }
            },
            {
                "_index": "test-index-area",
                "_id": "2",
                "_score": 0.58686763,
                "_source": {
                    "image": "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",
                    "multimodal_embeddings": [
                        0.016407186,],
                    "name": "Time Sqaure",
                    "category": "Sqaure"
                }
            }
        ]
    }
}
```

Search with image using search pipeline.
```
GET opensearch_docs/_search?search_pipeline=multimodal_semantic_search_pipeline
{
    "query": {
        "match_all": {}
    },
    "ext": {
        "ml_inference": {
            "image": "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"
        }
    }
}
```
Sample Response:

```
{
    "took": 175,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 2,
            "relation": "eq"
        },
        "max_score": 0.7822725,
        "hits": [
            {
                "_index": "test-index-area",
                "_id": "2",
                "_score": 0.7822725,
                "_source": {
                    "image": "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",
                    "multimodal_embeddings": [
                      0.016407186,...],
                    "name": "Time Sqaure",
                    "category": "Sqaure"
                }
            },
            {
                "_index": "test-index-area",
                "_id": "1",
                "_score": 0.42675978,
                "_source": {
                    "multimodal_embeddings": [
                       0.01171875,..],
                    "name": "Central Park",
                    "category": "Park"
                }
            }
        ]
    }
}

```

Search with text and image using search pipeline.
```
GET opensearch_docs/_search?search_pipeline=multimodal_semantic_search_pipeline
{
    "query": {
        "match_all": {}
    },
    "ext": {
        "ml_inference": {
            "text":"any place in new york",
            "image": "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"
        }
    }
}
```
Sample Response:

```
{
    "took": 109,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 2,
            "relation": "eq"
        },
        "max_score": 0.58110446,
        "hits": [
            {
                "_index": "test-index-area",
                "_id": "2",
                "_score": 0.58110446,
                "_source": {
                    "image": "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",
                    "multimodal_embeddings": [
                        0.016407186,...],
                    "name": "Time Sqaure",
                    "category": "Sqaure"
                }
            },
            {
                "_index": "test-index-area",
                "_id": "1",
                "_score": 0.57516855,
                "_source": {
                    "multimodal_embeddings": [
                      0.01171875, ...],
                    "name": "Central Park",
                    "category": "Park"
                }
            }
        ]
    }
}
```