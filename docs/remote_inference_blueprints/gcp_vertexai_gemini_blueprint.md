# GCP Vertex AI Gemini Connector Blueprint

This blueprint uses the `google_cloud` connector protocol, which mints and refreshes GCP
OAuth2 access tokens automatically. You do **not** supply or rotate an access token by hand,
and you do **not** add an `Authorization` header — the plugin injects it per request.

Two credential modes are supported:
- **Service-account key** — supply the service account's `private_key` and `client_email`.
  The `token_uri` is optional and, when set, is restricted to Google token endpoints
  (`*.googleapis.com` over HTTPS).
- **ADC / Workload Identity** — set `auth_mode: adc` and supply no credentials; the node's
  Application Default Credentials (GKE Workload Identity, GCE metadata server, or an ADC file)
  are used. Use this mode only on GCP-hosted nodes: it resolves credentials from the
  environment, which includes contacting the GCE/GKE metadata server.

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

### Option A — Service-account key mode

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "GCP Vertex AI Connector: Gemini",
    "description": "Vertex AI Gemini generateContent connector",
    "version": 1,
    "protocol": "google_cloud",
    "parameters": {
        "project_id": "<YOUR_PROJECT_ID>",
        "location": "us-central1",
        "model": "gemini-2.5-flash",
        "scopes": "https://www.googleapis.com/auth/cloud-platform"
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
            "url": "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/${parameters.project_id}/locations/${parameters.location}/publishers/google/models/${parameters.model}:generateContent",
            "headers": {
                "Content-Type": "application/json"
            },
            "request_body": "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.prompt}\"}]}]}"
        }
    ]
}
```

### Option B — ADC / Workload Identity mode

Only the `credential` and `parameters` blocks differ; leave `credential` empty and set
`auth_mode: adc`:

```json
    "parameters": {
        "project_id": "<YOUR_PROJECT_ID>",
        "location": "us-central1",
        "model": "gemini-2.5-flash",
        "auth_mode": "adc",
        "scopes": "https://www.googleapis.com/auth/cloud-platform"
    },
    "credential": {}
```

Sample response:

```json
{
    "connector_id": "Xy1abcAB1cd2 efGHIjk"
}
```

## 3. Create a model group

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "vertexai_model_group",
    "description": "Model group for GCP Vertex AI models"
}
```

## 4. Register the model

```json
POST /_plugins/_ml/models/_register
{
    "name": "vertexAI Gemini model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Vertex AI Gemini generateContent model",
    "connector_id": "<CONNECTOR_ID>"
}
```

## 5. Deploy the model

```json
POST /_plugins/_ml/models/<MODEL_ID>/_deploy
```

## 6. Test inference

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
    "parameters": {
        "prompt": "Hello from Vertex AI!"
    }
}
```
