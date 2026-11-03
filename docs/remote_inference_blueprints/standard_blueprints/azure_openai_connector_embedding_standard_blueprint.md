# Azure OpenAI connector standard blueprint example for embedding model

This blueprint demonstrates how to deploy a `text-embedding-ada-002` using the Azure OpenAI connector without pre and post processing functions. This is recommended for version after OS 2.14.0 for models to use the ML inference processor to handle input/output mapping. Note that if using a model that requires pre and post processing functions, you must provide the functions in the blueprint. Please refer to legacy blueprint: [Azure OpenAI connector blueprint example for embedding model](../azure_openai_connector_embedding_blueprint.md)

## 1. Add Azure OpenAI endpoint to trusted URLs:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://.*\\.openai\\.azure\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for Azure OpenAI embedding model:

Refer to [Azure OpenAI Service REST API reference - Embedding](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#embeddings).

If you are using self-managed OpenSearch, you should supply OpenAI API key:

```jsonc
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "parameters": {
    "endpoint": "<YOUR RESOURCE NAME>.openai.azure.com/",
    "deploy-name": "<YOUR DEPLOYMENT NAME>",
    "model": "text-embedding-ada-002",
    "api-version": "<YOUR API VERSION>"
  },
  "credential": {
    "openAI_key": "<YOUR API KEY>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/openai/deployments/${parameters.deploy-name}/embeddings?api-version=${parameters.api-version}",
      "headers": {
        "api-key": "${credential.openAI_key}"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"input_type\":  \"array\"}" // support array of strings
    }
  ]
}
```

> [!NOTE]
> If you need the input type to be a string instead of array of strings, you can modify the request body to:
> ```json
> "request_body": "{ \"input\": \"${parameters.input}\" }"
> ```
> See [Azure OpenAI API Reference - Request Body - input_type](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#request-body-1)

Sample response:
```json
{
  "connector_id": "OyB0josB2yd36FqHy3lO"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
    "description": "This is an example description"
}
```

Sample response:
```json
{
  "model_group_id": "TWR0josByE8GuSOJ629m",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "OpenAI embedding model",
  "function_name": "remote",
  "model_group_id": "TWR0josByE8GuSOJ629m",
  "description": "test model",
  "connector_id": "OyB0josB2yd36FqHy3lO"
}
```


Sample response:
```json
{
  "task_id": "PCB1josB2yd36FqHAXk9",
  "status": "CREATED"
}
```
Get model id from task
```json
GET /_plugins/_ml/tasks/PCB1josB2yd36FqHAXk9
```
Deploy model, in this demo the model id is `PSB1josB2yd36FqHAnl1`
```json
POST /_plugins/_ml/models/PSB1josB2yd36FqHAnl1/_deploy
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/PSB1josB2yd36FqHAnl1/_predict
{
  "parameters": {
    "input": ["What is the meaning of life?", "42"]
  }
}
```

Response:
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
                "index": 0,
                "embedding": [
                  0.004411249,
                  -0.029655455,
                  -0.008198498,
                  ...
                ]
              },
                            {
                "object": "embedding",
                "index": 1,
                "embedding": [
                  -0.020884188,
                  -0.012239939,
                  0.031366087,
                  ...
                ]
              }
            ],
            "model": "text-embedding-ada-002",
            "usage": {
              "prompt_tokens": 7,
              "total_tokens": 7
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
} 
```

