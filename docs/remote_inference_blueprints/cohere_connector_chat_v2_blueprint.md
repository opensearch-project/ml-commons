# Cohere Chat v2 Connector Blueprint

This blueprint connects a Cohere v2 chat model to your OpenSearch cluster using the [Cohere v2 Chat API](https://docs.cohere.com/reference/chat). You will need a Cohere API key.

The v2 API uses the OpenAI-compatible messages format (`messages` array with `role`/`content`). Use this blueprint instead of the v1 blueprint when you want structured output support for agentic memory.

> **Note:** `supports_structured_output: true` enables JSON schema enforcement for agentic memory fact extraction. See [connector action parameters](../tutorials/remote_inference.md#connector) for details.

## 1. Add connector endpoint to trusted URLs

```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://api\\.cohere\\.com/.*$"
    ]
  }
}
```

## 2. Create connector

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Cohere Chat v2",
  "description": "Connector to Cohere v2 Chat API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "cohere_key": "<ENTER_COHERE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "command-r-plus"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.com/v2/chat",
      "supports_structured_output": true,
      "headers": {
        "Authorization": "Bearer ${credential.cohere_key}",
        "Request-Source": "unspecified:opensearch"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"stream\": false, \"messages\": ${parameters.messages} }"
    }
  ]
}
```

## 3. Register and deploy model

```json
POST /_plugins/_ml/models/_register
{
  "name": "Cohere Chat v2",
  "function_name": "remote",
  "description": "Cohere v2 chat model",
  "connector_id": "<CONNECTOR_ID>"
}
```

Poll the returned `task_id` until `status` is `COMPLETED`, then deploy:

```json
POST /_plugins/_ml/models/<MODEL_ID>/_deploy
```

## 4. Test model

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": "What is OpenSearch?"
      }
    ]
  }
}
```
