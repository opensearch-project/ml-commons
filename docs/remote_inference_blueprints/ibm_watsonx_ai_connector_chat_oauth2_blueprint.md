# IBM watsonx.ai connector blueprint for chat with an IBM Cloud IAM token

This blueprint connects to the watsonx.ai chat API with an IBM Cloud IAM access token. The connector declares an
`auth_config` of type `oauth2` with IBM's API key grant, `urn:ibm:params:oauth:grant-type:apikey`. The plugin exchanges
the API key for an access token at the IAM token endpoint, keeps the token in a cache on each node, requests a new one
before it expires, and sends `Authorization: Bearer <token>` with every request. The connector itself defines no
`Authorization` header, and nobody has to exchange or renew a token by hand.

Verified against the provider's documentation and official SDKs; not run against a live account.

Before you start, in IBM Cloud:

- Create an API key for a user or a service ID that has access to your watsonx.ai project or deployment space.
- Note the API key, the region your project runs in (see [Regions](#regions)), the project ID (or the space ID), the
  ID of the model you want to use, and the API version date.

## 1. Enable auth_config and trust the watsonx.ai endpoint

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with `403` until it is
on. The IAM token endpoint, `https://iam.cloud.ibm.com/identity/token`, is covered by the default
`plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, so that setting needs no change.

The watsonx.ai hosts are not covered by the default `plugins.ml_commons.trusted_connector_endpoints_regex`. It is a
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
            "^https://[a-z0-9-]+\\.ml\\.cloud\\.ibm\\.com/ml/v1/.*$"
        ]
    }
}
```

## 2. Create the connector

Refer to the [watsonx.ai API reference](https://cloud.ibm.com/apidocs/watsonx-ai) for the chat API and to the
[IAM Identity Services API reference](https://cloud.ibm.com/apidocs/iam-identity-token-api) for the API key grant.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "IBM watsonx.ai chat",
  "description": "watsonx.ai text chat, authenticated with an IBM Cloud IAM access token",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://iam.cloud.ibm.com/identity/token",
    "grant_type": "urn:ibm:params:oauth:grant-type:apikey",
    "client_auth_method": "none",
    "token_request_params": {
      "apikey": "${credential.apikey}"
    }
  },
  "parameters": {
    "region": "us-south",
    "api_version": "<YYYY-MM-DD>",
    "model_id": "<YOUR MODEL ID>",
    "project_id": "<YOUR PROJECT ID>"
  },
  "credential": {
    "apikey": "<YOUR IBM CLOUD API KEY>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.region}.ml.cloud.ibm.com/ml/v1/text/chat?version=${parameters.api_version}",
      "headers": {
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      "request_body": "{ \"model_id\": \"${parameters.model_id}\", \"project_id\": \"${parameters.project_id}\", \"messages\": ${parameters.messages} }"
    }
  ]
}
```

The plugin sends the token request that IBM documents for an API key:
`grant_type=urn:ibm:params:oauth:grant-type:apikey` and `apikey=<your API key>` in the form body
([IAM Identity Services API](https://cloud.ibm.com/apidocs/iam-identity-token-api)). IBM's example request sends no
`Authorization` header, and IBM's Go SDK sends one to the IAM token service only when a client ID and secret are
configured, and documents both as optional
([IBM Go SDK core, Authentication](https://github.com/IBM/go-sdk-core/blob/main/Authentication.md)). That is why
`client_auth_method` is `none` and the block sets neither `client_id` nor `client_secret`; with `none`, a
`client_secret` is rejected.

`urn:ibm:params:oauth:grant-type:apikey` is an extension grant, an absolute URI as RFC 6749 section 4.5 defines it.
It is the one grant `plugins.ml_commons.connector.oauth2_extension_grants` admits by default; a connector for another
provider's extension grant needs that grant added to the setting first, and a grant removed from the setting fails its
connectors at their next request.
With an extension grant, `client_auth_method` must be `none` and `token_request_params` must hold at least one
`${credential.<key>}` reference, here the API key. The API key is stored encrypted in `credential`, and
`token_request_params.apikey` refers to it. Always use the reference: `token_request_params` values are stored as they
are written, so a literal key there would not be encrypted. The names `grant_type`, `client_id`, `client_secret`,
`assertion`, `scope`, `audience` and those starting with `client_assertion` cannot be set through
`token_request_params`.

The token is read from `access_token` and its lifetime from `expires_in`, which the IAM token response carries
(3600 seconds in IBM's example,
[IAM Identity Services API](https://cloud.ibm.com/apidocs/iam-identity-token-api)), so `token_response` needs no
setting. A cached token never outlives `plugins.ml_commons.connector.oauth2_token_cache_max_seconds` (default 3600).

`region`, `api_version`, `model_id` and `project_id` are stored on the connector. The host of the action URL comes from
`region`, so it must be stored on the connector: request parameters cannot change the scheme, host or port of an action
URL. `api_version` is the version date of the API, of the form `YYYY-MM-DD`
([watsonx.ai API reference](https://cloud.ibm.com/apidocs/watsonx-ai)). To use a deployment space instead of a
project, replace `project_id` with `space_id` in `parameters` and in `request_body`; the API takes one of the two. The
create request is also rejected when an action URL does not use `https`, when an action defines its own `Authorization`
header, when `client_config.skip_ssl_verification` is set, and when the token URL does not match
`plugins.ml_commons.trusted_oauth2_token_endpoints_regex`.

### Sample response

```json
{
  "connector_id": "pV3mQo0BdQxvLz7KDy2a"
}
```

### Regions

The watsonx.ai regions on IBM Cloud and their hosts, from the endpoint URLs of the
[watsonx.ai API reference](https://cloud.ibm.com/apidocs/watsonx-ai) and the
[watsonx.ai Python SDK setup page](https://ibm.github.io/watsonx-ai-python-sdk/v1.9.1/setup_cloud.html):

| Location | `region` | Model host |
|---|---|---|
| Dallas | `us-south` | `us-south.ml.cloud.ibm.com` |
| Frankfurt | `eu-de` | `eu-de.ml.cloud.ibm.com` |
| London | `eu-gb` | `eu-gb.ml.cloud.ibm.com` |
| Sydney | `au-syd` | `au-syd.ml.cloud.ibm.com` |
| Tokyo | `jp-tok` | `jp-tok.ml.cloud.ibm.com` |
| Toronto | `ca-tor` | `ca-tor.ml.cloud.ibm.com` |

The same pages also list watsonx.ai regions hosted on AWS, Mumbai `ap-south-1.aws.wxai.ibm.com` and, on the SDK page,
US-EAST `us-east-1.aws.wxai.ibm.com`. This blueprint does not cover them: their hosts do not match the pattern of
step 1, and their API keys are not exchanged at `iam.cloud.ibm.com`. The SDK page lists
`account-iam.platform.saas.ibm.com` as their token service, and IBM's Python SDK requests their tokens from
`https://account-iam.platform.saas.ibm.com/api/2.0/apikeys/token` with a JSON body, not with the API key grant shown
here, and reads the token from a `token` member
([ibm-watsonx-ai](https://pypi.org/project/ibm-watsonx-ai/) 1.9.1, `ibm_watsonx_ai/utils/auth/aws_auth.py`).

## 3. Register and deploy the model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "ibm-watsonx-ai-chat",
    "function_name": "remote",
    "description": "IBM watsonx.ai chat model",
    "connector_id": "pV3mQo0BdQxvLz7KDy2a"
}
```

### Sample response

```json
{
  "task_id": "qF3nQo0BdQxvLz7KHi0b",
  "status": "CREATED",
  "model_id": "qV3nQo0BdQxvLz7KHi1C"
}
```

## 4. Test the model

```json
POST /_plugins/_ml/models/qV3nQo0BdQxvLz7KHi1C/_predict
{
  "parameters": {
    "messages": [
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
            "choices": [
              {
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

## Updates and the API key

An update that sends `auth_config` sends the whole block. Changing `token_url`, `grant_type`, `client_auth_method`,
`client_id`, `scope`, `audience` or `token_request_params`, or the credential keys the block refers to, requires
`credential` in the same update. To rotate the API key, create a new key in IBM Cloud, undeploy the model, update the
connector with the new key in `credential`, deploy the model again, and then delete the old key in IBM Cloud.

## Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed models fail the
  same way while it is off.
- `auth_config.client_auth_method must be none with an extension grant`: set `client_auth_method` to `none`.
- `auth_config.token_request_params must carry a ${credential.<key>} value with an extension grant`: `apikey` is
  missing from `token_request_params` or holds the key itself; write it as `${credential.apikey}` and put the key in
  `credential`.
- `auth_config references credential key [apikey], which the connector credential does not contain`: `credential` has
  no `apikey` member, or it has another name than the reference.
- `auth_config.client_secret must not be set when client_auth_method is none`: remove `client_secret`; the API key
  grant does not use one.
- `auth_config.grant_type must be client_credentials, urn:ietf:params:oauth:grant-type:jwt-bearer or an extension grant URI`:
  the grant type is not a URI, for example `apikey` alone; write `urn:ibm:params:oauth:grant-type:apikey`.
- `Failed to obtain OAuth2 token from iam.cloud.ibm.com: <reason>`: IAM refused the token request. Check that the API
  key in `credential` is complete and has not been deleted.
- `The scheme, host and port of a connector action URL come from the connector's own parameters; request parameters
  cannot change them. ...`: the predict request sets `region`. Set it on the connector instead.
- The model request returns `401` or `403` from watsonx.ai: check that the user or service ID that owns the API key
  has access to the project or space. After a `401` the plugin sends the request once more with a new token, unless
  the token it used is younger than ten seconds.
