# Topic

This tutorial is compatible with version 2.19 and later.

This tutorial demonstrates how to optimize vector search by integrating Cohere compression embedding with OpenSearch. Cohere's compression embedding allows for more efficient storage and faster retrieval of vector representations, making it ideal for large-scale search applications.

Bedrock supports compressed embeddings from Cohere Embed now ([blog](https://aws.amazon.com/about-aws/whats-new/2024/06/amazon-bedrock-compressed-embeddings-cohere-embed/)). This tutorial will use Cohere `embed-multilingual-v3` on Bedrock.

We'll use the following OpenSearch features:
- [ML inference ingest processor](https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/) to generate embedding when ingest data; 
- [ML inference search request processor](https://opensearch.org/docs/latest/search-plugins/search-pipelines/ml-inference-search-request/)
- [Search template query](https://opensearch.org/docs/latest/api-reference/search-template/) for searching.
- [k-NN index](https://opensearch.org/docs/latest/search-plugins/knn/index/) and [k-NN byte vector](https://opensearch.org/docs/latest/field-types/supported-field-types/knn-vector/#byte-vectors)

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 1. Create Embedding Model

First, we'll set up a connector to Amazon Bedrock for accessing Cohere's embedding model.


### 1.1 Create Connector
Create a connector by following this [blueprint](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_cohere_cohere.embed-multilingual-v3_blueprint.md).

Note: We don't need  pre/post process function in this tutorial as we are going to use ML inference processor.

`"embedding_types": ["int8"]` specifies that we want 8-bit integer quantized embeddings from the Cohere model.
This means embeddings are compressed from 32-bit floats to 8-bit integers.
It reduces storage space and improves computation speed.
There's a minor trade-off in precision, but it's usually negligible for search tasks.
It's compatible with OpenSearch's KNN index, which supports byte vectors.

You can find more details about model parameters on [Cohere docment](https://docs.cohere.com/v2/docs/embeddings) and [AWS Bedrock document](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html).

```
POST _plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: Cohere embed-multilingual-v3",
  "description": "Test connector for Amazon Bedrock Cohere embed-multilingual-v3",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "your_aws_access_key",
    "secret_key": "your_aws_secret_key",
    "session_token": "your_aws_session_token"
  },
  "parameters": {
    "region": "your_aws_region",
    "service_name": "bedrock",
    "truncate": "END",
    "input_type": "search_document",
    "model": "cohere.embed-multilingual-v3",
    "embedding_types": ["int8"]
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"input_type\": \"${parameters.input_type}\", \"embedding_types\":  ${parameters.embedding_types} }"

    }
  ]
}
```
Sample response
```
{
  "connector_id": "AOP0OZUB3JwAtE25PST0"
}
```

### 1.2 Create Model
Now, let's create a model using the connector we just set up:

Note: The `interface` parameter is optional. If you don't need an interface, you can simply set it as an empty object: `"interface": {}`. For more information about model interfaces, see the [model interface documentation](https://opensearch.org/docs/latest/ml-commons-plugin/api/model-apis/register-model/#the-interface-parameter).

```
POST _plugins/_ml/models/_register?deploy=true
{
  "name": "Bedrock Cohere embed-multilingual-v3",
  "version": "1.0",
  "function_name": "remote",
  "description": "Bedrock Cohere embed-multilingual-v3",
  "connector_id": "AOP0OZUB3JwAtE25PST0",
  "interface": {
    "input": "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"parameters\": {\n            \"type\": \"object\",\n            \"properties\": {\n                \"texts\": {\n                    \"type\": \"array\",\n                    \"items\": {\n                        \"type\": \"string\"\n                    }\n                },\n                \"embedding_types\": {\n                    \"type\": \"array\",\n                    \"items\": {\n                        \"type\": \"string\",\n                        \"enum\": [\"float\", \"int8\", \"uint8\", \"binary\", \"ubinary\"]\n                    }\n                },\n                \"truncate\": {\n                    \"type\": \"array\",\n                    \"items\": {\n                        \"type\": \"string\",\n                        \"enum\": [\"NONE\", \"START\", \"END\"]\n                    }\n                },\n                \"input_type\": {\n                    \"type\": \"string\",\n                    \"enum\": [\"search_document\", \"search_query\", \"classification\", \"clustering\"]\n                }\n            },\n            \"required\": [\"texts\"]\n        }\n    },\n    \"required\": [\"parameters\"]\n}",
    "output": "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"inference_results\": {\n            \"type\": \"array\",\n            \"items\": {\n                \"type\": \"object\",\n                \"properties\": {\n                    \"output\": {\n                        \"type\": \"array\",\n                        \"items\": {\n                            \"type\": \"object\",\n                            \"properties\": {\n                                \"name\": {\n                                    \"type\": \"string\"\n                                },\n                                \"dataAsMap\": {\n                                    \"type\": \"object\",\n                                    \"properties\": {\n                                        \"id\": {\n                                            \"type\": \"string\",\n                                            \"format\": \"uuid\"\n                                        },\n                                        \"texts\": {\n                                            \"type\": \"array\",\n                                            \"items\": {\n                                                \"type\": \"string\"\n                                            }\n                                        },\n                                        \"embeddings\": {\n                                            \"type\": \"object\",\n                                            \"properties\": {\n                                                \"binary\": {\n                                                    \"type\": \"array\",\n                                                    \"items\": {\n                                                        \"type\": \"array\",\n                                                        \"items\": {\n                                                            \"type\": \"number\"\n                                                        }\n                                                    }\n                                                },\n                                                \"float\": {\n                                                    \"type\": \"array\",\n                                                    \"items\": {\n                                                        \"type\": \"array\",\n                                                        \"items\": {\n                                                            \"type\": \"number\"\n                                                        }\n                                                    }\n                                                },\n                                                \"int8\": {\n                                                    \"type\": \"array\",\n                                                    \"items\": {\n                                                        \"type\": \"array\",\n                                                        \"items\": {\n                                                            \"type\": \"number\"\n                                                        }\n                                                    }\n                                                },\n                                                \"ubinary\": {\n                                                    \"type\": \"array\",\n                                                    \"items\": {\n                                                        \"type\": \"array\",\n                                                        \"items\": {\n                                                            \"type\": \"number\"\n                                                        }\n                                                    }\n                                                },\n                                                \"uint8\": {\n                                                    \"type\": \"array\",\n                                                    \"items\": {\n                                                        \"type\": \"array\",\n                                                        \"items\": {\n                                                            \"type\": \"number\"\n                                                        }\n                                                    }\n                                                }\n                                            }\n                                        },\n                                        \"response_type\": {\n                                            \"type\": \"string\"\n                                        }\n                                    },\n                                    \"required\": [\"embeddings\"]\n                                }\n                            },\n                            \"required\": [\"name\", \"dataAsMap\"]\n                        }\n                    },\n                    \"status_code\": {\n                        \"type\": \"integer\"\n                    }\n                },\n                \"required\": [\"output\", \"status_code\"]\n            }\n        }\n    },\n    \"required\": [\"inference_results\"]\n}"
  }
}
```
Sample response
```
{
  "task_id": "COP0OZUB3JwAtE25yiQr",
  "status": "CREATED",
  "model_id": "t64OPpUBX2k07okSZc2n"
}
```

Let's test our model to ensure it's working correctly:

```
POST _plugins/_ml/models/t64OPpUBX2k07okSZc2n/_predict
{
  "parameters": {
    "texts": ["Say this is a test"],
    "embedding_types": [ "int8" ]
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
            "id": "db07a08c-283d-4da5-b0c5-a9a54ef35d01",
            "texts": [
              "Say this is a test"
            ],
            "embeddings": {
              "int8": [
                [
                  -26.0,
                  31.0,
                  ...
                ]
              ]
            },
            "response_type": "embeddings_by_type"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 2. Create Ingest Pipeline

An ingest pipeline allows us to process documents before they are indexed. We'll use it to generate embeddings for the 'title' and 'description' fields of our book data.

We have two options for creating the ingest pipeline:

**Option 1: Invoke model separately with `title` and `description`**
```
PUT _ingest/pipeline/ml_inference_pipeline_cohere
{
  "processors": [
    {
      "ml_inference": {
        "tag": "ml_inference",
        "description": "This processor is going to run ml inference during ingest request",
        "model_id": "t64OPpUBX2k07okSZc2n",
        "input_map": [
          {
            "texts": "$..title"
          },
          {
            "texts": "$..description"
          }
        ],
        "output_map": [
          {
            "title_embedding": "embeddings.int8[0]"
          },
          {
            "description_embedding": "embeddings.int8[0]"
          }
        ],
        "model_config": {
          "embedding_types": ["int8"]
        },
        "ignore_failure": false
      }
    }
  ]
}
```

**Option 2: Invoke model in batch by combining `title` and `description`**

```
PUT _ingest/pipeline/ml_inference_pipeline_cohere
{
  "description": "Concatenate title and description fields",
  "processors": [
    {
      "set": {
        "field": "title_desc_tmp",
        "value": [
          "{{title}}",
          "{{description}}"
        ]
      }
    },
    {
      "ml_inference": {
        "tag": "ml_inference",
        "description": "This processor is going to run ml inference during ingest request",
        "model_id": "t64OPpUBX2k07okSZc2n",
        "input_map": [
          {
            "texts": "$.title_desc_tmp"
          }
        ],
        "output_map": [
          {
            "title_embedding": "embeddings.int8[0]",
            "description_embedding": "embeddings.int8[1]"
          }
        ],
        "model_config": {
          "embedding_types": [
            "int8"
          ]
        },
        "ignore_failure": true
      }
    },
    {
      "remove": {
        "field": "title_desc_tmp"
      }
    }
  ]
}
```
Choose the option that best fits your use case. Option 2 is more efficient as it reduces the number of model invocations.

You can simulate ingest pipeline to debug ([doc](https://opensearch.org/docs/latest/ingest-pipelines/simulate-ingest/)):
```
POST _ingest/pipeline/ml_inference_pipeline_cohere/_simulate
{
  "docs": [
    {
      "_index": "books",
      "_id": "1",
      "_source": {
        "title": "The Great Gatsby",
        "author": "F. Scott Fitzgerald",
        "description": "A novel of decadence and excess in the Jazz Age, exploring themes of wealth, love, and the American Dream.",
        "publication_year": 1925,
        "genre": "Classic Fiction"
      }
    }
  ]
}
```
Sample simulate response
```
{
  "docs": [
    {
      "doc": {
        "_index": "books",
        "_id": "1",
        "_source": {
          "publication_year": 1925,
          "author": "F. Scott Fitzgerald",
          "genre": "Classic Fiction",
          "description": "A novel of decadence and excess in the Jazz Age, exploring themes of wealth, love, and the American Dream.",
          "title": "The Great Gatsby",
          "title_embedding": [
            18,
            33,
            ...
          ],
          "description_embedding": [
            -21,
            -14,
            ...
          ]
        },
        "_ingest": {
          "timestamp": "2025-02-25T09:11:32.192125042Z"
        }
      }
    }
  ]
}
```

## 3. Create KNN Index and Load Test Data

Now, we'll create a KNN index and load some test data into this index.


```
PUT books
{
  "settings": {
    "index": {
      "default_pipeline": "ml_inference_pipeline_cohere",
      "knn": true,
      "knn.algo_param.ef_search": 100
    }
  },
  "mappings": {
    "properties": {
      "title_embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "data_type": "byte",
        "space_type": "l2",
        "method": {
          "name": "hnsw",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 100,
            "m": 16
          }
        }
      },
      "description_embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "data_type": "byte",
        "space_type": "l2",
        "method": {
          "name": "hnsw",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 100,
            "m": 16
          }
        }
      }
    }
  }
}
```

Load test data 
```
POST _bulk
{"index":{"_index":"books"}}
{"title":"The Great Gatsby","author":"F. Scott Fitzgerald","description":"A novel of decadence and excess in the Jazz Age, exploring themes of wealth, love, and the American Dream.","publication_year":1925,"genre":"Classic Fiction"}
{"index":{"_index":"books"}}
{"title":"To Kill a Mockingbird","author":"Harper Lee","description":"A powerful story of racial injustice and loss of innocence in the American South during the Great Depression.","publication_year":1960,"genre":"Literary Fiction"}
{"index":{"_index":"books"}}
{"title":"Pride and Prejudice","author":"Jane Austen","description":"A romantic novel of manners that follows the character development of Elizabeth Bennet as she learns about the repercussions of hasty judgments and comes to appreciate the difference between superficial goodness and actual goodness.","publication_year":1813,"genre":"Romance"}

```

## 4. Perform Neural Search
We'll explore two methods of performing neural search: using a template query with a search pipeline, and rewriting the query in the search pipeline.

### 4.1 Search with Template Query and Search Pipeline

First, create a search pipeline:

```
PUT _search/pipeline/ml_inference_pipeline_cohere_search
{
  "request_processors": [
    {
      "ml_inference": {
        "model_id": "t64OPpUBX2k07okSZc2n",
        "input_map": [
          {
            "texts": "$..ext.ml_inference.text"
          }
        ],
        "output_map": [
          {
            "ext.ml_inference.vector": "embeddings.int8[0]"
          }
        ]
      }
    }
  ]
}
```

Now, perform a search:

```
GET books/_search?search_pipeline=ml_inference_pipeline_cohere_search&verbose_pipeline=false
{
  "query": {
    "template": {
      "knn": {
        "description_embedding": {
          "vector": "${ext.ml_inference.vector}",
          "k": 10
        }
      }
    }
  },
  "ext": {
    "ml_inference": {
      "text": "American Dream"
    }
  },
  "_source": {
    "excludes": [
      "title_embedding", "description_embedding"
    ]
  },
  "size": 2
}
```
Add `&verbose_pipeline=true` to see each search processor's input/ouput, which is usful for debugging. For more details, see [Debugging a search pipeline](https://opensearch.org/docs/latest/search-plugins/search-pipelines/debugging-search-pipeline/).

### 4.2 Search with Rewriting Query in Search Pipeline

Create another search pipeline that rewrites the query:

```
PUT _search/pipeline/ml_inference_pipeline_cohere_search2
{
  "request_processors": [
    {
      "ml_inference": {
        "model_id": "t64OPpUBX2k07okSZc2n",
        "input_map": [
          {
            "texts": "$..match.description.query"
          }
        ],
        "output_map": [
          {
            "query_vector": "embeddings.int8[0]"
          }
        ],
        "model_config": {
          "input_type": "search_query"
        },
        "query_template": """
          {
            "query": {
              "knn": {
                "description_embedding": {
                  "vector": ${query_vector},
                  "k": 10
                }
              }
            },
            "_source": {
              "excludes": [
                "title_embedding",
                "description_embedding"
              ]
            },
            "size": 2
          }
        """
      }
    }
  ]
}
```

Now, perform a search using this pipeline:


```
GET books/_search?search_pipeline=ml_inference_pipeline_cohere_search2
{
  "query": {
    "match": {
      "description": "American Dream"
    }
  }
}
```


Sample response
```
{
  "took": 96,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 3,
      "relation": "eq"
    },
    "max_score": 7.271585e-7,
    "hits": [
      {
        "_index": "books",
        "_id": "U640PJUBX2k07okSEMwy",
        "_score": 7.271585e-7,
        "_source": {
          "publication_year": 1925,
          "author": "F. Scott Fitzgerald",
          "genre": "Classic Fiction",
          "description": "A novel of decadence and excess in the Jazz Age, exploring themes of wealth, love, and the American Dream.",
          "title": "The Great Gatsby"
        }
      },
      {
        "_index": "books",
        "_id": "VK40PJUBX2k07okSEMwy",
        "_score": 6.773544e-7,
        "_source": {
          "publication_year": 1960,
          "author": "Harper Lee",
          "genre": "Literary Fiction",
          "description": "A powerful story of racial injustice and loss of innocence in the American South during the Great Depression.",
          "title": "To Kill a Mockingbird"
        }
      }
    ]
  }
}
```