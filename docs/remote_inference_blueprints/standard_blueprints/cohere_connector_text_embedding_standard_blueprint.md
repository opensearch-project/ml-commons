# Cohere Text Embedding Connector Blueprint:

This blueprint demonstrates how to deploy a text embedding model using embed-english-v2.0 and embed-english-v3.0 using the Cohere connector without pre and post processing functions.
This is recommended for models to use the ML inference processor to handle input/output mapping.
Note that if using a model that requires pre and post processing functions, you must provide the functions in the blueprint. Please refer to legacy blueprint: [Cohere Embedding Connector Blueprint for text embedding mode](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/cohere_connector_embedding_blueprint.md)

- embed-english-v3.0 1024
- embed-english-v2.0 4096

See [Cohere's /embed API docs](https://docs.cohere.com/reference/embed) for more details.

## 1. Add connector endpoint to trusted URLs:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
          "^https://api\\.cohere\\.ai/.*$"
        ]
    }
}
```
Sample response:
```json
{
    "acknowledged": true,
    "persistent": {
        "plugins": {
            "ml_commons": {
                "trusted_connector_endpoints_regex": [
                    "^https://api\\.cohere\\.ai/.*$"
                ]
            }
        }
    },
    "transient": {}
}
```

## 2. Create a connector 

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
      "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }"
    }
  ]
}
```
If you're using cohere V2 embedding API, you should pass `embedding_types` in the request body
```json
POST /_plugins/_ml/connectors/_create
{
  ...
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.ai/v2/embed",
      "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"END\", \"model\": \"${parameters.model_name}\", \"embedding_types\": [\"float\"], \"input_type\": \"${parameters.input_type}\"}"
    }
  ]
}
```

For cohere v2 embedding API, there are several build-in post_process_function that can extract the embedding result to a list of list of number format:
1. v2 float: connector.post_process.cohere_v2.embedding.float
2. v2 int8: connector.post_process.cohere_v2.embedding.int8
3. v2 uint8: connector.post_process.cohere_v2.embedding.uint8
4. v2 binary: connector.post_process.cohere_v2.embedding.binary
5. v2 ubinary: connector.post_process.cohere_v2.embedding.ubinary

This request response will return the `connector_id`, note it down.

Sample response:
```json
{
    "connector_id": "fw_GjJUB_BtQcl4FasiL"
}
```


## 3. Register model to model group & deploy model:

You can now register your model with `connector_id` created from the previous steps.

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Cohere Embed Model",
    "function_name": "remote",
    "description": "Your Cohere Embedding Model",
    "connector_id": "<CONNECTOR_ID>"
}
```
This will create a registration task, the response should look like:

```json
{
  "task_id": "hQ_HjJUB_BtQcl4Fecii",
  "status": "CREATED",
  "model_id": "hg_HjJUB_BtQcl4Feci0"
}
```
Model should be deployed already. in this demo the model id is `hg_HjJUB_BtQcl4Feci0`
If we still need to deploy the model

```json
POST /_plugins/_ml/models/hg_HjJUB_BtQcl4Feci0/_deploy
```

```json
{
    "task_id": "IQ-ojJUB_BtQcl4FyMhB",
    "task_type": "DEPLOY_MODEL",
    "status": "COMPLETED"
}
```
## 4. Test model inference

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "texts": ["Say this is a test"]
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
            "id": "e240f7ea-e442-4638-87b4-73dd7e0dae51",
            "texts": [
              "say this is a test"
            ],
            "embeddings": [
              [
                -0.0024547577,
                0.0062217712,
                -0.01675415,
                ...
              ]
            ],
            "meta": {
              "api_version": {
                "version": "1"
              },
              "billed_units": {
                "input_tokens": 5.0
              }
            },
            "response_type": "embeddings_floats"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```