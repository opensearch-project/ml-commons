# OpenAI-compatible chat connector blueprint behind an OAuth 2.0 identity provider

This blueprint connects to a self-hosted or gateway-fronted endpoint that implements the OpenAI chat completions API,
such as vLLM, TGI, Ollama or LiteLLM behind a gateway, where the gateway accepts OAuth 2.0 access tokens issued by an
identity provider (Keycloak in the example, others below). The connector declares an `auth_config` of type `oauth2`
with the client credentials grant. The plugin requests the token from the identity provider, keeps it in a cache on each
node, requests a new one before it expires, and sends `Authorization: Bearer <token>` with every request. The connector
itself defines no `Authorization` header, and nobody has to rotate a token by hand.

Before you start, register a confidential client for OpenSearch at the identity provider with the client credentials
grant (in Keycloak: a client with client authentication on and service accounts enabled), and note its client ID and
secret, the token endpoint, and the scope or audience the gateway expects, if any.

The examples use `idp.example.com` for the identity provider, realm `ml`, and `llm.example.com` for the gateway.

## 1. Enable auth_config and trust both endpoints

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with `403` until it is
on. The connector needs two list settings to cover its endpoints:

- `plugins.ml_commons.trusted_connector_endpoints_regex` for the model endpoint.
- `plugins.ml_commons.trusted_oauth2_token_endpoints_regex` for the token endpoint. Its defaults cover the client
  credentials token endpoints of Microsoft Entra ID, Okta, Salesforce and the Databricks account host; other identity
  providers are added here.

A `PUT` of a list setting replaces it, so read both first and send every pattern back, otherwise the other connectors
in the cluster stop working. The response lists the patterns under `defaults` while a setting has its default value,
and under `persistent` or `transient` once it was changed:

```json
GET /_cluster/settings?include_defaults=true&filter_path=**.trusted_connector_endpoints_regex,**.trusted_oauth2_token_endpoints_regex
```

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.auth_enabled": true,
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY trusted_connector_endpoints_regex PATTERN FROM THE RESPONSE ABOVE",
            "^https://llm\\.example\\.com/v1/.*$"
        ],
        "plugins.ml_commons.trusted_oauth2_token_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY trusted_oauth2_token_endpoints_regex PATTERN FROM THE RESPONSE ABOVE",
            "^https://idp\\.example\\.com/realms/ml/protocol/openid-connect/token$"
        ]
    }
}
```

A token endpoint pattern must match the whole token URL in its canonical form: `https`, the host in lower case, no
`:443`, and the path as written. Write the host with literal labels or `[a-z0-9-]+`; a pattern that could let the host
match other characters, such as `.*`, is rejected.

Token requests connect only to the addresses the connector address settings allow. When the identity provider or the
gateway resolves to a private address, also set `plugins.ml_commons.connector.private_ip_enabled` to `true`, and when
`plugins.ml_commons.trusted_connector_private_endpoints_regex` is set, add a pattern for each host to it. Whatever these
settings say, a token request never connects to a loopback, unspecified or IPv6 unique-local address, to an address of
the node itself, or to a port in the node's `http.port` or `transport.port` range. The identity provider's TLS
certificate must chain to a CA in the JVM trust store of every node; there is no option to skip the verification.

## 2. Create the connector

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "OpenAI-compatible chat with OAuth 2.0",
  "description": "OpenAI-compatible chat completions behind a gateway that accepts OAuth 2.0 access tokens",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://idp.example.com/realms/ml/protocol/openid-connect/token",
    "client_auth_method": "client_secret_basic",
    "client_id": "<YOUR CLIENT ID>",
    "client_secret": "${credential.client_secret}",
    "scope": "<SCOPE THE GATEWAY EXPECTS, OR REMOVE THIS LINE>"
  },
  "parameters": {
    "endpoint": "llm.example.com",
    "model": "<YOUR MODEL NAME>"
  },
  "credential": {
    "client_secret": "<YOUR CLIENT SECRET>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Content-Type": "application/json"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
    }
  ]
}
```

The client secret is stored encrypted in `credential`, and `auth_config.client_secret` refers to it with a
`${credential.<key>}` reference; a literal secret in `auth_config` is rejected. The create request is also rejected
when an action URL does not use `https`, when an action defines its own `Authorization` header, when
`client_config.skip_ssl_verification` is set, and when the token URL does not match
`plugins.ml_commons.trusted_oauth2_token_endpoints_regex`.

`client_secret_basic` sends the client ID and secret in a Basic `Authorization` header to the token endpoint, each
form-encoded first as RFC 6749 section 2.3.1 requires. If the identity provider rejects a client ID or secret that
contains special characters, use `client_secret_post`, which sends them in the form body.

The token is read from `access_token` and its lifetime from `expires_in`. For an identity provider that names them
differently, set `token_response` in `auth_config`: `access_token_field`, `expires_in_field`, `expires_at_field` (an
absolute expiry in epoch seconds) and `default_ttl_seconds` (the lifetime when the response gives none; default 300, at
most 3600). A cached token never outlives `plugins.ml_commons.connector.oauth2_token_cache_max_seconds` (default 3600).

### Sample response

```json
{
  "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```

### Other identity providers

| Identity provider | `token_url` | `client_auth_method` | Notes |
|---|---|---|---|
| Keycloak | `https://<host>/realms/<realm>/protocol/openid-connect/token` | `client_secret_basic` | append the token endpoint pattern |
| Okta | `https://<org>.okta.com/oauth2/<authorization server id>/v1/token` | `client_secret_basic` | covered by the default token endpoint patterns |
| Ping Identity | `https://<host>/as/token.oauth2` | `client_secret_basic` | append the token endpoint pattern |
| Microsoft Entra ID | `https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token` | `client_secret_post` | covered by the default token endpoint patterns; `scope` is `api://<application id of the gateway>/.default` |
| Auth0 | `https://<tenant>.auth0.com/oauth/token` | `client_secret_post` | append the token endpoint pattern; set `audience` to the API identifier |

## 3. Register and deploy the model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "openai-compatible-chat",
    "function_name": "remote",
    "description": "OpenAI-compatible chat model behind an OAuth 2.0 identity provider",
    "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```

### Sample response

```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "lyjxwZABNrAVdFa9zrcZ"
}
```

## 4. Test the model

```json
POST /_plugins/_ml/models/lyjxwZABNrAVdFa9zrcZ/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Hello!"
      }
    ]
  }
}
```

### Sample response

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "object": "chat.completion",
            "choices": [
              {
                "index": 0,
                "finish_reason": "stop",
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I help you today?"
                }
              }
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed models fail the
  same way while it is off.
- `auth_config.token_url does not match plugins.ml_commons.trusted_oauth2_token_endpoints_regex`: no pattern of the
  setting matches the token URL; see step 1.
- `Failed to obtain OAuth2 token from idp.example.com: <reason>`: the token request failed. `invalid_client (HTTP 401)`
  means the identity provider rejected the client ID or secret; a reason about the token endpoint host or port means
  the address checks of step 1 refused it.
- The model request returns `401` from the gateway: check that the gateway accepts tokens of this client, scope and
  audience. After a `401` the plugin sends the request once more with a new token, unless the token it used is younger
  than ten seconds.
