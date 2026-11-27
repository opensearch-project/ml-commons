# GCP Vertex AI Batch Inference Connector Blueprint

This blueprint runs offline batch inference against Vertex AI `batchPredictionJobs` using the
`google_cloud` connector protocol. OAuth2 tokens are minted and refreshed automatically; no
`Authorization` header is added by you.

It covers three operations:
- submit a batch prediction job
- poll a job's status
- cancel a running job

Only the `batch_predict` action is declared. ml-commons derives the status and cancel calls from
it, so `batch_predict_status` and `cancel_batch_predict` are not declared separately (see step 2).

Batch input and output are configured in the request body via GCS or BigQuery locations. See
the [batch inference from Cloud Storage docs](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/batch-inference/new-job-from-cloud-storage).
Google has since renamed Vertex AI to Gemini Enterprise Agent Platform; the API host, the
`batchPredictionJobs` resource and the request shape are unchanged.

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

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "GCP Vertex AI Connector: batch inference",
    "description": "Vertex AI batchPredictionJobs connector",
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
    "batch_job_status": {
        "field_name": "state",
        "mapping": {
            "JOB_STATE_SUCCEEDED": "COMPLETED",
            "JOB_STATE_FAILED": "FAILED",
            "JOB_STATE_EXPIRED": "EXPIRED",
            "JOB_STATE_CANCELLING": "CANCELLING",
            "JOB_STATE_CANCELLED": "CANCELLED"
        }
    },
    "actions": [
        {
            "action_type": "batch_predict",
            "method": "POST",
            "url": "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/${parameters.project_id}/locations/${parameters.location}/batchPredictionJobs",
            "headers": {
                "Content-Type": "application/json"
            },
            "request_body": "{\"displayName\":\"${parameters.job_name}\",\"model\":\"publishers/google/models/${parameters.model}\",\"inputConfig\":{\"instancesFormat\":\"jsonl\",\"gcsSource\":{\"uris\":[\"${parameters.input_uri}\"]}},\"outputConfig\":{\"predictionsFormat\":\"jsonl\",\"gcsDestination\":{\"outputUriPrefix\":\"${parameters.output_uri}\"}}}"
        }
    ]
}
```

Only the `batch_predict` action is defined. ml-commons derives the status and cancel calls
from it automatically (using the Vertex batch job's resource `name` returned at submit time),
so you do **not** declare separate `batch_predict_status` / `cancel_batch_predict` actions.
Declaring them would leave an unresolved `${parameters.job_id}` in the derived URL.

The `batch_job_status` block tells ml-commons how to map Vertex's job status onto an ML task
state. Vertex reports status in the flat `state` field (for example `JOB_STATE_SUCCEEDED`),
which is matched exactly against `mapping` to set the terminal task state. When a connector
declares `batch_job_status`, this mapping is used exclusively and the cluster-wide
`plugins.ml_commons.remote_job.status_*` settings are not consulted for that connector.

For ADC / Workload Identity mode, set `"auth_mode": "adc"` in `parameters` and leave
`credential` empty (`{}`).

**ADC mode.** ADC uses the application default credentials present on the OpenSearch nodes and is
expressed only with the `protocol: google_cloud` form and `auth_mode: adc`; it has no `auth_config`
form.

## 3. Register and deploy the model

Register and deploy the model as in the
[Gemini blueprint](./gcp_vertexai_gemini_blueprint.md) (steps 3–5), using this connector.

## 4. Submit a batch prediction job

```json
POST /_plugins/_ml/models/<MODEL_ID>/_batch_predict
{
    "parameters": {
        "job_name": "vertex-batch-2026-07-21",
        "input_uri": "gs://<YOUR_BUCKET>/batch_input.jsonl",
        "output_uri": "gs://<YOUR_BUCKET>/output/"
    }
}
```

Submitting returns a `task_id`. Status and cancel are performed against that task via the
ML task APIs below (not a `_predict/...` route).

## 5. Check job status

```json
GET /_plugins/_ml/tasks/<TASK_ID>
```

The response includes the refreshed remote job state under `remote_job.state` (e.g.
`JOB_STATE_PENDING`, `JOB_STATE_RUNNING`, `JOB_STATE_SUCCEEDED`, `JOB_STATE_CANCELLED`).

The ML task's own state transitions to a terminal value (`COMPLETED`, `FAILED`, `EXPIRED`,
`CANCELLED`) once the remote job finishes. That value comes from the `batch_job_status` mapping
declared on the connector, not from the cluster-wide `plugins.ml_commons.remote_job.*` settings:
`remote_job.status_field` defaults to `["status", "Status", "TransformJobStatus"]`, none of which
is the `state` field Vertex reports. The `batch_job_status` block in step 2 is therefore required
rather than optional.

## 6. Cancel a job

```json
POST /_plugins/_ml/tasks/<TASK_ID>/_cancel
```

## Generic form (`protocol: http`, `auth_config`)

The same connector can be declared on the generic `http` protocol with an `auth_config` block of type
`oauth2` and the JWT bearer grant. The node signs a JWT assertion with the service account's private
key, exchanges it at the Google token endpoint for an access token, keeps the token in a cache on each
node, requests a new one before it expires, and sends `Authorization: Bearer <token>` with every
request, the derived status and cancel calls included. You write no `Authorization` header and never
handle a token.

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
    "name": "GCP Vertex AI Connector: batch inference",
    "description": "Vertex AI batchPredictionJobs connector",
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
    "batch_job_status": {
        "field_name": "state",
        "mapping": {
            "JOB_STATE_SUCCEEDED": "COMPLETED",
            "JOB_STATE_FAILED": "FAILED",
            "JOB_STATE_EXPIRED": "EXPIRED",
            "JOB_STATE_CANCELLING": "CANCELLING",
            "JOB_STATE_CANCELLED": "CANCELLED"
        }
    },
    "actions": [
        {
            "action_type": "batch_predict",
            "method": "POST",
            "url": "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/${parameters.project_id}/locations/${parameters.location}/batchPredictionJobs",
            "headers": {
                "Content-Type": "application/json"
            },
            "request_body": "{\"displayName\":\"${parameters.job_name}\",\"model\":\"publishers/google/models/${parameters.model}\",\"inputConfig\":{\"instancesFormat\":\"jsonl\",\"gcsSource\":{\"uris\":[\"${parameters.input_uri}\"]}},\"outputConfig\":{\"predictionsFormat\":\"jsonl\",\"gcsDestination\":{\"outputUriPrefix\":\"${parameters.output_uri}\"}}}"
        }
    ]
}
```

Only the `batch_predict` action is declared and the `batch_job_status` block is required, for the
reasons given in step 2; the status and cancel calls that ml-commons derives from `batch_predict`
carry the same bearer token.

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

### Register, deploy and run a job

Register and deploy the model as in the [Gemini blueprint](./gcp_vertexai_gemini_blueprint.md)
(steps 3–5), then submit, check and cancel jobs as in steps 4 to 6 above. For troubleshooting, see
the [Gemini blueprint](./gcp_vertexai_gemini_blueprint.md#troubleshooting).
