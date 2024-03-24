### GCP VertexAI Embedding Connector Blueprint:

## 1. Add VertextAI endpoint to trusted URLs:

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

## 2. Create connector for VertexAI embedding model:

Refer to [VertexAI Service REST API reference - Embedding](https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings#get_text_embeddings_for_a_snippet_of_text).

If you are using self-managed Opensearch, you should supply GCP Token key:


```json
POST /_plugins/_ml/connectors/_create
{
  "name": "VertexAI Connector",
  "description": "The connector to public vertextAI model service for text embedding",
  "version": 1,
  "protocol": "http",
  "parameters": {
    "project": "<YOUR EMBEDDING MODEL ID>",
    "model_id": "<YOUR PROJECT ID>"
  },
  "credential": {
    "vertexAI_key": "" // NOT Needed as gcp vertex AI based on access token
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "Authorization": "Bearer <YOUR ACCESS TOKEN>"
      },
      "url": "https://us-central1-aiplatform.googleapis.com/v1/projects/${parameters.project}/locations/us-central1/publishers/google/models/${parameters.model_id}:predict",
      "request_body": "{\"instances\": [{ \"content\": \"${parameters.prompt}\"}],}"
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "qQsgEo4BP0vcvNcheNWx"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
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

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "VertexAI: text embeddings model",
  "function_name": "remote",
  "model_group_id": "BPtPEIwBqYi_Zeg-SR7R",
  "description": "vertex AI text embedding model",
  "connector_id": "qQsgEo4BP0vcvNcheNWx"
}
```


Sample response:
```json
{
  "task_id": "rAshEo4BP0vcvNchOtWu",
  "status": "CREATED"
}
```
Get model id from task
```json
GET /_plugins/_ml/tasks/rAshEo4BP0vcvNchOtWu
```
Deploy model, in this demo the model id is `qwshEo4BP0vcvNchDtVA`
```json
POST /_plugins/_ml/models/qwshEo4BP0vcvNchDtVA/_deploy
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/qwshEo4BP0vcvNchDtVA/_predict
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

