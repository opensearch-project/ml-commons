# Topic

> This tutorial doesn't explain details about byte-quantized vectors. Read this [Byte-quantized vectors in OpenSearch](https://opensearch.org/blog/byte-quantized-vectors-in-opensearch/) to learn more details.

This tutorial explains how to build semantic search with [Cohere Embedding model](https://docs.cohere.com/reference/embed) and byte-quantized vectors in OpenSearch.

Cohere embedding model v3 supports more `embedding_types`, this tutorial uses `"int8"` to work with byte-quantized vectors.

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

Cohere embedding model v3 supports different `input_type`. From Cohere [doc](https://docs.cohere.com/reference/embed)
> - `"search_document"`: Used for embeddings stored in a vector database for search use-cases.
> - `"search_query"`: Used for embeddings of search queries run against a vector DB to find relevant documents.

We will create two models in this tutorial:
- one model with `search_document` input type for ingestion
- one model with `search_query` input type for search

## 1. Create embedding model for ingestion

Create connector with `search_document` input type

```
POST /_plugins/_ml/connectors/_create
{
    "name": "Cohere embedding connector with int8 embedding type for ingestion",
    "description": "Test connector for Cohere embedding model",
    "version": 1,
    "protocol": "http",
    "credential": {
        "cohere_key": "your_cohere_api_key"
    },
    "parameters": {
        "model": "embed-english-v3.0",
        "embedding_types": ["int8"],
        "input_type": "search_document"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "Authorization": "Bearer ${credential.cohere_key}",
                "Request-Source": "unspecified:opensearch"
            },
            "url": "https://api.cohere.ai/v1/embed",
            "request_body": "{ \"model\": \"${parameters.model}\", \"texts\": ${parameters.texts}, \"input_type\":\"${parameters.input_type}\", \"embedding_types\": ${parameters.embedding_types} }",
            "pre_process_function": "connector.pre_process.cohere.embedding",
            "post_process_function": "\n    def name = \"sentence_embedding\";\n    def data_type = \"FLOAT32\";\n    def result;\n    if (params.embeddings.int8 != null) {\n      data_type = \"INT8\";\n      result = params.embeddings.int8;\n    } else if (params.embeddings.uint8 != null) {\n      data_type = \"UINT8\";\n      result = params.embeddings.uint8;\n    } else if (params.embeddings.float != null) {\n      data_type = \"FLOAT32\";\n      result = params.embeddings.float;\n    }\n    \n    if (result == null) {\n      return \"Invalid embedding result\";\n    }\n    \n    def embedding_list = new StringBuilder(\"[\");\n    \n    for (int m=0; m<result.length; m++) {\n      def embedding_size = result[m].length;\n      def embedding = new StringBuilder(\"[\");\n      def shape = [embedding_size];\n      for (int i=0; i<embedding_size; i++) {\n        def val;\n        if (\"FLOAT32\".equals(data_type)) {\n          val = result[m][i].floatValue();\n        } else if (\"INT8\".equals(data_type) || \"UINT8\".equals(data_type)) {\n          val = result[m][i].intValue();\n        }\n        embedding.append(val);\n        if (i < embedding_size - 1) {\n          embedding.append(\",\");  \n        }\n      }\n      embedding.append(\"]\");  \n      \n      // workaround for compatible with neural-search\n      def dummy_data_type = 'FLOAT32';\n      \n      def json = '{' +\n                   '\"name\":\"' + name + '\",' +\n                   '\"data_type\":\"' + dummy_data_type + '\",' +\n                   '\"shape\":' + shape + ',' +\n                   '\"data\":' + embedding +\n                   '}';\n      embedding_list.append(json);\n      if (m < result.length - 1) {\n        embedding_list.append(\",\");  \n      }\n    }\n    embedding_list.append(\"]\");  \n    return embedding_list.toString();\n    "
        }
    ]
}
```
Use connector id from the response to create model
```
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Cohere embedding model for INT8 with search_document input type",
    "function_name": "remote",
    "description": "test model",
    "connector_id": "your_connector_id"
}
```
Note the model id, it will be used in step 2.1

Test model
```
POST /_plugins/_ml/models/your_embedding_model_id/_predict
{
    "parameters": {
        "texts": ["hello", "goodbye"]
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
                    "name": "sentence_embedding",
                    "data_type": "FLOAT32",
                    "shape": [
                        1024
                    ],
                    "data": [
                        20,
                        -11,
                        -60,
                        -91,
                        ...
                    ]
                },
                {
                    "name": "sentence_embedding",
                    "data_type": "FLOAT32",
                    "shape": [
                        1024
                    ],
                    "data": [
                        58,
                        -30,
                        9,
                        -51,
                        ...
                    ]
                }
            ],
            "status_code": 200
        }
    ]
}
```

## 2. Ingest data

### 2.1 Create ingest pipeline

```
PUT /_ingest/pipeline/pipeline-cohere
{
  "description": "Cohere embedding ingest pipeline",
  "processors": [
    {
      "text_embedding": {
        "model_id": "your_embedding_model_id_created_in_step1",
        "field_map": {
          "passage_text": "passage_embedding"
        }
      }
    }
  ]
}
```

### 2.2 Create KNN index with byte-quantized vector
Refer to https://opensearch.org/blog/byte-quantized-vectors-in-opensearch/

```
PUT my_test_data
{
  "settings": {
    "index": {
      "knn": true,
      "knn.algo_param.ef_search": 100,
      "default_pipeline": "pipeline-cohere"
    }
  },
  "mappings": {
    "properties": {
      "passage_text": {
        "type": "text"
      },
      "passage_embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "data_type": "byte",
        "method": {
          "name": "hnsw",
          "space_type": "l2",
          "engine": "lucene",
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

Ingest test data

```
POST _bulk
{ "index" : { "_index" : "my_test_data" } }
{ "passage_text" : "OpenSearch is the flexible, scalable, open-source way to build solutions for data-intensive applications. Explore, enrich, and visualize your data with built-in performance, developer-friendly tools, and powerful integrations for machine learning, data processing, and more." }
{ "index" : { "_index" : "my_test_data"} }
{ "passage_text" : "BM25 is a keyword-based algorithm that performs well on queries containing keywords but fails to capture the semantic meaning of the query terms. Semantic search, unlike keyword-based search, takes into account the meaning of the query in the search context. Thus, semantic search performs well when a query requires natural language understanding." }

```

## 3. Semantic search

Let's create another embedding model with `search_query` input type
```
POST /_plugins/_ml/connectors/_create
{
    "name": "Cohere embedding connector with int8 embedding type for search",
    "description": "Test connector for Cohere embedding model. Use this connector for search.",
    "version": 1,
    "protocol": "http",
    "credential": {
        "cohere_key": "your_cohere_api_key"
    },
    "parameters": {
        "model": "embed-english-v3.0",
        "embedding_types": ["int8"],
        "input_type": "search_query"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "Authorization": "Bearer ${credential.cohere_key}",
                "Request-Source": "unspecified:opensearch"
            },
            "url": "https://api.cohere.ai/v1/embed",
            "request_body": "{ \"model\": \"${parameters.model}\", \"texts\": ${parameters.texts}, \"input_type\":\"${parameters.input_type}\", \"embedding_types\": ${parameters.embedding_types} }",
            "pre_process_function": "connector.pre_process.cohere.embedding",
            "post_process_function": "\n    def name = \"sentence_embedding\";\n    def data_type = \"FLOAT32\";\n    def result;\n    if (params.embeddings.int8 != null) {\n      data_type = \"INT8\";\n      result = params.embeddings.int8;\n    } else if (params.embeddings.uint8 != null) {\n      data_type = \"UINT8\";\n      result = params.embeddings.uint8;\n    } else if (params.embeddings.float != null) {\n      data_type = \"FLOAT32\";\n      result = params.embeddings.float;\n    }\n    \n    if (result == null) {\n      return \"Invalid embedding result\";\n    }\n    \n    def embedding_list = new StringBuilder(\"[\");\n    \n    for (int m=0; m<result.length; m++) {\n      def embedding_size = result[m].length;\n      def embedding = new StringBuilder(\"[\");\n      def shape = [embedding_size];\n      for (int i=0; i<embedding_size; i++) {\n        def val;\n        if (\"FLOAT32\".equals(data_type)) {\n          val = result[m][i].floatValue();\n        } else if (\"INT8\".equals(data_type) || \"UINT8\".equals(data_type)) {\n          val = result[m][i].intValue();\n        }\n        embedding.append(val);\n        if (i < embedding_size - 1) {\n          embedding.append(\",\");  \n        }\n      }\n      embedding.append(\"]\");  \n      \n      // workaround for compatible with neural-search\n      def dummy_data_type = 'FLOAT32';\n      \n      def json = '{' +\n                   '\"name\":\"' + name + '\",' +\n                   '\"data_type\":\"' + dummy_data_type + '\",' +\n                   '\"shape\":' + shape + ',' +\n                   '\"data\":' + embedding +\n                   '}';\n      embedding_list.append(json);\n      if (m < result.length - 1) {\n        embedding_list.append(\",\");  \n      }\n    }\n    embedding_list.append(\"]\");  \n    return embedding_list.toString();\n    "
        }
    ]
}
```
Use the connector id from response to create model
```
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Cohere embedding model for INT8 with search_document input type",
    "function_name": "remote",
    "description": "test model",
    "connector_id": "your_connector_id"
}
```
Then you can use the model id from response to run neural search query:
```
POST /my_test_data/_search
{
  "query": {
    "neural": {
      "passage_embedding": {
        "query_text": "semantic search",
        "model_id": "your_embedding_model_id",
        "k": 100
      }
    }
  },
  "size": "1",
  "_source": ["passage_text"]
}
```
Sample response
```
{
  "took": 143,
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
    "max_score": 9.345969e-7,
    "hits": [
      {
        "_index": "my_test_data",
        "_id": "_IXCuY0BJr_OiKWden7i",
        "_score": 9.345969e-7,
        "_source": {
          "passage_text": "BM25 is a keyword-based algorithm that performs well on queries containing keywords but fails to capture the semantic meaning of the query terms. Semantic search, unlike keyword-based search, takes into account the meaning of the query in the search context. Thus, semantic search performs well when a query requires natural language understanding."
        }
      }
    ]
  }
}
```