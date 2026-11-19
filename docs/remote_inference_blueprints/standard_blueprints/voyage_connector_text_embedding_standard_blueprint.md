# Voyage AI Text Embedding Connector Standard Blueprint:

This blueprint demonstrates how to deploy a [Voyage AI](https://docs.voyageai.com/docs/embeddings) text embedding model using the connector without pre and post processing functions.
This is recommended for version after OS 2.14.0 to use the ML inference processor to handle input/output mapping.
If you need the `text_embedding` ingest processor, please refer to the legacy blueprint: [Voyage AI Embedding Connector Blueprint](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/voyage_connector_embedding_blueprint.md)

Supported models: `voyage-4-large`, `voyage-4`, `voyage-4-lite`, `voyage-code-4`, `voyage-3-large`, `voyage-3.5`, `voyage-3.5-lite`, `voyage-code-3` (default dimension 1024, `output_dimension` 2048, 1024, 512 or 256, 32K context), `voyage-finance-2` (1024, 32K context) and `voyage-law-2` (1024, 16K context).

The model is always required; the blueprint has no default. A request accepts at most 1000 inputs and a per-model token cap: 1M tokens for `voyage-4-lite` and `voyage-3.5-lite`, 320K for `voyage-4` and `voyage-3.5`, and 120K for `voyage-4-large`, `voyage-3-large`, `voyage-code-3`, `voyage-finance-2` and `voyage-law-2`. Voyage documents no cap for `voyage-code-4`. This blueprint sends the whole `input` list in one request, and ML Commons does not count tokens, so keep each list under these limits. (`input_docs_processed_step_size` only splits text documents sent as `text_docs`; see the legacy blueprint.)

## 1. Create a connector

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
    "input_type": "document"
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
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }"
    }
  ]
}
```

`input_type` can only be `document` or `query`. To request a smaller embedding, add `\"output_dimension\": ${parameters.output_dimension}` to the request body and set `output_dimension` in the parameters (only for the models that support it).

## 2. Register model to model group & deploy model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Voyage AI Embed Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your Voyage AI Embedding Model",
    "connector_id": "<CONNECTOR_ID>"
}
```

## 3. Test model inference

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "input": ["Say this is a test"]
  }
}
```

Sample response:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "object": "list",
            "data": [
              {
                "object": "embedding",
                "embedding": [-0.0024547577, 0.0062217712, ...],
                "index": 0
              }
            ],
            "model": "voyage-4-lite",
            "usage": {
              "total_tokens": 6
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 4. Use the model in an ingest pipeline

With the ML inference processor, map a field that holds a list of strings to `input` and read the vector from `data[0].embedding`:

```json
PUT /_ingest/pipeline/voyage-ingest-pipeline
{
  "processors": [
    {
      "ml_inference": {
        "model_id": "<MODEL_ID>",
        "input_map": [
          { "input": "texts" }
        ],
        "output_map": [
          { "text_embedding": "data[0].embedding" }
        ]
      }
    }
  ]
}
```
