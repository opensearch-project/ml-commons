# GCP Vertex AI Gemini Streaming Connector Blueprint

This blueprint streams Gemini responses via Vertex AI `streamGenerateContent` (server-sent
events) using the `google_cloud` connector protocol. OAuth2 tokens are minted and refreshed
automatically; no `Authorization` header is added by you.

Streaming requires the `_llm_interface` parameter set to `gemini/v1beta/generatecontent`, and
predictions are issued against the streaming predict endpoint.

## 1. Add Vertex AI endpoint to trusted URLs

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://.*-aiplatform\\.googleapis\\.com/.*$"
        ]
    }
}
```

## 2. Create the connector

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "GCP Vertex AI Connector: Gemini streaming",
    "description": "Vertex AI Gemini streamGenerateContent connector",
    "version": 1,
    "protocol": "google_cloud",
    "parameters": {
        "project_id": "<YOUR_PROJECT_ID>",
        "location": "us-central1",
        "model": "gemini-2.5-flash",
        "scopes": "https://www.googleapis.com/auth/cloud-platform",
        "_llm_interface": "gemini/v1beta/generatecontent"
    },
    "credential": {
        "private_key": "<YOUR_SERVICE_ACCOUNT_PRIVATE_KEY>",
        "client_email": "<YOUR_SERVICE_ACCOUNT_CLIENT_EMAIL>",
        "token_uri": "https://oauth2.googleapis.com/token"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/${parameters.project_id}/locations/${parameters.location}/publishers/google/models/${parameters.model}:streamGenerateContent?alt=sse",
            "headers": {
                "Content-Type": "application/json"
            },
            "request_body": "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.prompt}\"}]}]}"
        }
    ]
}
```

For ADC / Workload Identity mode, set `"auth_mode": "adc"` in `parameters` and leave
`credential` empty (`{}`).

## 3. Register and deploy the model

Register and deploy the model as in the
[Gemini blueprint](./gcp_vertexai_gemini_blueprint.md) (steps 3–5), using this connector.

## 4. Test streaming inference

Use the streaming predict endpoint. The `_llm_interface` parameter selects the Gemini SSE
parser:

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict/stream
{
    "parameters": {
        "prompt": "Tell me a short story about OpenSearch.",
        "_llm_interface": "gemini/v1beta/generatecontent"
    }
}
```
