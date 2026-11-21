# Salesforce Models API connector blueprint for chat generations with OAuth 2.0

This blueprint connects to the chat generations API of the Salesforce Models API on `api.salesforce.com`. The
connector declares an `auth_config` of type `oauth2` with the client credentials grant on your org's My Domain token
endpoint; a [JWT bearer variant](#variant-jwt-bearer-flow) that stores no client secret follows. The plugin requests
the token from Salesforce, keeps it in a cache on each node, requests a new one before it expires, and sends
`Authorization: Bearer <token>` with every request. The connector itself defines no `Authorization` header, and nobody
has to request a token by hand.

Verified against the provider's documentation and API specification; not run against a live account.

Before you start, in Salesforce, configure an external client app for the integration with the settings below. They
follow an earlier version of Salesforce's
[Models API guide](https://developer.salesforce.com/docs/ai/agentforce/guide/access-models-api-with-rest.html), which
now describes the app setup in
[Authenticate to the Models API](https://developer.salesforce.com/docs/ai/agentforce/guide/models-api-authentication.html);
check them against that page.

- In the app's OAuth settings, add the OAuth scopes "Manage user data via APIs (`api`)", "Perform requests at any time
  (`refresh_token`, `offline_access`)" and "Access the Salesforce API Platform (`sfap_api`)". `sfap_api` gives access
  to the Salesforce API Platform, including the API services on `api.salesforce.com`
  ([OAuth tokens and scopes](https://help.salesforce.com/s/articleView?id=xcloud.remoteaccess_oauth_tokens_scopes.htm&type=5)).
- In the OAuth settings, select "Enable Client Credentials Flow"
  ([Configure a client credentials flow](https://help.salesforce.com/s/articleView?id=xcloud.configure_client_credentials_flow_for_external_client_apps.htm&type=5)),
  and in the Security section, select "Issue JSON Web Token (JWT)-based access tokens for named users"
  ([JWT-based access tokens in external client apps](https://help.salesforce.com/s/articleView?id=xcloud.jwt_eca_configuration.htm&type=5)).
  The Models API takes a JWT-based access token from your org
  ([Models API specification](https://github.com/salesforce/einstein-platform/blob/main/api-specs/models/models.yaml)),
  and an app can issue such tokens for all of its authorization flows
  ([JWT-based access tokens](https://help.salesforce.com/s/articleView?id=xcloud.jwt_access_tokens.htm&type=5)).
- In the app's policies, select "Enable Client Credentials Flow" and enter the username of the integration user that
  the flow runs as
  ([Configure client credential flow policies](https://help.salesforce.com/s/articleView?id=xcloud.policies_configure_client_credentials_flow_for_external_client_apps.htm&type=5)),
  and set the timeout of the JWT-based access tokens for named users. That earlier version of the guide gave 30
  minutes as the default of this timeout.
- Note the consumer key, the consumer secret and your My Domain host, the Current My Domain URL in Setup under
  My Domain.

## 1. Enable auth_config and trust the Models API endpoint

`plugins.ml_commons.connector.auth_enabled` is off by default, and creating the connector fails with `403` until it is
on. The client credentials flow runs on your org's My Domain; Salesforce does not support it on
`login.salesforce.com` or `test.salesforce.com`
([client credentials flow](https://help.salesforce.com/s/articleView?id=xcloud.remoteaccess_oauth_client_credentials_flow.htm&type=5)).
A production My Domain token endpoint, `https://<your My Domain>.my.salesforce.com/services/oauth2/token`, is covered
by the default `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`, so for a production org that setting needs
no change.

The Models API host is not covered by the default `plugins.ml_commons.trusted_connector_endpoints_regex`. It is a list
setting and a `PUT` replaces it, so read it first and send every pattern back, otherwise the other connectors in the
cluster stop working. The response lists the patterns under `defaults` while the cluster uses the default list, and
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
            "^https://api\\.salesforce\\.com/einstein/platform/v1/.*$"
        ]
    }
}
```

The default token endpoint pattern, `^https://[a-z0-9-]+\.my\.salesforce\.com/services/oauth2/token$`, matches
production My Domains only. The My Domain login host of a sandbox is
`MyDomainName--SandboxName.sandbox.my.salesforce.com`
([Partitioned domains for sandboxes](https://help.salesforce.com/s/articleView?id=xcloud.domain_name_partitioned_domains_sandbox.htm&type=5)),
and that of a Developer Edition org with partitioned domains is `MyDomainName.develop.my.salesforce.com`
([Partitioned domains for Developer Edition orgs](https://help.salesforce.com/s/articleView?id=xcloud.domain_name_partitioned_domains_de.htm&type=5)).
For such an org, append the pattern for its token URL to `plugins.ml_commons.trusted_oauth2_token_endpoints_regex` in
the same way; keep the line you need:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_oauth2_token_endpoints_regex": [
            "REPLACE THIS LINE WITH EVERY trusted_oauth2_token_endpoints_regex PATTERN FROM THE RESPONSE ABOVE",
            "^https://[a-z0-9-]+\\.sandbox\\.my\\.salesforce\\.com/services/oauth2/token$",
            "^https://[a-z0-9-]+\\.develop\\.my\\.salesforce\\.com/services/oauth2/token$"
        ]
    }
}
```

A token endpoint pattern must match the whole token URL in its canonical form: `https`, the host in lower case, no
`:443`, and the path as written. Write the host with literal labels or `[a-z0-9-]+`; a pattern that could let the host
match other characters, such as `.*`, is rejected.

## 2. Create the connector

Refer to the [Models API specification](https://github.com/salesforce/einstein-platform/blob/main/api-specs/models/models.yaml)
and to the [client credentials flow](https://help.salesforce.com/s/articleView?id=xcloud.remoteaccess_oauth_client_credentials_flow.htm&type=5).

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Salesforce Models API chat",
  "description": "Salesforce Models API chat generations, client credentials flow on My Domain",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://<YOUR MY DOMAIN>.my.salesforce.com/services/oauth2/token",
    "client_auth_method": "client_secret_post",
    "client_id": "<YOUR CONSUMER KEY>",
    "client_secret": "${credential.client_secret}",
    "token_response": {
      "default_ttl_seconds": 600
    }
  },
  "parameters": {
    "model": "<YOUR MODEL API NAME>"
  },
  "credential": {
    "client_secret": "<YOUR CONSUMER SECRET>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.salesforce.com/einstein/platform/v1/models/${parameters.model}/chat-generations",
      "headers": {
        "Content-Type": "application/json",
        "x-sfdc-app-context": "EinsteinGPT",
        "x-client-feature-id": "ai-platform-models-connected-app"
      },
      "request_body": "{ \"messages\": ${parameters.messages} }"
    }
  ]
}
```

The consumer secret is stored encrypted in `credential`, and `auth_config.client_secret` refers to it with a
`${credential.<key>}` reference; a literal secret in `auth_config` is rejected. The create request is also rejected
when an action URL does not use `https`, when an action defines its own `Authorization` header, when
`client_config.skip_ssl_verification` is set, and when the token URL does not match
`plugins.ml_commons.trusted_oauth2_token_endpoints_regex`.

`client_secret_post` sends `grant_type=client_credentials`, `client_id` and `client_secret` in the form body, as in
Salesforce's example of the flow; Salesforce also takes the consumer key and secret in a Basic `Authorization`
header, so `client_secret_basic` works too. The token request carries no `scope`: Salesforce does not support scopes
on the token endpoint for this flow and takes them from the app.

Salesforce's token response for this flow has no `expires_in`, so `token_response.default_ttl_seconds` sets how long
the plugin uses a token: 600 seconds here instead of the default 300. Keep it at or below the timeout of the JWT-based
access token, which is the integration user's session timeout or the app's own token timeout
([JWT-based access tokens in external client apps](https://help.salesforce.com/s/articleView?id=xcloud.jwt_eca_configuration.htm&type=5));
the setting allows at most 3600. A cached token never outlives
`plugins.ml_commons.connector.oauth2_token_cache_max_seconds` (default 3600).

The Models API requires both `x-` headers. The specification reserves both for future use and asks for the values
`EinsteinGPT` in `x-sfdc-app-context` and `ai-platform-models-connected-app` in `x-client-feature-id` for now.

`model` is the API name of a model, such as `sfdc_ai__DefaultOpenAIGPT4OmniMini` for OpenAI GPT-4o mini, the example
in Salesforce's Models API guide. The API names of the standard models are listed in
[Supported Models](https://developer.salesforce.com/docs/ai/agentforce/guide/supported-models.html), and the API name of
a model configured in your org is shown in its details on the Generative tab of the Models page
([Model API names](https://developer.salesforce.com/docs/ai/agentforce/guide/models-api-names.html)). The host of the
action URL is fixed, and request parameters cannot change the scheme, host or port of an action URL.

### Sample response

```json
{
  "connector_id": "fD6rUo0BdQxvLz7Ka7wj"
}
```

### Variant: JWT bearer flow

Salesforce documents that an app can issue JWT-based access tokens for all of its authorization flows, but it shows no
Models API example with the JWT bearer flow, so test this variant before you rely on it.

With the JWT bearer flow, the plugin signs a JWT with a private key whose certificate is registered on the app, and
Salesforce returns an access token for the user the JWT names
([OAuth 2.0 JWT bearer flow](https://help.salesforce.com/s/articleView?id=xcloud.remoteaccess_oauth_jwt_flow.htm&type=5)).
No client secret is stored. In Salesforce, besides the scope and the JWT-based access tokens above:

- Register the certificate of the key pair on the app: in its OAuth settings, select "Enable JWT Bearer Flow" and
  upload the X.509 certificate
  ([Configure a JWT bearer flow](https://help.salesforce.com/s/articleView?id=xcloud.configure_oauth_jwt_flow_external_client_apps.htm&type=5)).
  The private key goes into `credential`.
- Pre-approve the integration user for the app: Salesforce issues a token only for a valid JWT and an app that has
  prior approval. In the app's policies, set Permitted Users to "Admin approved users are pre-authorized" and give
  the integration user's profile or a permission set of the user access to the app
  ([Preauthorize user app access](https://help.salesforce.com/s/articleView?id=xcloud.preauth_user_app_access_through_eca.htm&type=5)).

The connector of step 2, with this `auth_config` and `credential`:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Salesforce Models API chat (JWT bearer)",
  "description": "Salesforce Models API chat generations, JWT bearer flow",
  "version": 1,
  "protocol": "http",
  "auth_config": {
    "type": "oauth2",
    "token_url": "https://login.salesforce.com/services/oauth2/token",
    "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
    "client_auth_method": "none",
    "jwt": {
      "issuer": "<YOUR CONSUMER KEY>",
      "subject": "<USERNAME OF THE INTEGRATION USER>",
      "audience": "https://login.salesforce.com",
      "signing_alg": "RS256",
      "lifetime_seconds": 180,
      "private_key": "${credential.private_key}"
    },
    "token_response": {
      "default_ttl_seconds": 600
    }
  },
  "parameters": {
    "model": "<YOUR MODEL API NAME>"
  },
  "credential": {
    "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.salesforce.com/einstein/platform/v1/models/${parameters.model}/chat-generations",
      "headers": {
        "Content-Type": "application/json",
        "x-sfdc-app-context": "EinsteinGPT",
        "x-client-feature-id": "ai-platform-models-connected-app"
      },
      "request_body": "{ \"messages\": ${parameters.messages} }"
    }
  ]
}
```

- `issuer` is the consumer key, the OAuth client ID of the app on which the certificate is registered. `subject` is
  the username of the integration user, and `audience` is `https://login.salesforce.com`, the authorization server's
  URL.
- Salesforce requires the JWT to be signed with RSA SHA-256, which is `RS256`. The key must be an unencrypted PKCS#8
  PEM (`-----BEGIN PRIVATE KEY-----`),
  pasted with its `\n` escapes. `auth_config.jwt.private_key` refers to it with a `${credential.<key>}` reference; a
  literal key in `auth_config` is rejected.
- `lifetime_seconds: 180` keeps the assertion short; Salesforce allows a 3-minute buffer for clock skew on `exp`. The
  plugin also sets `iat` and a new random `jti` in every assertion; Salesforce rejects a `jti` it has seen before, so a
  fresh one each time is what it expects.
- This flow's token response has no `expires_in` either, so `default_ttl_seconds` applies as in step 2.
- For a sandbox, set both `token_url` and `audience` to `https://test.salesforce.com`, the first with the path
  `/services/oauth2/token`. That token URL is not in the defaults: append
  `^https://test\.salesforce\.com/services/oauth2/token$` to `plugins.ml_commons.trusted_oauth2_token_endpoints_regex`
  as in step 1, with each `\` written twice inside the JSON string.

## 3. Register and deploy the model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "salesforce-models-api-chat",
    "function_name": "remote",
    "description": "Salesforce Models API chat model",
    "connector_id": "fD6rUo0BdQxvLz7Ka7wj"
}
```

### Sample response

```json
{
  "task_id": "fT6sUo0BdQxvLz7Kc8xk",
  "status": "CREATED",
  "model_id": "fj6sUo0BdQxvLz7Kc8yl"
}
```

## 4. Test the model

```json
POST /_plugins/_ml/models/fj6sUo0BdQxvLz7Kc8yl/_predict
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

The reply is at `generationDetails.generations[0].content`. The response is shortened here:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "generationDetails": {
              "generations": [
                {
                  "role": "assistant",
                  "content": "Hello! How can I help you today?"
                }
              ]
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Updates and the credentials

An update that sends `auth_config` sends the whole block. Changing `token_url`, `grant_type`, `client_auth_method`,
`client_id`, `scope`, `audience`, `token_request_params`, `jwt.issuer`, `jwt.subject`, `jwt.audience` or
`jwt.additional_claims`, or the credential keys the block refers to, requires `credential` in the same update. To
rotate the consumer secret, undeploy the model, update the connector with the new secret in `credential`, and deploy
the model again. To replace the key pair of the JWT bearer variant, register the new certificate on the app first,
then undeploy the model, update the connector with the new private key in `credential`, and deploy the model again.

## Troubleshooting

- `403` naming `plugins.ml_commons.connector.auth_enabled`: the setting is off. Requests of deployed models fail the
  same way while it is off.
- `auth_config.token_url does not match plugins.ml_commons.trusted_oauth2_token_endpoints_regex`: for the client
  credentials flow, `token_url` must be the token URL of your My Domain; for a sandbox or a Developer Edition org,
  append its pattern as in step 1. For the JWT bearer variant on a sandbox, append the `test.salesforce.com` pattern.
- `Failed to obtain OAuth2 token from <host>: <reason>`: Salesforce refused the token request. For the client
  credentials flow, check the consumer key and secret, and that the flow is enabled on the app with an integration
  user; an `invalid_grant` error with "request not supported on this domain" means that `token_url` does not use the
  Current My Domain URL shown in Setup under My Domain. For the JWT bearer variant, check `issuer`, `subject` and
  `audience`, that the certificate of the key is registered on the app, that the user is pre-approved, and that the
  node clocks are correct.
- `auth_config.jwt.private_key must reference an unencrypted PKCS#8 PEM key (-----BEGIN PRIVATE KEY-----)`: the key is
  in another format, for example a `-----BEGIN RSA PRIVATE KEY-----` key or one protected by a passphrase.
- The Models API returns `401` or `404`: check that the app issues JWT-based access tokens and has the scopes listed
  at the start, among them `sfap_api`. If the `401` comes near the end of each `default_ttl_seconds` period, the
  tokens expire earlier than the setting assumes; lower it. After a `401` the plugin sends the request once more with
  a new token, unless the token it used is younger than ten seconds.
- The Models API reports a missing header: both `x-sfdc-app-context` and `x-client-feature-id` must be set on the
  action.
