# Azure OpenAI connector blueprint example for embedding model

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

If you are using self-managed Opensearch, you should supply OpenAI API key:


```json
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
      "request_body": "{ \"input\": ${parameters.input}}",
      "pre_process_function": "connector.pre_process.openai.embedding",
      "post_process_function": "connector.post_process.openai.embedding"
    }
  ]
}
```

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
    "input": [ "What is the meaning of life?" ]
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1536
          ],
          "data": [
            -0.0043460787,
            -0.029653417,
            -0.008173223,
            ...
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

