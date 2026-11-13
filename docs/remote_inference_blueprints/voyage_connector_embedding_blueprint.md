### Voyage AI Embedding Connector Blueprint:

This blueprint shows how to connect a [Voyage AI](https://docs.voyageai.com/docs/embeddings) text embedding model to your OpenSearch cluster, including creating a k-NN index and an ingest pipeline. You need a Voyage AI API key to create the connector.

This is the legacy blueprint, which uses the built-in `connector.pre_process.voyage.embedding` and `connector.post_process.voyage.embedding` functions so that the model works with the `text_embedding` ingest processor. If you use the ML inference processor, see the [standard blueprint](standard_blueprints/voyage_connector_text_embedding_standard_blueprint.md).

Voyage AI offers the following text embedding models. The model is always required: the blueprint has no default, so set `parameters.model` explicitly.

| Model | Default dimension | Other dimensions (`output_dimension`) | Context length |
|---|---|---|---|
| voyage-4-large, voyage-4, voyage-4-lite, voyage-code-4 | 1024 | 2048, 512, 256 | 32K tokens |
| voyage-3-large, voyage-3.5, voyage-3.5-lite, voyage-code-3 | 1024 | 2048, 512, 256 | 32K tokens |
| voyage-finance-2 | 1024 | none | 32K tokens |
| voyage-law-2 | 1024 | none | 16K tokens |

See [Voyage AI's /v1/embeddings API docs](https://docs.voyageai.com/reference/embeddings-api) for more details.

#### Request limits

A single `/v1/embeddings` request accepts at most 1000 inputs. The total number of tokens in one request is also capped: 1M for `voyage-4-lite` and `voyage-3.5-lite`, 320K for `voyage-4` and `voyage-3.5`, and 120K for `voyage-4-large`, `voyage-3-large`, `voyage-code-3`, `voyage-finance-2` and `voyage-law-2`. Voyage documents no cap for `voyage-code-4`.

When the inputs arrive as text documents (the `text_embedding` processor, or `text_docs` in the `_predict/TEXT_EMBEDDING` API), ML Commons splits them into requests by count only, using the `input_docs_processed_step_size` connector parameter (the blueprint sets it to 128). It does not count tokens, and the results come back in the order of the inputs. Lower the step size if your documents are long enough that 128 of them can exceed the token cap of your model; the Voyage API rejects a request over the cap with HTTP 400. If you send `parameters.input` directly to `_predict`, the whole list goes in one request, so keep it under the limits yourself.

#### 1. Create a connector and model group

##### 1a. Register model group

```json
POST /_plugins/_ml/model_groups/_register

{
  "name": "voyage_model_group",
  "description": "Your Voyage AI model group"
}
```

This request response will return the `model_group_id`, note it down.

##### 1b. Create a connector

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Voyage AI Embed Model",
  "description": "The connector to Voyage AI's public embeddings API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "voyage_key": "<ENTER_VOYAGE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>", // Choose a model from the list above
    "input_type": "document",
    "input_docs_processed_step_size": "128"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.voyageai.com/v1/embeddings",
      "headers": {
        "Authorization": "Bearer ${credential.voyage_key}",
        "Content-Type": "application/json"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }",
      "pre_process_function": "connector.pre_process.voyage.embedding",
      "post_process_function": "connector.post_process.voyage.embedding"
    }
  ]
}
```

`input_type` can only be `document` or `query`. This connector embeds everything with the `input_type` you set in the parameters. Use `document` for the connector that indexes text. For search queries, create a second connector and model with `"input_type": "query"` and use that model id in your query-time embedding (for example in a `neural` query or a search request processor); the vectors from both are compatible.

To use a smaller embedding, add `output_dimension` to the parameters and to the request body. It is only valid for the models that list other dimensions above; the other models return HTTP 400 for any value except 1024.

```json
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>",
    "input_type": "document",
    "output_dimension": 512,
    "input_docs_processed_step_size": "128"
  },
  ...
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\", \"output_dimension\": ${parameters.output_dimension} }",
```

If you leave `model` out, the predict call fails with `Some parameter placeholder not filled in payload: model`.

This request response will return the `connector_id`, note it down.

##### 1c. Register a model with your connector

```json
POST /_plugins/_ml/models/_register?deploy=true
Content-Type: application/json

{
    "name": "Voyage AI Embed Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your Voyage AI Embedding Model",
    "connector_id": "<CONNECTOR_ID>"
}
```

The response contains the `task_id` and the `model_id`. If the model is not deployed yet, deploy it with:

```json
POST /_plugins/_ml/models/<MODEL_ID>/_deploy
```

##### 1d. Test model

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "input": ["Say this is a test"]
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
            ... ...
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

The `field_map` parameter contains all the document fields you'd like to embed. The key is the document field name, and the value is the field that receives the embedding.

```json
PUT /_ingest/pipeline/voyage-ingest-pipeline
{
  "description": "Test Voyage AI Embedding pipeline",
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

##### 2b. Create a k-NN index

The `dimension` must match the model's embedding dimension (`1024` by default, or your `output_dimension`).

```json
PUT /voyage-nlp-index
{
  "settings": {
    "index.knn": true,
    "default_pipeline": "voyage-ingest-pipeline"
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
```

##### 2c. Testing the index and pipeline

```json
PUT /voyage-nlp-index/_doc/1
{
  "passage_text": "Hi - Voyage AI Embeddings are cool!",
  "id": "v1"
}
```

Then check that the embedding was created:

```json
GET /voyage-nlp-index/_search
{
  "query": {
    "match_all": {}
  }
}
```
