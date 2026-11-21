# Databricks Model Serving connector blueprint for chat with OAuth machine-to-machine authentication

This blueprint connects to a Databricks Model Serving endpoint that serves a chat model, authenticated as a service
principal with Databricks OAuth machine-to-machine (M2M) authentication instead of a personal access token. Model
Serving queries its models through an OpenAI-compatible API
([Databricks: foundation models](https://learn.microsoft.com/en-us/azure/databricks/machine-learning/model-serving/score-foundation-models)).
The connector declares an `auth_config` of type `oauth2` with the client credentials grant. The plugin requests the
token from the workspace's token endpoint, keeps it in a cache on each node, requests a new one before it expires, and
sends `Authorization: Bearer <token>` with every request. The connector itself defines no `Authorization` header, and
nobody has to rotate a token by hand.

Verified against the provider's documentation and official SDKs; not run against a live account.

Before you start, in Databricks:

- Add a service principal to the workspace and create an OAuth secret for it, as described in Databricks' OAuth M2M
  guide for [AWS](https://docs.databricks.com/aws/en/dev-tools/auth/oauth-m2m),
  [Azure](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/oauth-m2m) or
  [Google Cloud](https://docs.databricks.com/gcp/en/dev-tools/auth/oauth-m2m). Note its client ID and the secret.
- Give the service principal permission to query the serving endpoint.
- Note the name of the serving endpoint and the workspace host, the host name of your per-workspace URL, for example
  `cust-success.cloud.databricks.com` on AWS
  ([workspace details](https://docs.databricks.com/aws/en/workspace/workspace-details)),
  `adb-1234567890123456.7.azuredatabricks.net` on Azure
  ([workspace details](https://learn.microsoft.com/en-us/azure/databricks/workspace/workspace-details)) or
  `1234567890123456.7.gcp.databricks.com` on Google Cloud
  ([OAuth M2M](https://docs.databricks.com/gcp/en/dev-tools/auth/oauth-m2m)). Databricks advises against legacy
  regional URLs, such as `westus.azuredatabricks.net`; the Azure and Google Cloud patterns of step 1 do not match
  them.

## 1. Enable auth_config and trust both endpoints

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with `403` until it is
on. The connector needs two list settings to cover its endpoints:

- `plugins.ml_commons.trusted_connector_endpoints_regex` for the serving endpoint.
- `plugins.ml_commons.trusted_oauth2_token_endpoints_regex` for the token endpoint. Its defaults cover the Databricks
  account token endpoint on AWS, but no workspace token endpoint.

The workspace token endpoint is `https://<workspace host>/oidc/v1/token` on all three clouds; only the host differs.
These patterns cover the workspace hosts of each cloud:

| Cloud | Token endpoint pattern | Serving endpoint pattern |
|---|---|---|
| AWS | `^https://[a-z0-9-]+\.cloud\.databricks\.com/oidc/v1/token$` | `^https://[a-z0-9-]+\.cloud\.databricks\.com/serving-endpoints/.*$` |
| Azure | `^https://[a-z0-9-]+\.[a-z0-9-]+\.azuredatabricks\.net/oidc/v1/token$` | `^https://[a-z0-9-]+\.[a-z0-9-]+\.azuredatabricks\.net/serving-endpoints/.*$` |
| Google Cloud | `^https://[a-z0-9-]+\.[a-z0-9-]+\.gcp\.databricks\.com/oidc/v1/token$` | `^https://[a-z0-9-]+\.[a-z0-9-]+\.gcp\.databricks\.com/serving-endpoints/.*$` |

A `PUT` of a list setting replaces it, so read both first and send every pattern back, otherwise the other connectors
in the cluster stop working. The response lists the patterns under `defaults` while a setting has its default value,
and under `persistent` or `transient` once it was changed:

```json
GET /_cluster/settings?include_defaults=true&filter_path=**.trusted_connector_endpoints_regex,**.trusted_oauth2_token_endpoints_regex
```

The request below appends the AWS patterns; for Azure or Google Cloud, use the patterns of that row of the table, with
each `\` written twice inside the JSON string:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.auth_enabled": true,
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY trusted_connector_endpoints_regex PATTERN FROM THE RESPONSE ABOVE",
            "^https://[a-z0-9-]+\\.cloud\\.databricks\\.com/serving-endpoints/.*$"
        ],
        "plugins.ml_commons.trusted_oauth2_token_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY trusted_oauth2_token_endpoints_regex PATTERN FROM THE RESPONSE ABOVE",
            "^https://[a-z0-9-]+\\.cloud\\.databricks\\.com/oidc/v1/token$"
        ]
    }
}
```

A token endpoint pattern must match the whole token URL in its canonical form: `https`, the host in lower case, no
`:443`, and the path as written. Write the host with literal labels or `[a-z0-9-]+`; a pattern that could let the host
match other characters, such as `.*`, is rejected. To trust a single workspace only, write its host with literal
labels, such as `^https://cust-success\.cloud\.databricks\.com/oidc/v1/token$`.

On AWS, the account token endpoint,
`https://accounts.cloud.databricks.com/oidc/accounts/<account id>/v1/token`, is covered by the default token endpoint
patterns. Databricks documents account tokens as usable for "both account-level and workspace-level REST APIs in
accounts and workspaces the service principal can access"
([OAuth M2M](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/oauth-m2m)), so on AWS you can set it as
`token_url` and skip the token endpoint pattern. The account token endpoints of Azure and Google Cloud are not in the
defaults. Tokens for route-optimized endpoints are requested from the workspace token endpoint (see
[Route-optimized endpoints](#route-optimized-endpoints)).

## 2. Create the connector

Refer to [Query chat models](https://learn.microsoft.com/en-us/azure/databricks/machine-learning/model-serving/query-chat-models)
and [OAuth M2M authentication](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/oauth-m2m).

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Databricks Model Serving chat",
  "description": "Databricks serving endpoint with the OpenAI-compatible chat API, OAuth M2M",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://<YOUR WORKSPACE HOST>/oidc/v1/token",
    "client_auth_method": "client_secret_basic",
    "client_id": "<YOUR SERVICE PRINCIPAL CLIENT ID>",
    "client_secret": "${credential.client_secret}",
    "scope": "all-apis"
  },
  "parameters": {
    "workspace_host": "<YOUR WORKSPACE HOST>",
    "endpoint": "<YOUR SERVING ENDPOINT NAME>",
    "max_tokens": 256
  },
  "credential": {
    "client_secret": "<YOUR OAUTH SECRET>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.workspace_host}/serving-endpoints/${parameters.endpoint}/invocations",
      "headers": {
        "Content-Type": "application/json"
      },
      "request_body": "{ \"messages\": ${parameters.messages}, \"max_tokens\": ${parameters.max_tokens} }"
    }
  ]
}
```

The client secret is stored encrypted in `credential`, and `auth_config.client_secret` refers to it with a
`${credential.<key>}` reference; a literal secret in `auth_config` is rejected. The create request is also rejected
when an action URL does not use `https`, when an action defines its own `Authorization` header, when
`client_config.skip_ssl_verification` is set, and when the token URL does not match
`plugins.ml_commons.trusted_oauth2_token_endpoints_regex`.

The token request is the one in Databricks' guide: the client ID and secret in a Basic `Authorization` header
(`client_secret_basic`), and `grant_type=client_credentials&scope=all-apis` in the form body
([OAuth M2M](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/oauth-m2m)). `all-apis` lets the
token call every API the service principal may use. To limit the token to queries of serving endpoints, set `scope`
to `model-serving-inference`, the API scope for "Query model serving endpoints for real-time inference"
([API scopes](https://docs.databricks.com/api/workspace/scopes)); a token minted from a scoped OAuth secret cannot
exceed the secret's scopes. Route-optimized endpoints take `all-apis` only (see
[Route-optimized endpoints](#route-optimized-endpoints)). "Each access token is valid for one hour", and the response
carries `expires_in`, so `token_response` needs no setting. A cached token never outlives
`plugins.ml_commons.connector.oauth2_token_cache_max_seconds` (default 3600).

`workspace_host`, `endpoint` and `max_tokens` are stored on the connector. The host of the action URL comes from
`workspace_host`, so it must be stored on the connector: request parameters cannot change the scheme, host or port of
an action URL.

On Azure Databricks, a service principal managed in Microsoft Entra ID can also authenticate with an Entra ID token
([Azure service principals](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/azure-sp)); this
blueprint does not cover that setup.

### Sample response

```json
{
  "connector_id": "bK8tSo0BdQxvLz7KN3qe"
}
```

### Route-optimized endpoints

A route-optimized serving endpoint is queried only with OAuth tokens and at its own URL, and Databricks requires that
route-optimized endpoints created from September 22, 2025 on are queried only through that URL
([Query route-optimized serving endpoints](https://learn.microsoft.com/en-us/azure/databricks/machine-learning/model-serving/query-route-optimization)).
Its token must carry `authorization_details` for the endpoint: a token with only `scope=all-apis` is rejected with
`401` "Missing authorization details", and `all-apis` is the only scope these tokens support. `authorization_details`
is a JSON array sent as one form field to the workspace token endpoint
([OAuth authorization details](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/oauth-authorization-details)).
Write it in `token_request_params` as a literal JSON string, and point the action at the route-optimized URL. These are
the members that change from step 2:

```json
{
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://<YOUR WORKSPACE HOST>/oidc/v1/token",
    "client_auth_method": "client_secret_basic",
    "client_id": "<YOUR SERVICE PRINCIPAL CLIENT ID>",
    "client_secret": "${credential.client_secret}",
    "scope": "all-apis",
    "token_request_params": {
      "authorization_details": "[{\"type\":\"workspace_permission\",\"object_type\":\"serving-endpoints\",\"object_path\":\"/serving-endpoints/<YOUR ENDPOINT ID>\",\"actions\":[\"query_inference_endpoint\"]}]"
    }
  },
  "parameters": {
    "serving_host": "<HOST OF THE ROUTE-OPTIMIZED URL>",
    "workspace_id": "<YOUR WORKSPACE ID>",
    "endpoint": "<YOUR SERVING ENDPOINT NAME>",
    "max_tokens": 256
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.serving_host}/${parameters.workspace_id}/serving-endpoints/${parameters.endpoint}/invocations",
      "headers": {
        "Content-Type": "application/json"
      },
      "request_body": "{ \"messages\": ${parameters.messages}, \"max_tokens\": ${parameters.max_tokens} }"
    }
  ]
}
```

- The token names one endpoint, so use one connector per route-optimized endpoint.
- The route-optimized URL is the `endpoint_url` that `GET /api/2.0/serving-endpoints/<endpoint name>` returns for a
  route-optimized endpoint. Its form is
  `https://<serving host>/<workspace id>/serving-endpoints/<endpoint name>/invocations`, and the serving host is
  `<unique id>.serving.cloud.databricks.com` on AWS, `<unique id>.<shard>.serving.azuredatabricks.net` on Azure and
  `<unique id>.serving.gcp.databricks.com` on Google Cloud
  ([AWS](https://docs.databricks.com/aws/en/machine-learning/model-serving/query-route-optimization),
  [Azure](https://learn.microsoft.com/en-us/azure/databricks/machine-learning/model-serving/query-route-optimization),
  [Google Cloud](https://docs.databricks.com/gcp/en/machine-learning/model-serving/query-route-optimization)).
- Databricks' route-optimization page describes `<YOUR ENDPOINT ID>` in two ways: its end-to-end recipe takes the
  `id` that `databricks serving-endpoints get <endpoint name>` returns, and its manual steps take the ID in the host
  name of the route-optimized URL. Use the `id`, as the recipe does.
- Append a pattern for the serving host to `plugins.ml_commons.trusted_connector_endpoints_regex` as in step 1:
  `^https://[a-z0-9-]+\.serving\.cloud\.databricks\.com/[0-9]+/serving-endpoints/.*$` on AWS,
  `^https://[a-z0-9-]+\.[a-z0-9-]+\.serving\.azuredatabricks\.net/[0-9]+/serving-endpoints/.*$` on Azure and
  `^https://[a-z0-9-]+\.serving\.gcp\.databricks\.com/[0-9]+/serving-endpoints/.*$` on Google Cloud.

## 3. Register and deploy the model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "databricks-serving-chat",
    "function_name": "remote",
    "description": "Databricks Model Serving chat model with OAuth M2M",
    "connector_id": "bK8tSo0BdQxvLz7KN3qe"
}
```

### Sample response

```json
{
  "task_id": "cU8uSo0BdQxvLz7KQ4rf",
  "status": "CREATED",
  "model_id": "ck8uSo0BdQxvLz7KQ4sZ"
}
```

## 4. Test the model

```json
POST /_plugins/_ml/models/ck8uSo0BdQxvLz7KQ4sZ/_predict
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

The reply is at `choices[0].message.content`. The response is shortened here:

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
            ],
            "usage": {
              "prompt_tokens": 19,
              "completion_tokens": 9,
              "total_tokens": 28
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Updates and the OAuth secret

An update that sends `auth_config` sends the whole block. Changing `token_url`, `grant_type`, `client_auth_method`,
`client_id`, `scope`, `audience` or `token_request_params`, or the credential keys the block refers to, requires
`credential` in the same update. To rotate the OAuth secret, create a new secret for the service principal, undeploy
the model, update the connector with the new secret in `credential`, deploy the model again, and then delete the old
secret in Databricks.

## Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed models fail the
  same way while it is off.
- `auth_config.token_url does not match plugins.ml_commons.trusted_oauth2_token_endpoints_regex`: no pattern of the
  setting matches the workspace token URL; append the pattern of your cloud as in step 1.
- `Trusted OAuth2 token endpoint pattern "..." uses a wildcard in the host position ...`: the pattern lets the host
  match other characters, for example with `.*` or an unescaped `.`; write it as in the table of step 1.
- `Failed to obtain OAuth2 token from <workspace host>: <reason>`: the token request failed. Check the client ID and
  the OAuth secret of the service principal. Databricks notes that M2M tokens come from the Databricks OAuth token
  endpoint, not from your identity provider or a cloud token endpoint
  ([OAuth M2M](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/auth/oauth-m2m)).
- The serving endpoint returns `401` with "Missing authorization details": the endpoint is route-optimized; see
  [Route-optimized endpoints](#route-optimized-endpoints).
- The serving endpoint returns `400` with "This is a route-optimized endpoint": the action uses the workspace URL;
  use the route-optimized URL as in [Route-optimized endpoints](#route-optimized-endpoints).
- The token request fails with `invalid_scope` for a route-optimized endpoint: set `scope` to `all-apis`, the only
  scope these tokens support.
- The token request with `authorization_details` is refused for a route-optimized endpoint although the client ID and
  secret are right: check the endpoint ID in `object_path`. If the `id` is refused, try the ID in the host name of the
  route-optimized URL, the other form that Databricks' page shows.
- The serving endpoint returns `401` or `403` otherwise: check that the service principal may query the endpoint.
  After a `401` the plugin sends the request once more with a new token, unless the token it used is younger than ten
  seconds.
