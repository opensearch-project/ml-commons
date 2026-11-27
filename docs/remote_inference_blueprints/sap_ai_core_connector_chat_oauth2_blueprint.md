# SAP AI Core connector blueprint for chat completion in the generative AI hub

This blueprint connects to an OpenAI-family chat model deployed in the generative AI hub of SAP AI Core. The connector
declares an `auth_config` of type `oauth2` with the client credentials grant and the client ID and secret of an SAP AI
Core service key. The plugin requests the token from the authentication URL of the service key, keeps it in a cache on
each node, requests a new one before it expires, and sends `Authorization: Bearer <token>` with every request. The
connector itself defines no `Authorization` header, and nobody has to request a token by hand.

Verified against the provider's documentation and official SDKs; not run against a live account.

Before you start, in SAP AI Core:

- Create a service key for your SAP AI Core instance that uses a client secret
  ([Create a service key](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/create-a-service-key-7323ff4.md)).
  From the service key, note `clientid`, `clientsecret`, `url` and `serviceurls.AI_API_URL`.
- Deploy an OpenAI-family chat model in the generative AI hub and wait until the deployment is running
  ([Create a deployment](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/generativeAI/create-a-deployment-b32e7a8.md)).
  Note the deployment ID, which is part of the `deploymentUrl` of the running deployment, and the resource group you
  deployed it in.

A service key of the X.509 type is not supported: its client authenticates with a certificate over mutual TLS when it
requests a token, and the plugin's token request presents no client certificate. Connector actions can use mutual TLS,
but the token request cannot. Use a service key with a client secret.

## 1. Enable auth_config and trust both endpoints

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with `403` until it is
on. The connector needs two list settings to cover its endpoints, and neither default covers SAP AI Core:

- `plugins.ml_commons.trusted_connector_endpoints_regex` for the model endpoint, the host of `AI_API_URL`, such as
  `api.ai.prod.us-east-1.aws.ml.hana.ondemand.com`.
- `plugins.ml_commons.trusted_oauth2_token_endpoints_regex` for the token endpoint, `<url>/oauth/token`, where `<url>`
  is the `url` of the service key, such as `https://tutorial.authentication.us10.hana.ondemand.com`.

Both examples are from SAP's [AI Core setup tutorial](https://developers.sap.com/tutorials/ai-core-setup.html). A
`PUT` of a list setting replaces it, so read both first and send every pattern back, otherwise the other connectors in
the cluster stop working. The response lists the patterns under `defaults` while a setting has its default value, and
under `persistent` or `transient` once it was changed:

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
            "^https://api\\.ai\\.([a-z0-9-]+\\.)*ml\\.hana\\.ondemand\\.com/v2/inference/deployments/.*$"
        ],
        "plugins.ml_commons.trusted_oauth2_token_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY trusted_oauth2_token_endpoints_regex PATTERN FROM THE RESPONSE ABOVE",
            "^https://[a-z0-9-]+\\.authentication\\.[a-z0-9-]+\\.hana\\.ondemand\\.com/oauth/token$"
        ]
    }
}
```

The patterns follow the host forms of SAP's examples, which also include `https://api.ai.ml.hana.ondemand.com` and
`https://tutorial.authentication.sap.hana.ondemand.com`. Check that they match the `url` and `AI_API_URL` of your
service key; if either is on another domain, write the pattern for that host instead. A token endpoint pattern must
match the whole token URL in its canonical form: `https`, the host in lower case, no `:443`, and the path as written.
Write the host with literal labels or `[a-z0-9-]+`; a pattern that could let the host match other characters, such as
`.*`, is rejected.

## 2. Create the connector

