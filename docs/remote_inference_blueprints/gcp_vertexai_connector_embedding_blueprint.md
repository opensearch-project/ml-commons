### GCP VertexAI Embedding Connector Blueprint:

## 1. Add VertexAI endpoint to trusted URLs:

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

## 2. Generate access-token to access Vertex AI

Vertex AI API can be only accessed by using the access tokens, you can generate the short-live or long live (upto 12h)

short live token can be generated using below command:

```
gcloud auth print-access-token
```

Longer expriration token upto 12h, can be generated using the below command:

```
# Authenticate using service account key
gcloud auth activate-service-account --key-file=/path/to/service-account-key.json

# Obtain access token
gcloud auth print-access-token
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_gcp",
    "description": "This is an example group for embedding models"
}
```

Sample response:
```json
{
  "model_group_id": "BPtPEIwBqYi_Zeg-SR7R",
  "status": "CREATED"
}
```


## 3. Register model for VertexAI embedding model:

Refer to [VertexAI Service REST API reference - Embedding](https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings#get_text_embeddings_for_a_snippet_of_text).

In order to use this, you need to supply the values for the below attributes

* Project Id
* Model Id
* Access token


```json
POST /_plugins/_ml/models/_register
{
    "name": "vertexAI: model to generate embeddings",
    "function_name": "remote",
    "model_group_id": "BPtPEIwBqYi_Zeg-SR7R",
    "description": "test vertexAI model",
    "connector": {
        "name": "VertexAI Connector",
        "description": "The connector to public vertexAI model service for text embedding",
        "version": 1,
        "protocol": "http",
        "parameters": {
            "project": "<YOUR PROJECT_ID>",
            "model_id": "<YOUR MODEL_ID>"
        },
        "credential": {
            "vertexAI_token": "<YOUR ACCESS TOKEN>"
        },
        "actions": [
            {
                "action_type": "predict",
                "method": "POST",
                "url": "https://us-central1-aiplatform.googleapis.com/v1/projects/${parameters.project}/locations/us-central1/publishers/google/models/${parameters.model_id}:predict",
                "headers": {
                    "Authorization": "Bearer ${credential.vertexAI_token}"
                },
                "request_body": "{\"instances\": [{ \"content\": \"${parameters.prompt}\"}]}"
            }
        ]
    }
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

## 4. Check the model registration
```json
GET /_plugins/_ml/tasks/pX8scY4B2QHLlv0i6LYZ
```
Sample response:


```Response:
{
  "model_id": "pn8scY4B2QHLlv0i6LZB",
  "task_type": "REGISTER_MODEL",
  "function_name": "REMOTE",
  "state": "COMPLETED",
  "worker_node": [
    "pQg-fbRpSm-x-V-0ICQpQw"
  ],
  "create_time": 1711295752216,
  "last_update_time": 1711295752273,
  "is_async": false
}
```

## 5. Deploy the model

```json
POST /_plugins/_ml/models/pn8scY4B2QHLlv0i6LZB/_deploy
```
Sample response:

```Response:
{
  "task_id": "p38xcY4B2QHLlv0iSbb_",
  "task_type": "DEPLOY_MODEL",
  "status": "COMPLETED"
}
```

## 6. Check model deployment
```json
GET /_plugins/_ml/tasks/p38xcY4B2QHLlv0iSbb_
```

Sample response:
```
{
  "model_id": "pn8scY4B2QHLlv0i6LZB",
  "task_type": "REGISTER_MODEL",
  "function_name": "REMOTE",
  "state": "COMPLETED",
  "worker_node": [
    "pQg-fbRpSm-x-V-0ICQpQw"
  ],
  "create_time": 1711295752216,
  "last_update_time": 1711295752273,
  "is_async": false
}
```


## 7. Test model inference

```json
POST /_plugins/_ml/models/pn8scY4B2QHLlv0i6LZB/_predict
{
  "parameters": {
    "prompt": "Hello World form vertex AI!"
  }
}
```

Response:
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "predictions": [
              {
                "embeddings": {
                  "statistics": {
                    "truncated": false,
                    "token_count": 7
                  },
                  "values": [
                    0.006371465977281332,
                    -0.03583695366978645,
                    -0.009082107804715633,
                    -0.018701229244470596,
                    0.00281976698897779,
                    -0.00454947492107749,
                    0.012263650074601173,
                    -0.011159989982843399,
                    -0.01578657515347004,
                    0.004389482084661722,
                    0.0002078384131891653,
                    -0.000520859903190285,
                    0.03948991373181343,
                    -0.057535942643880844,
                    -0.005250570364296436,
                    -0.02551070787012577,
                    0.022418972104787827,
                    0.029485566541552544,
                    0.01078018918633461,
                    -0.010538897477090359
                    ....
                  ]
                }
              }
            ],
            "metadata": {
              "billableCharacterCount": 23
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```


## 8. Update the access token

This requirement arises because the Vertex API can only be accessed using the GCP access token, which has a limited lifespan. Applications are responsible for refreshing this token as needed. 

Note:  This update feature is only avaialable in 2.12 or later

```json
POST /_plugins/_ml/models/pn8scY4B2QHLlv0i6LZB
{
  "connector": {
    "credential": {
      "vertexAI_token": "<YOUR REFRESHED ACCESS TOKEN>"
    }
  }
}
```

Response:
```json
{
  "_index": ".plugins-ml-model",
  "_id": "qwshEo4BP0vcvNchDtVA",
  "_version": 5,
  "result": "updated",
  "forced_refresh": true,
  "_shards": {
    "total": 1,
    "successful": 1,
    "failed": 0
  },
  "_seq_no": 36,
  "_primary_term": 3
}
```
