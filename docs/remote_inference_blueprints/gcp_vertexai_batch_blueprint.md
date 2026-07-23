# GCP Vertex AI Batch Inference Connector Blueprint

This blueprint runs offline batch inference against Vertex AI `batchPredictionJobs` using the
`google_cloud` connector protocol. OAuth2 tokens are minted and refreshed automatically; no
`Authorization` header is added by you.

It defines three actions:
- `batch_predict` — submit a batch prediction job.
- `batch_predict_status` — poll a job's status.
- `cancel_batch_predict` — cancel a running job.

Batch input and output are configured in the request body via GCS or BigQuery locations. See
the [Vertex AI batch prediction docs](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/batch-prediction).

## 1. Add Vertex AI endpoint to trusted URLs

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://.*-aiplatform\\.googleapis\\.com/.*$"
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
so you do **not** declare separate `batch_predict_status` / `cancel_batch_predict` actions —
doing so would leave an unresolved `${parameters.job_id}` in the derived URL.

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

## 6. Cancel a job

```json
POST /_plugins/_ml/tasks/<TASK_ID>/_cancel
```