Refer to [Use a service key](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/use-a-service-key-3a97465.md)
and [OpenAI models in the generative AI hub](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/generativeAI/openai-490f55d.md).

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "SAP AI Core chat",
  "description": "SAP AI Core generative AI hub deployment with the OpenAI chat API, client credentials",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://<YOUR SUBDOMAIN>.authentication.<YOUR REGION>.hana.ondemand.com/oauth/token",
    "client_auth_method": "client_secret_post",
    "client_id": "<clientid FROM YOUR SERVICE KEY>",
    "client_secret": "${credential.client_secret}"
  },
  "parameters": {
    "ai_api_host": "<HOST OF AI_API_URL FROM YOUR SERVICE KEY>",
    "deployment_id": "<YOUR DEPLOYMENT ID>",
    "api_version": "2023-05-15",
    "max_tokens": 256
  },
  "credential": {
    "client_secret": "<clientsecret FROM YOUR SERVICE KEY>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.ai_api_host}/v2/inference/deployments/${parameters.deployment_id}/chat/completions?api-version=${parameters.api_version}",
      "headers": {
        "Content-Type": "application/json",
        "AI-Resource-Group": "<YOUR RESOURCE GROUP, FOR EXAMPLE default>"
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

`token_url` is the `url` of the service key followed by `/oauth/token`. SAP's guide says to write these URLs without
a trailing slash, so the result has a single `/` before `oauth`. `client_secret_post` sends
`grant_type=client_credentials`, `client_id` and `client_secret` in the form body, as SAP's curl examples do
([Use a service key](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/use-a-service-key-3a97465.md),
[Get an auth token](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/generativeAI/get-an-auth-token-0808d42.md)).
The PowerShell example of the first page sends the client ID and secret in a Basic `Authorization` header instead,
which is `client_secret_basic`. The client ID of a service key contains characters such as `!` and `|`; copy it as it
is.

The token is read from `access_token` and its lifetime from `expires_in`, which SAP's token response carries (7199
seconds in SAP's example,
[Get an auth token](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/generativeAI/get-an-auth-token-0808d42.md)),
so `token_response` needs no setting. A cached token never outlives
`plugins.ml_commons.connector.oauth2_token_cache_max_seconds` (default 3600).

Every request to a deployment carries the `AI-Resource-Group` header, set to the resource group of the deployment
([Consume models](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/generativeAI/consume-models-bf0373b.md));
SAP asks you to use the same resource group for all of your generative AI activities
([Create a deployment](https://github.com/SAP-docs/sap-artificial-intelligence/blob/main/docs/sap-ai-core/generativeAI/create-a-deployment-b32e7a8.md)).
It is written in the header as a literal value.

`ai_api_host`, `deployment_id`, `api_version` and `max_tokens` are stored on the connector. The host of the action URL
comes from `ai_api_host`, so it must be stored on the connector: request parameters cannot change the scheme, host or
port of an action URL. The action URL is the `deploymentUrl` of the deployment, which has the form
`<AI_API_URL>/v2/inference/deployments/<deployment id>`, followed by `/chat/completions`; SAP's
[AI SDK for JavaScript](https://github.com/SAP/ai-sdk-js/blob/main/packages/foundation-models/src/azure-openai/azure-openai-chat-client.ts)
builds the same URL. `2023-05-15` is the API version of SAP's chat example; for o1 and o3-mini, SAP's page uses
`2024-12-01-preview` and sends no `max_tokens`, so remove `max_tokens` from `request_body` for those models.

### Sample response

```json
{
  "connector_id": "sA2pTo0BdQxvLz7KV5tg"
}
```

## 3. Register and deploy the model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "sap-ai-core-chat",
    "function_name": "remote",
    "description": "OpenAI-family chat model in SAP AI Core",
    "connector_id": "sA2pTo0BdQxvLz7KV5tg"
}
```

### Sample response

```json
{
  "task_id": "sQ2qTo0BdQxvLz7KX6uh",
  "status": "CREATED",
  "model_id": "sg2qTo0BdQxvLz7KX6vi"
}
```

## 4. Test the model

```json
POST /_plugins/_ml/models/sg2qTo0BdQxvLz7KX6vi/_predict
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
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Updates and the client secret

An update that sends `auth_config` sends the whole block. Changing `token_url`, `grant_type`, `client_auth_method`,
`client_id`, `scope`, `audience` or `token_request_params`, or the credential keys the block refers to, requires
`credential` in the same update. To rotate the client secret, create a new service key, undeploy the model, update the
connector with the new key's `clientid` in `auth_config.client_id` and its `clientsecret` in `credential`, deploy the
model again, and then delete the old service key.

## Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed models fail the
  same way while it is off.
- `auth_config.token_url does not match plugins.ml_commons.trusted_oauth2_token_endpoints_regex`: no pattern of the
  setting matches the token URL. Check the pattern of step 1 against the `url` of your service key, and check that
  `token_url` has no `//` before `oauth`.
- `Failed to obtain OAuth2 token from <subdomain>.authentication.<region>.hana.ondemand.com: <reason>`: the token
  request failed. Check that `client_id` and the secret in `credential` are the `clientid` and `clientsecret` of the
  same service key, copied as they are.
- The model request fails at SAP AI Core: check that the `AI-Resource-Group` header names the resource group of the
  deployment, that the deployment is running, and that `deployment_id` and `ai_api_host` match its `deploymentUrl`.
  After a `401` the plugin sends the request once more with a new token, unless the token it used is younger than ten
  seconds.
