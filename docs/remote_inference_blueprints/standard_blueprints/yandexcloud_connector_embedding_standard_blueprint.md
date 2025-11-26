# Yandex Cloud AI Studio connector standard blueprint example for embedding models

This blueprint demonstrates how to deploy a Yandex Cloud AI Studio embedding models. 

## 1. Allow connection to Yandex Cloud

```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://llm\\.api\\.cloud\\.yandex\\.net/.*$"
    ]
  }
}
```

## 2. Create connector for Yandex Cloud Embeddings:


```json
POST /_plugins/_ml/connectors/_create
{
  "name": "YC Connector: embedding",
  "description": "Yandex Cloud AI Studio Embeddings",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "modelUri": "emb://<folder_ID>/text-search-<doc|query>/latest",
    "folder_id":"<folder_ID>"
  },
  "credential": {
    "api_key": "<API-KEY>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://llm.api.cloud.yandex.net/foundationModels/v1/textEmbedding",
      "headers": {
        "Authorization": "Api-Key ${credential.api_key}",
        "x-folder-id": "${parameters.folder_id}"
      },
      "request_body": "{ \"text\": \"${parameters.inputText}\", \"modelUri\": \"${parameters.modelUri}\" }",
      "pre_process_function": "connector.pre_process.bedrock.embedding",
      "post_process_function": "connector.post_process.bedrock.embedding"
    }
  ]
}
```

Note: Replace all `<placeholders>` in the preceding code snippet with appropriate values, while preserving `${curly braces}` syntax exactly as shown. Short-lived [bearer tokens](https://yandex.cloud/en/docs/iam/concepts/authorization/iam-token) (valid ~12 hours) may be used as an alternative to [API keys](https://yandex.cloud/en/docs/iam/concepts/authorization/api-key). API keys must be granted either `yc.ai.languageModels.execute` or `yc.ai.foundationModels.execute` roles. Also refer to [the guide](https://yandex.cloud/en/docs/ai-studio/security/). Additionally, due to distinct [models](https://yandex.cloud/en/docs/ai-studio/concepts/embeddings) being employed for query processing versus document processing, two dedicated connectors are required. Using these particular pre/post processing functions is crucial.  

Sample response:
```json
{
    "connector_id": "CTEou5oBdUNOOrVArUAU"
}
```

## 3. Register model & deploy model:

```json
POST /_plugins/_ml/model_groups/_register
{
  "name": "yc_remote_model_group",
  "description": "A model group for external YC AI Studio models"
}
```

Sample response:
```json
{
    "model_group_id": "4THNtZoBdUNOOrVAzj_V"
}
```

```json
POST /_plugins/_ml/models/_register
{
    "name": "yc-embedding",
    "function_name": "remote",
    "model_group_id": "4THNtZoBdUNOOrVAzj_V",
    "description": "YC embedding model",
    "connector_id": "CTEou5oBdUNOOrVArUAU"
}
```

Sample response:
```json
{
  "task_id": "5THZtZoBdUNOOrVAEj_I",
  "status": "CREATED",
  "model_id": "CzEou5oBdUNOOrVA10Db"
}
```

## 4. Test model inference

```json
POST /_plugins/_ml/models/CzEou5oBdUNOOrVA10Db/_predict
{
  "parameters": {
    "inputText": "What is the meaning of life?"
  }
}
```

Sample response of Yandex Cloud AI Studio Embedding:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            256
          ],
          "data": [
            0.06268310546875,
            -0.04071044921875,
            0.047119140625,
            -0.007476806640625,
            -0.038543701171875,
            -0.003681182861328125,
            ...
            0.040679931640625
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
