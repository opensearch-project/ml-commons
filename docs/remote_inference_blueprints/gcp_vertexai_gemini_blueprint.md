# GCP Vertex AI Gemini Connector Blueprint

This blueprint uses the `google_cloud` connector protocol, which mints and refreshes GCP
OAuth2 access tokens automatically. You do **not** supply or rotate an access token by hand,
and you do **not** add an `Authorization` header — the plugin injects it per request.

Two credential modes are supported:
- **Service-account key** — supply the service account's `private_key` and `client_email`.
  The `token_uri` is optional and defaults to `https://oauth2.googleapis.com/token`. When set it
  must be an `https` URL accepted by `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`,
  whose default covers that endpoint. It is the only endpoint that receives the signed assertion.
- **ADC / Workload Identity** — set `auth_mode: adc` and supply no credentials; the node's
  Application Default Credentials (GKE Workload Identity, GCE metadata server, or an ADC file)
  are used. Use this mode only on GCP-hosted nodes: it resolves credentials from the
  environment, which includes contacting the GCE/GKE metadata server.

The same connector can also be declared with `protocol: http` and an `auth_config` block; see
[Generic form](#generic-form-protocol-http-auth_config) below. Existing connectors on
`protocol: google_cloud` keep working. Service-account connectors have their tokens obtained through
the shared OAuth 2.0 engine (same assertion claims, cached per node, one retry after a `401`), with
the token endpoint matched against `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, whose
default includes `https://oauth2.googleapis.com/token`. For service-account and ADC connectors
alike, `client_config.skip_ssl_verification` is not accepted together with authentication and an
action may not declare its own `Authorization` header.

## 1. Enable the connector and check trusted endpoints

The `google_cloud` connector is opt-in, and creating it fails with `403` until it is enabled. From
3.10 the Vertex AI host is already covered by the default
`plugins.ml_commons.trusted_connector_endpoints_regex`, so this is all that is needed:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.vertexai_enabled": true
    }
}
```

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
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY PATTERN FROM THE RESPONSE ABOVE",
            "^https://([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com/.*$"
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

**ADC mode.** ADC uses the application default credentials present on the OpenSearch nodes and is
expressed only with the `protocol: google_cloud` form and `auth_mode: adc`; it has no `auth_config`
form.

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

## Generic form (`protocol: http`, `auth_config`)

The same connector can be declared on the generic `http` protocol with an `auth_config` block of type
`oauth2` and the JWT bearer grant. The node signs a JWT assertion with the service account's private
key, exchanges it at the Google token endpoint for an access token, keeps the token in a cache on each
node, requests a new one before it expires, and sends `Authorization: Bearer <token>` with every
request. You write no `Authorization` header and never handle a token.

### Prerequisites

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with
`403` until it is on. The Google token endpoint, `https://oauth2.googleapis.com/token`, is covered by the
default `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, so that setting needs no change.
The Vertex AI host is covered by the default `plugins.ml_commons.trusted_connector_endpoints_regex` as
in step 1; if that setting has been overridden, add the Vertex AI pattern of step 1 to it. This form
does not use `protocol: google_cloud`, so `plugins.ml_commons.connector.vertexai_enabled` is not
required.

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.auth_enabled": true
    }
}
```

### Create the connector

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "GCP Vertex AI Connector: Gemini",
    "description": "Vertex AI Gemini generateContent connector",
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
        "model": "gemini-2.5-flash"
    },
    "credential": {
        "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
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

Steps 3 to 6 above apply unchanged.

### Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed
  models fail the same way while it is off.
- `auth_config.jwt.private_key must reference an unencrypted PKCS#8 PEM key
  (-----BEGIN PRIVATE KEY-----)`: the key is in another format, for example a
  `-----BEGIN RSA PRIVATE KEY-----` key or one protected by a passphrase.
- `Failed to obtain OAuth2 token from oauth2.googleapis.com: invalid_grant (HTTP 400)`: Google rejected
  the assertion. Check that `issuer` is the service account's email, that the key belongs to that
  service account and has not been deleted, and that the node clocks are correct.
- The model request returns `401` or `403` from Vertex AI: check the service account's IAM role on the
  project. After a `401` the plugin sends the request once more with a new token, unless the token it
  used is younger than ten seconds.
