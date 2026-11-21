# Azure OpenAI connector blueprint for chat completion with Microsoft Entra ID

This blueprint authenticates to Azure OpenAI with a Microsoft Entra ID access token instead of an API key, so it also
works for resources whose key-based access is disabled. The connector declares an `auth_config` of type `oauth2` with
the client credentials grant. The plugin requests the token from Entra ID, keeps it in a cache on each node, requests a
new one before it expires, and sends `Authorization: Bearer <token>` with every request. The connector itself defines
no `Authorization` or `api-key` header.

Before you start, in Azure:

- Register an application in your Entra ID tenant and create a client secret for it, or upload a certificate to
  authenticate with instead (see [Certificate credentials](#certificate-credentials-private_key_jwt)).
- Assign the application's service principal the **Cognitive Services OpenAI User** role on the Azure OpenAI resource.
- Note the tenant ID, the application (client) ID, the client secret, the resource name, the deployment name and the
  API version you want to use.

## 1. Enable auth_config and trust the Azure OpenAI endpoint

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with `403` until it is
on. The Entra ID v2 token endpoint, `https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token`, is covered by the
default `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, with the tenant written as a GUID or as a domain
name such as `contoso.onmicrosoft.com`.

The Azure OpenAI host is not covered by the default `plugins.ml_commons.trusted_connector_endpoints_regex`. It is a
list setting and a `PUT` replaces it, so read it first and send every pattern back, otherwise the other connectors in
the cluster stop working. The response lists the patterns under `defaults` while the cluster uses the default list, and
under `persistent` or `transient` once it was changed:

```json
GET /_cluster/settings?include_defaults=true&filter_path=**.trusted_connector_endpoints_regex
```

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.connector.auth_enabled": true,
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY PATTERN FROM THE RESPONSE ABOVE",
            "^https://[a-z0-9-]+\\.openai\\.azure\\.com/.*$"
        ]
    }
}
```

## 2. Create the connector

Refer to [Azure OpenAI in Azure AI Foundry Models REST API reference - Chat completions](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#chat-completions).

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Azure OpenAI chat with Entra ID",
  "description": "Azure OpenAI chat completions, authenticated with an Entra ID access token",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://login.microsoftonline.com/<YOUR TENANT ID>/oauth2/v2.0/token",
    "client_auth_method": "client_secret_post",
    "client_id": "<YOUR APPLICATION (CLIENT) ID>",
    "client_secret": "${credential.client_secret}",
    "scope": "https://cognitiveservices.azure.com/.default"
  },
  "parameters": {
    "endpoint": "<YOUR RESOURCE NAME>.openai.azure.com",
    "deployment": "<YOUR DEPLOYMENT NAME>",
    "api_version": "<YOUR API VERSION>",
    "temperature": 0.7
  },
  "credential": {
    "client_secret": "<YOUR CLIENT SECRET>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/openai/deployments/${parameters.deployment}/chat/completions?api-version=${parameters.api_version}",
      "headers": {
        "Content-Type": "application/json"
      },
      "request_body": "{ \"messages\": ${parameters.messages}, \"temperature\": ${parameters.temperature} }"
    }
  ]
}
```

The client secret is stored encrypted in `credential`, and `auth_config.client_secret` refers to it with a
`${credential.<key>}` reference; a literal secret in `auth_config` is rejected. The create request is also rejected
when an action URL does not use `https`, when an action defines its own `Authorization` header, when
`client_config.skip_ssl_verification` is set, and when the token URL does not match
`plugins.ml_commons.trusted_oauth2_token_endpoints_regex`.

### Sample response

```json
{
  "connector_id": "EapnEY0BpYxvPx3Hxpmp"
}
```

## 3. Create a model group

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
    "description": "This is an example description"
}
```

### Sample response

```json
{
  "model_group_id": "YyvcbYsBjU568JRbdHqv",
  "status": "CREATED"
}
```

## 4. Register and deploy the model

```json
POST /_plugins/_ml/models/_register
{
    "name": "azure-openai-gpt-4o-entra",
    "function_name": "remote",
    "model_group_id": "YyvcbYsBjU568JRbdHqv",
    "description": "Azure OpenAI GPT-4o with Entra ID",
    "connector_id": "EapnEY0BpYxvPx3Hxpmp"
}
```

### Sample response

```json
{
  "task_id": "E6ppEY0BpYxvPx3HZZkL",
  "status": "CREATED",
  "model_id": "FKppEY0BpYxvPx3HZZk0"
}
```

When the registration task is completed, deploy the model:

```
POST /_plugins/_ml/models/FKppEY0BpYxvPx3HZZk0/_deploy
```

## 5. Test the model

