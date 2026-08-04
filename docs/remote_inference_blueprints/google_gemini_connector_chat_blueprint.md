# Google Gemini Connector Blueprint for Chat

This blueprint connects a Google Gemini chat model to your OpenSearch cluster using the [Gemini API](https://ai.google.dev/api/generate-content). You will need a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).

> **Note:** `supports_structured_output: true` enables JSON schema enforcement for agentic memory fact extraction. See [connector action parameters](../tutorials/remote_inference.md#connector) for details.

## 1. Add connector endpoint to trusted URLs

```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://generativelanguage\\.googleapis\\.com/.*$"
    ]
  }
}
```

## 2. Create connector

The Gemini API uses `contents` (not `messages`) with `role` and `parts`. `generationConfig` is optional and can be passed per-request if needed.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Google Gemini Chat",
  "description": "Connector to Google Gemini generateContent API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "gemini_key": "<ENTER_GEMINI_API_KEY_HERE>"
  },
  "parameters": {
    "model": "gemini-2.0-flash"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent",
      "supports_structured_output": true,
      "headers": {
        "Content-Type": "application/json",
        "x-goog-api-key": "${credential.gemini_key}"
      },
      "request_body": "{ \"contents\": ${parameters.contents} }"
    }
  ]
}
```

## 3. Register and deploy model

```json
POST /_plugins/_ml/models/_register
{
  "name": "Google Gemini Chat",
  "function_name": "remote",
  "description": "Google Gemini chat model",
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
    "contents": [
      {
        "role": "user",
        "parts": [{"text": "What is OpenSearch?"}]
      }
    ]
  }
}
```
