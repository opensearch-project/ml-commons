# GCP Vertex AI Gemini Streaming Connector Blueprint

This blueprint streams Gemini responses via Vertex AI `streamGenerateContent` (server-sent
events) using the `google_cloud` connector protocol. OAuth2 tokens are minted and refreshed
automatically; no `Authorization` header is added by you.

Streaming requires the `_llm_interface` parameter set to `gemini/v1beta/generatecontent`, and
predictions are issued against the streaming predict endpoint.

The same connector can also be declared with `protocol: http` and an `auth_config` block; see
[Generic form](#generic-form-protocol-http-auth_config) below. Existing connectors on
`protocol: google_cloud` keep working. Service-account connectors have their tokens obtained through
the shared OAuth 2.0 engine (same assertion claims, cached per node, one retry after a `401`), with
the token endpoint matched against `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, whose
default includes `https://oauth2.googleapis.com/token`. For service-account and ADC connectors
alike, `client_config.skip_ssl_verification` is not accepted together with authentication and an
action may not declare its own `Authorization` header.

## 1. Enable the connector and streaming, and check trusted endpoints

The `google_cloud` connector and the streaming API are both opt-in. Creating the connector fails
with `403` until the first is enabled, and `_predict/stream` fails until the second is. From 3.10
the Vertex AI host is already covered by the default
`plugins.ml_commons.trusted_connector_endpoints_regex`, so these are all that are needed:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.vertexai_enabled": true,
        "plugins.ml_commons.stream_enabled": true
    }
}
```

**Streaming prerequisites.** `plugins.ml_commons.stream_enabled` is necessary but not sufficient.
The `_predict/stream` call in step 4 also needs cluster-level setup that this blueprint does not
cover: the `transport-reactor-netty4`, `arrow-base` and `arrow-flight-rpc` plugins, which ship with
OpenSearch but are not installed by default, plus `http.type` and
`opensearch.experimental.feature.transport.stream.enabled` in `opensearch.yml`. Those two settings
are static, so `_cluster/settings` rejects them and a node restart is required. See
[Predict Stream API prerequisites](https://docs.opensearch.org/latest/ml-commons-plugin/api/train-predict/predict-stream/#prerequisites).

On 3.9 and earlier, or if that regex setting has been overridden, a Vertex AI pattern is
needed too. It is a list setting and a `PUT` replaces it, so read it first and send every
pattern back, otherwise the other providers in the cluster stop working:

```json
GET /_cluster/settings?include_defaults=true&filter_path=**.trusted_connector_endpoints_regex
```

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.vertexai_enabled": true,
        "plugins.ml_commons.stream_enabled": true,
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY PATTERN FROM THE RESPONSE ABOVE",
            "^https://([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com/.*$"
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

**ADC mode.** ADC uses the application default credentials present on the OpenSearch nodes and is
expressed only with the `protocol: google_cloud` form and `auth_mode: adc`; it has no `auth_config`
form.

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

## Generic form (`protocol: http`, `auth_config`)

The same connector can be declared on the generic `http` protocol with an `auth_config` block of type
`oauth2` and the JWT bearer grant. The node signs a JWT assertion with the service account's private
key, exchanges it at the Google token endpoint for an access token, keeps the token in a cache on each
node, requests a new one before it expires, and sends `Authorization: Bearer <token>` with every
request, streams included. You write no `Authorization` header and never handle a token. The
`_llm_interface` value `gemini/v1beta/generatecontent` is accepted on `protocol: http`.

### Prerequisites

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with
`403` until it is on; `plugins.ml_commons.stream_enabled` and the streaming prerequisites of step 1
still apply. The Google token endpoint, `https://oauth2.googleapis.com/token`, is covered by the
default `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, so that setting needs no change.
The Vertex AI host is covered by the default `plugins.ml_commons.trusted_connector_endpoints_regex` as
in step 1; if that setting has been overridden, add the Vertex AI pattern of step 1 to it. This form
does not use `protocol: google_cloud`, so `plugins.ml_commons.connector.vertexai_enabled` is not
required.

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.auth_enabled": true,
        "plugins.ml_commons.stream_enabled": true
    }
}
```

### Create the connector

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "GCP Vertex AI Connector: Gemini streaming",
    "description": "Vertex AI Gemini streamGenerateContent connector",
    "version": 1,
    "protocol": "http",
    "auth_config": {
        "type": "oauth2",
        "token_url": "https://oauth2.googleapis.com/token",
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "client_auth_method": "none",
        "jwt": {
            "issuer": "<YOUR_SERVICE_ACCOUNT_CLIENT_EMAIL>",
            "signing_alg": "RS256",
            "lifetime_seconds": 3600,
            "additional_claims": {
                "scope": "https://www.googleapis.com/auth/cloud-platform"
            },
            "private_key": "${credential.private_key}"
        }
    },
    "parameters": {
        "project_id": "<YOUR_PROJECT_ID>",
        "location": "us-central1",
        "model": "gemini-2.5-flash",
        "_llm_interface": "gemini/v1beta/generatecontent"
    },
    "credential": {
        "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
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

The values come from the service account's JSON key file: `client_email` is the `issuer`, `private_key`
goes into `credential` (paste it as it is, with its `\n` escapes), and `token_uri` is the `token_url`.
The key must be an unencrypted PKCS#8 PEM (`-----BEGIN PRIVATE KEY-----`), the format Google issues.
`auth_config.jwt.private_key` refers to it with a `${credential.<key>}` reference; a literal key in
`auth_config` is rejected. The scope is a claim of the assertion, so it is written under
`additional_claims`, not as a `parameters.scopes` entry. The assertion carries no `sub` claim unless
`subject` is set.

`project_id`, `location` and `model` are stored on the connector: the host of the action URL comes
from `location`, so it must be stored on the connector and cannot be changed per request. The create
request is also rejected when an action URL does not use `https`, when an action defines its own
`Authorization` header, or when `client_config.skip_ssl_verification` is set.

### Register, deploy and test

Register and deploy the model as in the [Gemini blueprint](./gcp_vertexai_gemini_blueprint.md)
(steps 3–5), then test streaming inference as in step 4 above. For troubleshooting, see the
[Gemini blueprint](./gcp_vertexai_gemini_blueprint.md#troubleshooting).
