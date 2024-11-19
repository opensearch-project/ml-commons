### Cohere Embedding Connector Blueprint:

This blueprint will show you how to connect a Cohere embedding model to your OpenSearch cluster, including creating a k-nn index and your own Embedding pipeline. You will require a Cohere API key to create a connector.

Cohere currently offers the following Embedding models (with model name and embedding dimensions). Note that only the following have been tested with the blueprint guide.

- embed-english-v3.0 1024
- embed-english-v2.0 4096

See [Cohere's /embed API docs](https://docs.cohere.com/reference/embed) for more details.

#### 1. Create a connector and model group

##### 1a. Register model group

```json
POST /_plugins/_ml/model_groups/_register

{
  "name": "cohere_model_group",
  "description": "Your Cohere model group"
}
```

This request response will return the `model_group_id`, note it down.

##### 1b. Create a connector

See above for all the values the `parameters > model` parameter can take.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Cohere Embed Model",
  "description": "The connector to Cohere's public embed API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "cohere_key": "<ENTER_COHERE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>", // Choose a Model from the provided list above
    "input_type":"search_document",
    "truncate": "END"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.ai/v1/embed",
      "headers": {
        "Authorization": "Bearer ${credential.cohere_key}",
        "Request-Source": "unspecified:opensearch"
      },
      "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }",
      "pre_process_function": "connector.pre_process.cohere.embedding",
      "post_process_function": "connector.post_process.cohere.embedding"
    }
  ]
}
```

This request response will return the `connector_id`, note it down.

##### 1c. Register a model with your connector

You can now register your model with the `model_group_id` and `connector_id` created from the previous steps.

```json
POST /_plugins/_ml/models/_register
Content-Type: application/json

{
    "name": "Cohere Embed Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your Cohere Embedding Model",
    "connector_id": "<CONNECTOR_ID>"
}
```

This will create a registration task, the response should look like:

```json
{
  "task_id": "9bXpRY0BRil1qhQaUK-u",
  "status": "CREATED",
  "model_id": "9rXpRY0BRil1qhQaUK_8"
}
```

##### 1d. Deploy model

The last step is to deploy your model. Use the `model_id` returned by the registration request, and run:

```json
POST /_plugins/_ml/models/<MODEL_ID>/_deploy
```

This will once again spawn a task to deploy your model, with a response that will look like:

```json
{
  "task_id": "97XrRY0BRil1qhQaQK_c",
  "task_type": "DEPLOY_MODEL",
  "status": "COMPLETED"
}
```

You can run the GET tasks request again to verify the status.

```json
GET /_plugins/_ml/tasks/<TASK_ID>
```

Once this is complete, your model is deployed and ready!

##### 1e. Test model

You can try this request to test that the model behaves correctly:

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "texts": ["Say this is a test"]
  }
}
```

It should return a response similar to this:

```json
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
            -0.0024547577,
            0.0062217712,
            -0.01675415,
            -0.020736694,
            -0.020263672,
            ... ...
            0.038635254
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

#### (Optional) 2. Setup k-NN index and ingestion pipeline

##### 2a. Create your pipeline

It is important that the `field_map` parameter contains all the document fields you'd like to embed as a vector. The key value is the document field name, and the value will be the field containing the embedding.

```json
PUT /_ingest/pipeline/cohere-ingest-pipeline
{
  "description": "Test Cohere Embedding pipeline",
  "processors": [
    {
      "text_embedding": {
        "model_id": "<MODEL_ID>",
        "field_map": {
          "passage_text": "passage_embedding"
        }
      }
    }
  ]
}
```

Sample response:

```json
{
  "acknowledged": true
}
```

##### 2b. Create a k-NN index

Here `cohere-nlp-index` is the name of your index, you can change it as needed.

````json
PUT /cohere-nlp-index

{
  "settings": {
    "index.knn": true,
    "default_pipeline": "cohere-ingest-pipeline"
  },
  "mappings": {
    "properties": {
      "id": {
      "type": "text"
      },
      "passage_embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": {
          "engine": "lucene",
          "space_type": "l2",
          "name": "hnsw",
          "parameters": {}
        }
      },
      "passage_text": {
        "type": "text"
      }
    }
  }
}

Sample response:

```json
{
  "acknowledged": true,
  "shards_acknowledged": true,
  "index": "cohere-nlp-index"
}
````

##### 2c. Testing the index and pipeline

First, you can insert a record:

```json
PUT /cohere-nlp-index/_doc/1
{
  "passage_text": "Hi - Cohere Embeddings are cool!",
  "id": "c1"
}
```

Sample response:

```json
{
  "_index": "cohere-nlp-index",
  "_id": "1",
  "_version": 1,
  "result": "created",
  "_shards": {
    "total": 2,
    "successful": 1,
    "failed": 0
  },
  "_seq_no": 0,
  "_primary_term": 1
}
```

The last step is to check that the embeddings were properly created. Notice that the embedding field created corresponds to the `field_map` mapping you defined in step 3a.

```json
GET /cohere-nlp-index/\_search

{
  "query": {
    "match_all": {}
  }
}
```

Sample response:

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
      "value": 1,
      "relation": "eq"
    },
    "max_score": 1,
    "hits": [
      {
        "_index": "cohere-nlp-index",
        "_id": "1",
        "_score": 1,
        "_source": {
          "passage_text": "Hi - Cohere Embeddings are cool!",
          "passage_embedding": [
            0.02494812,
            -0.009391785,
            -0.015716553,
            -0.051849365,
            -0.015930176,
            -0.024734497,
            -0.028518677,
            -0.008323669,
            -0.008323669,
            .............

          ],
          "id": "c1"
        }
      }
    ]
  }
}
```

Congratulations! You've successfully created your ingestion pipeline.
