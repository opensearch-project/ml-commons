# Yandex Cloud AI Studio connector standard blueprint example for embedding models

This blueprint demonstrates how to deploy Yandex Cloud AI Studio embedding models using the standard connector without pre and post processing functions.
This is recommended for OpenSearch version 2.14.0 and later to use the ML inference processor to handle input/output mapping.
Note that if using the legacy embedding approach with pre/post processing functions, please refer to the legacy blueprint: [Yandex Cloud AI Studio connector legacy blueprint example for embedding models](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/yandexcloud_connector_embedding_legacy_blueprint.md)

## 1. Allow connection to Yandex Cloud AI Studio

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

## 2. Create connector for Yandex Cloud AI Studio Embeddings

Refer to [Yandex Cloud AI Studio Embeddings API docs](https://aistudio.yandex.ru/docs/en/ai-studio/embeddings/createEmbedding.html).

### 2.1. Create connector for document embedding

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "YC AI Studio Connector: document embedding",
  "description": "Yandex Cloud AI Studio Embeddings for document search",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "model": "emb://<folder_ID>/text-search-doc/latest"
  },
  "credential": {
    "api_key": "<API-KEY>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://llm.api.cloud.yandex.net/v1/embeddings",
      "headers": {
        "Authorization": "Api-Key ${credential.api_key}"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\" }"
    }
  ]
}
```

Sample response:
```json
{
    "connector_id": "CTEou5oBdUNOOrVArUAU"
}
```

Note:
* Replace all `<placeholders>` in the preceding code snippet with appropriate values, while preserving `${curly braces}` syntax exactly as shown. Short-lived [bearer tokens](https://yandex.cloud/en/docs/iam/concepts/authorization/iam-token) (valid ~12 hours) may be used as an alternative to [API keys](https://yandex.cloud/en/docs/iam/concepts/authorization/api-key). API keys must be granted either `yc.ai.languageModels.execute` or `yc.ai.foundationModels.execute` roles. Also refer to [the guide](https://yandex.cloud/en/docs/ai-studio/security/).

### 2.2. Create connector for query embedding

Due to Yandex Cloud using distinct [models](https://yandex.cloud/en/docs/ai-studio/concepts/embeddings) for query processing and document processing, separate connectors are required for each purpose. To create the query processing connector, duplicate the connector definition above and replace `text-search-doc` with `text-search-query`.

## 3. Create model group:

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

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "yc-embedding",
    "function_name": "remote",
    "model_group_id": "4THNtZoBdUNOOrVAzj_V",
    "description": "YC AI Studio embedding model",
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

Model should be deployed already. In this demo the model id is `CzEou5oBdUNOOrVA10Db`.
If the model still needs to be deployed:

```json
POST /_plugins/_ml/models/CzEou5oBdUNOOrVA10Db/_deploy
```

```json
{
    "task_id": "IQ-ojJUB_BtQcl4FyMhB",
    "task_type": "DEPLOY_MODEL",
    "status": "COMPLETED"
}
```

Repeat this step with `connector_id` obtained in step `2.2` to get a dedicated `model_id` for query embedding.

## 5. Test model inference

```json
POST /_plugins/_ml/models/CzEou5oBdUNOOrVA10Db/_predict
{
  "parameters": {
    "input": ["What is the meaning of life?"]
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
                        "object": "list",
                        "data": [
                            {
                                "object": "embedding",
                                "index": 0.0,
                                "embedding": [
                                    0.06268310546875,
                                    -0.04071044921875,
                                    0.047119140625,
                                    -0.007476806640625,
                                    -0.038543701171875,
                                    ...
                                ]
                            }
                        ],
                        "model": "emb://<folder_ID>/text-search-doc/latest",
                        "usage": {
                            "prompt_tokens": 7.0,
                            "total_tokens": 7.0
                        }
                    }
                }
            ],
            "status_code": 200
        }
    ]
}
```
