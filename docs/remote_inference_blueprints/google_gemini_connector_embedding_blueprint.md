# Google Gemini Connector Blueprint for Embedding

This blueprint connects a Google Gemini text embedding model to your OpenSearch cluster using the [Gemini embedContent API](https://ai.google.dev/api/embeddings). You will need a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).

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

The Gemini API uses `content.parts[].text` for the input text and returns the embedding as `embedding.values`. The `response_filter` extracts the values array directly.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Google Gemini Embedding",
  "description": "Connector to Google Gemini embedContent API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "gemini_key": "<ENTER_GEMINI_API_KEY_HERE>"
  },
  "parameters": {
    "model": "gemini-embedding-001",
    "response_filter": "$.embedding.values"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:embedContent",
      "headers": {
        "Content-Type": "application/json",
        "x-goog-api-key": "${credential.gemini_key}"
      },
      "request_body": "{ \"model\": \"models/${parameters.model}\", \"content\": { \"parts\": [{ \"text\": \"${parameters.text}\" }] } }"
    }
  ]
}
```

> **Note:** This blueprint calls `embedContent` for one input at a time via the `text` parameter. Gemini's `batchEmbedContents` endpoint can be used instead if you need to embed multiple documents per request; adjust `request_body` and `response_filter` accordingly.

## 3. Register and deploy model

```json
POST /_plugins/_ml/models/_register
{
  "name": "Google Gemini Embedding",
  "function_name": "remote",
  "description": "Google Gemini text embedding model",
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
    "text": "What is OpenSearch?"
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
            "response": [
              -0.011785943,
              0.023027036,
              0.0034649687,
              ...
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
