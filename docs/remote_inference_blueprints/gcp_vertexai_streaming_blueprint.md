# GCP Vertex AI Gemini Streaming Connector Blueprint

This blueprint streams Gemini responses via Vertex AI `streamGenerateContent` (server-sent
events) using the `google_cloud` connector protocol. OAuth2 tokens are minted and refreshed
automatically; no `Authorization` header is added by you.

Streaming requires the `_llm_interface` parameter set to `gemini/v1beta/generatecontent`, and
predictions are issued against the streaming predict endpoint.

## 1. Enable the connector and streaming, and check trusted endpoints

The `google_cloud` connector and the streaming API are both opt-in, and both default to `false`.
Without `plugins.ml_commons.connector.vertexai_enabled`, creating the connector in step 2 fails
with `403` and the message "The Vertex AI (google_cloud) connector is not enabled." Without
`plugins.ml_commons.stream_enabled`, the `_predict/stream` call in step 4 fails with "Streaming is
currently disabled".

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.vertexai_enabled": true,
        "plugins.ml_commons.stream_enabled": true
    }
}
```

**Trusted endpoints.** Starting in 3.10, the Vertex AI host is matched by the default
`plugins.ml_commons.trusted_connector_endpoints_regex`, so no endpoint change is needed. On 3.9,
and on any cluster that has overridden that setting, you must add a Vertex AI pattern yourself.
Check which applies to your cluster:

```json
GET /_cluster/settings?include_defaults=true&filter_path=**.trusted_connector_endpoints_regex
```

If no pattern in the response matches `*-aiplatform.googleapis.com`, re-send the list with one
added. The setting is a list and a `PUT` replaces it rather than appending, so include every
pattern the response returned:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "<each pattern returned above>",
            "^https://([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com/.*$"
        ]
    }
}
```

Assigning only the Vertex AI pattern replaces the defaults, which breaks connector creation and
inference for every other provider in the cluster, including SageMaker, OpenAI, Cohere, DeepSeek
and Bedrock.

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
