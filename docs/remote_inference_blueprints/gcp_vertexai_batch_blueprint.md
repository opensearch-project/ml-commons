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
the [Vertex AI batch prediction docs](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/batch-prediction).

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
            "<each pattern returned above>",
            "^https://([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com/.*$"
        ]
    }
}
```

Assigning only the Vertex AI pattern replaces the defaults, which breaks connector creation and
inference for every other provider in the cluster, including SageMaker, OpenAI, Cohere, DeepSeek
and Bedrock.

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