```json
POST /_plugins/_ml/models/FKppEY0BpYxvPx3HZZk0/_predict
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
            "model": "gpt-4o",
            "choices": [
              {
                "finish_reason": "stop",
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today?"
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

## Certificate credentials (`private_key_jwt`)

Instead of a client secret, the application can authenticate with a certificate. The plugin signs a client assertion
(RFC 7523, OpenID Connect `private_key_jwt`) with the certificate's private key, which the connector credential holds,
and Entra ID verifies it with the certificate uploaded to the application. No secret is sent.

Create a key and a certificate for it, and upload `cert.pem` to the application under **Certificates & secrets** >
**Certificates**. The key must be an unencrypted PKCS#8 RSA key of at least 2048 bits (`-----BEGIN PRIVATE KEY-----`);
convert a `-----BEGIN RSA PRIVATE KEY-----` file with `openssl pkcs8 -topk8 -nocrypt -in key.pem -out key.p8.pem`. A
key generated with `-algorithm RSA-PSS` is not supported.

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out key.pem
openssl req -new -x509 -key key.pem -out cert.pem -days 365 -subj "/CN=opensearch-connector"
```

Entra ID finds the certificate by its thumbprint in the assertion header: `x5t#S256`, the base64url-encoded SHA-256
digest of the certificate without padding. The SHA-1 form `x5t` is also accepted in its place. The hexadecimal
thumbprint the Azure portal shows is not either of them and is rejected; convert it with the last command.

```sh
# x5t#S256
openssl x509 -in cert.pem -outform DER | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '='
# x5t
openssl x509 -in cert.pem -outform DER | openssl dgst -sha1 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '='
# x5t, from the hexadecimal thumbprint of the portal
echo <HEX THUMBPRINT> | xxd -r -p | openssl base64 -A | tr '+/' '-_' | tr -d '='
```

Create the connector of step 2 with this `auth_config` and `credential`; the other members stay as they are. In JSON,
the line breaks of the key are written as `\n`; `jq -Rs . key.pem` prints the file in that form.

```json
{
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://login.microsoftonline.com/<YOUR TENANT ID>/oauth2/v2.0/token",
    "client_auth_method": "private_key_jwt",
    "client_id": "<YOUR APPLICATION (CLIENT) ID>",
    "scope": "https://cognitiveservices.azure.com/.default",
    "jwt": {
      "signing_alg": "PS256",
      "header": {
        "x5t#S256": "<YOUR CERTIFICATE THUMBPRINT>"
      },
      "private_key": "${credential.private_key}"
    }
  },
  "credential": {
    "private_key": "-----BEGIN PRIVATE KEY-----\n<YOUR KEY>\n-----END PRIVATE KEY-----\n"
  }
}
```

`PS256` is the algorithm Entra ID documents for certificate credentials; `RS256`, the default, also works. The
assertion names the client ID as its issuer and subject, so `jwt.issuer` and `jwt.subject` are not set, and
`client_secret` is rejected beside `private_key_jwt`. Its audience is the token URL, as Entra ID expects. Each token
request carries a new assertion, valid for `jwt.lifetime_seconds` (default 60, at most 3600). The key is read when
the connector is created and whenever `credential` is updated, and one in another form, or of fewer than 2048 bits, is
rejected before it is stored.

## Updates and the client secret

An update that sends `auth_config` sends the whole block. Changing `token_url`, `grant_type`, `client_auth_method`,
`client_id`, `scope`, `audience`, `token_request_params`, `jwt.issuer`, `jwt.subject`, `jwt.audience` or
`jwt.additional_claims`, or the credential keys the block refers to, requires `credential` in the same update;
`token_response`, `jwt.signing_alg`, `jwt.lifetime_seconds` and `jwt.header` can change without it. To rotate the
client secret, undeploy the model, update the connector with the new secret in `credential`, and deploy the model
again. To rotate a certificate, upload the new one to the application first, then do the same with the new key in
`credential` and its thumbprint in `auth_config.jwt.header`, and remove the old certificate.

## Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed models fail the
  same way while it is off.
- `auth_config.token_url does not match plugins.ml_commons.trusted_oauth2_token_endpoints_regex`: the token URL is not
  an Entra ID v2 token endpoint, or the setting no longer holds its default pattern. The v1 endpoint,
  `https://login.microsoftonline.com/<tenant>/oauth2/token`, is not in the default list; to use it, append a pattern for
  it to the setting in the same way as in step 1, and replace `scope` with
  `"token_request_params": { "resource": "https://cognitiveservices.azure.com/" }`.
- `Failed to obtain OAuth2 token from login.microsoftonline.com: invalid_client (HTTP 401)`: Entra ID rejected the client
  ID or the client secret. With certificate credentials, the thumbprint in `jwt.header` is not that of a certificate
  of the application, or the key in `credential` is not that certificate's key.
- The model request returns `401` from Azure OpenAI: check the role assignment of the service principal on the
  resource. After a `401` the plugin sends the request once more with a new token, unless the token it used is younger
  than ten seconds.
