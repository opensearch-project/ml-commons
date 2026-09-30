# GCP Vertex AI Embedding Connector Blueprint

This blueprint uses the `google_cloud` connector protocol, which mints and refreshes GCP
OAuth2 access tokens automatically. You do **not** supply or rotate an access token by hand,
and you do **not** add an `Authorization` header — the plugin injects it per request.

Two credential modes are supported:
- **Service-account key** — supply the service account's `private_key` and `client_email`.
- **ADC / Workload Identity** — set `auth_mode: adc` and supply no credentials.

> Replaces the legacy `gcp_vertexai_connector_embedding_blueprint.md`, which used the generic
> `http` protocol with a manually refreshed static token.

## 1. Enable the connector and check trusted endpoints

The `google_cloud` connector is opt-in. With `plugins.ml_commons.connector.vertexai_enabled`
left at its default of `false`, creating the connector in step 2 fails with `403` and the message
"The Vertex AI (google_cloud) connector is not enabled."

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.vertexai_enabled": true
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
            "REPLACE THIS LINE WITH EVERY PATTERN FROM THE RESPONSE ABOVE",
            "^https://([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com/.*$"
        ]
    }
}
```

Assigning only the Vertex AI pattern replaces the defaults, which breaks connector creation and
inference for every other provider in the cluster, including SageMaker, OpenAI, Cohere, DeepSeek
and Bedrock.

## 2. Create the connector

### Option A — Service-account key mode

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "GCP Vertex AI Connector: embedding",
    "description": "Vertex AI text embedding connector",
    "version": 1,
    "protocol": "google_cloud",
    "parameters": {
        "project_id": "<YOUR_PROJECT_ID>",
        "location": "us-central1",
        "model": "text-embedding-004",
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
            "url": "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/${parameters.project_id}/locations/${parameters.location}/publishers/google/models/${parameters.model}:predict",
            "headers": {
                "Content-Type": "application/json"
            },
            "request_body": "{\"instances\":[{\"content\":\"${parameters.prompt}\"}]}"
        }
    ]
}
```

### Option B — ADC / Workload Identity mode

```json
    "parameters": {
        "project_id": "<YOUR_PROJECT_ID>",
        "location": "us-central1",
        "model": "text-embedding-004",
        "auth_mode": "adc",
        "scopes": "https://www.googleapis.com/auth/cloud-platform"
    },
    "credential": {}
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
    "name": "vertexAI embedding model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Vertex AI text embedding model",
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
