### Cohere Embedding Connector Blueprint:

This blueprint will show you how to connect a Cohere multi-modal embedding model to your OpenSearch cluster, including creating a k-nn index and your own Embedding pipeline. You will require a Cohere API key to create a connector.

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
    "input_type":"image",
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
      "request_body": "{ \"images\": ${parameters.images}, \"truncate\": \"${parameters.truncate}\", \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }",
      "pre_process_function": "connector.pre_process.cohere.multimodal_embedding",
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
    "images": ["data:image/png;base64,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"]
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
  "passage_text": "data:image/png;base64,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",
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
