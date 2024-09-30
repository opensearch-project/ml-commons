# Topic

This tutorial shows how to generate embeddings for arrays of objects in OpenSearch.

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 1. Create embedding model

We will use [Bedrock Titan Embedding model](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html) in this tutorial. 

- If you are using AWS managed OpenSearch service, you can use this [python notebook](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/AIConnectorHelper.ipynb) to create Bedrock Embedding Model easily. Search `1. Create Connector of Bedrock Embedding Model` on the page. 
Or you can manually create connector following this [tutorial](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/semantic_search_with_bedrock_titan_embedding_model.md).

- If you are using self-managed OpenSearch, you can follow this [blueprint](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/remote_inference_blueprints/bedrock_connector_titan_embedding_blueprint.md). 

Use the model ID from the response to test predict API:
```
POST /_plugins/_ml/models/your_embedding_model_id/_predict
{
    "parameters": {
        "inputText": "hello world"
    }
}
```
Sample response:

```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [ 1536 ],
          "data": [0.7265625, -0.0703125, 0.34765625, ...]
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 2. Create ingest pipeline

### 2.1 Create test index
```
PUT my_books
{
  "settings" : {
      "index.knn" : "true",
      "default_pipeline": "bedrock_embedding_foreach_pipeline"
  },
  "mappings": {
    "properties": {
      "books": {
        "type": "nested",
        "properties": {
          "title_embedding": {
            "type": "knn_vector",
            "dimension": 1536
          },
          "title": {
            "type": "text"
          },
          "description": {
            "type": "text"
          }
        }
      }
    }
  }
}
```

### 2.2 Create ingest pipeline

Create sub-pipeline to generate embedding for one item in the array.

This pipeline contains 3 processors
- set processor: The `text_embedding` processor is unable to identify "_ingest._value.title". You need to copy "_ingest._value.title" to a non-existing temporary field for text_embedding to process it.
- text_embedding processor: convert value of the temporary field to embedding
- remove processor: remove temporary field
```
PUT _ingest/pipeline/bedrock_embedding_pipeline
{
  "processors": [
    {
      "set": {
        "field": "title_tmp",
        "value": "{{_ingest._value.title}}"
      }
    },
    {
      "text_embedding": {
        "model_id": your_embedding_model_id,
        "field_map": {
          "title_tmp": "_ingest._value.title_embedding"
        }
      }
    },
    {
      "remove": {
        "field": "title_tmp"
      }
    }
  ]
}
```

Create pipeline with foreach processor:
```
PUT _ingest/pipeline/bedrock_embedding_foreach_pipeline
{
  "description": "Test nested embeddings",
  "processors": [
    {
      "foreach": {
        "field": "books",
        "processor": {
          "pipeline": {
            "name": "bedrock_embedding_pipeline"
          }
        },
        "ignore_failure": true
      }
    }
  ]
}
```

### 2.3 Simulate pipeline

- Case1: two book objects with title
```
POST _ingest/pipeline/bedrock_embedding_foreach_pipeline/_simulate
{
  "docs": [
    {
      "_index": "my_books",
      "_id": "1",
      "_source": {
        "books": [
          {
            "title": "first book",
            "description": "This is first book"
          },
          {
            "title": "second book",
            "description": "This is second book"
          }
        ]
      }
    }
  ]
}
```
Response
```
{
  "docs": [
    {
      "doc": {
        "_index": "my_books",
        "_id": "1",
        "_source": {
          "books": [
            {
              "title": "first book",
              "title_embedding": [-1.1015625, 0.65234375, 0.7578125, ...],
              "description": "This is first book"
            },
            {
              "title": "second book",
              "title_embedding": [-0.65234375, 0.21679688, 0.7265625, ...],
              "description": "This is second book"
            }
          ]
        },
        "_ingest": {
          "_value": null,
          "timestamp": "2024-05-28T16:16:50.538929413Z"
        }
      }
    }
  ]
}
```
- Case2: book object without title
```
POST _ingest/pipeline/bedrock_embedding_foreach_pipeline/_simulate
{
  "docs": [
    {
      "_index": "my_books",
      "_id": "1",
      "_source": {
        "books": [
          {
            "title": "first book",
            "description": "This is first book"
          },
          {
            "description": "This is second book"
          }
        ]
      }
    }
  ]
}
```
Response
```
{
  "docs": [
    {
      "doc": {
        "_index": "my_books",
        "_id": "1",
        "_source": {
          "books": [
            {
              "title": "first book",
              "title_embedding": [-1.1015625, 0.65234375, 0.7578125, ...],
              "description": "This is first book"
            },
            {
              "description": "This is second book"
            }
          ]
        },
        "_ingest": {
          "_value": null,
          "timestamp": "2024-05-28T16:19:03.942644042Z"
        }
      }
    }
  ]
}
```
### 2.4 Test ingest data
Ingest one doc
```
PUT my_books/_doc/1
{
  "books": [
    {
      "title": "first book",
      "description": "This is first book"
    },
    {
      "title": "second book",
      "description": "This is second book"
    }
  ]
}
```
Get document
```
GET my_books/_doc/1
```
Response
```
{
  "_index": "my_books",
  "_id": "1",
  "_version": 1,
  "_seq_no": 0,
  "_primary_term": 1,
  "found": true,
  "_source": {
    "books": [
      {
        "description": "This is first book",
        "title": "first book",
        "title_embedding": [-1.1015625, 0.65234375, 0.7578125, ...]
      },
      {
        "description": "This is second book",
        "title": "second book",
        "title_embedding": [-0.65234375, 0.21679688, 0.7265625, ...]
      }
    ]
  }
}      
```
Bulk ingestion
```
POST _bulk
{ "index" : { "_index" : "my_books" } }
{ "books" : [{"title": "first book", "description": "This is first book"}, {"title": "second book", "description": "This is second book"}] }
{ "index" : { "_index" : "my_books" } }
{ "books" : [{"title": "third book", "description": "This is third book"}, {"description": "This is fourth book"}] }

```