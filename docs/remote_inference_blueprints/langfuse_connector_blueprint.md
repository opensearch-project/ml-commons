### Langfuse Connector Blueprint:

## 1. Add Langfuse Endpoint to Trusted URLs:

```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://us\\.cloud\\.langfuse\\.com/.*$"
    ]
  }
}
```

Sample response:
```json
{
  "acknowledged": true,
  "persistent": {
    "plugins": {
      "ml_commons": {
        "trusted_connector_endpoints_regex": [
          "^https://us\\.cloud\\.langfuse\\.com/.*$"
        ]
      }
    }
  },
  "transient": {}
}
```

## 2. Create Connector for Langfuse:
The following connector is connecting to the API for getting a prompt. There are other apis from Langfuse that is available to connect to, please refer to https://api.reference.langfuse.com/#get-/api/public/comments. 
Note: The username:password should be Base64 encoded. The username is the Langfuse Public Key, and the password is the Langfuse Secret Key.
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Langfuse",
  "version": "1",
  "description": "The connector to Langfuse",
  "protocol": "http",
  "parameters": {
    "promptName": "<YOUR_PROMPT_NAME>"
  },
  "credential": {
    "username:password": "<BASE64_ENCODED_CREDENTIALS>"
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "GET",
      "url": "https://us.cloud.langfuse.com/api/public/v2/prompts/${parameters.promptName}",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json",
        "Authorization": "Basic ${credential.username:password}"
      },
      "request_body": "{ \"promptName\": ${parameters.promptName} }"
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "WKLJj5QB8stpCo6Fw-3r"
}
```

## 3. Create model group (optional):

```json
POST /_plugins/_ml/model_groups/_register
{
  "name": "langfuse_model_group",
  "description": "Model group for Langfuse models"
}
```
Sample response:
```json
{
  "model_group_id": "BPtPEIwBqYi_Zeg-SR7R",
  "status": "CREATED"
}
```

## 3. Register model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "langfuse model",
  "version": "1.0",
  "function_name": "remote",
  "description": "Langfuse model",
  "connector_id": "<YOUR_CONNECTOR_ID>"
}
```

Sample response:
```json
{
  "task_id": "pX8scY4B2QHLlv0i6LYZ",
  "status": "CREATED",
  "model_id": "pn8scY4B2QHLlv0i6LZB"
}
```

## 4. Deploy Model:
```json
POST /_plugins/_ml/models/<YOUR_MODEL_ID>/_deploy
```
Sample response:
```json
{
  "task_id": "W6LJj5QB8stpCo6F_-3z",
  "task_type": "DEPLOY_MODEL",
  "status": "COMPLETED"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/<YOUR_MODEL_ID>/_predict
{
  "parameters": {
    "promptName": "<YOUR_PROMPT_NAME>"
  }
}
```

Sample response:
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "id": "cm677jhsl00mvjlr01ac2cglf",
            "createdAt": "2025-01-22T01:10:34.197Z",
            "updatedAt": "2025-01-22T01:10:34.197Z",
            "projectId": "cm66wtcxl00imd76r8flcho67",
            "createdBy": "cm66ws5az00ho13n5qkoh0o60",
            "prompt": "\\n\\nHuman:You are an AI search assistant for a music search service. \\n\\n Assistant:",
            "name": "Milestone1_json",
            "version": 1.0,
            "type": "text",
            "isActive": null,
            "config": {},
            "tags": [],
            "labels": [
              "production",
              "latest"
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```