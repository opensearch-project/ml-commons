# Azure OpenAI connector blueprint example for LLM judgment

This blueprint integrates an [Azure OpenAI Chat Completions](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#chat-completions) deployment for Search Relevance Workbench (SRW) LLM judgments. It maps SRW's neutral `system_prompt` / `user_prompt` parameters into the Chat Completions `messages` shape and adds a `post_process_function` that returns the model text in a neutral `response` field. Azure exposes the same Chat Completions shape as OpenAI; only the endpoint, API version, and `api-key` header differ.

## Available models

Azure routes by your **deployment name** (the `deploy-name` parameter), not a model ID — the underlying model is whatever you deployed in your Azure OpenAI resource. Models you can deploy:

| Model | Deployable model name |
|---|---|
| GPT-5 | `gpt-5` |
| GPT-4.1 | `gpt-4.1` |
| GPT-4o | `gpt-4o` |
| GPT-4o mini | `gpt-4o-mini` |

For the models you can deploy and the minimum API version each needs, see [Azure OpenAI models](https://learn.microsoft.com/en-us/azure/ai-services/openai/concepts/models).

## 1. Add connector endpoint to trusted URLs:
Note: skip this step starting 2.19.0

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
          "^https://.*\\.openai\\.azure\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for Azure OpenAI Chat:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Azure OpenAI Chat",
  "description": "Azure OpenAI Chat Completions connector for SRW LLM judgments",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "endpoint": "<YOUR RESOURCE NAME>.openai.azure.com",
    "deploy-name": "<YOUR DEPLOYMENT NAME>",
    "api-version": "2024-08-01-preview"
  },
  "credential": {
    "openAI_key": "<PLEASE ADD YOUR AZURE OPENAI API KEY HERE>"
  },
  "client_config": {
    "max_retry_times": 3,
    "retry_backoff_policy": "exponential_full_jitter"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/openai/deployments/${parameters.deploy-name}/chat/completions?api-version=${parameters.api-version}",
      "headers": {
        "Content-Type": "application/json",
        "api-key": "${credential.openAI_key}"
      },
      "request_body": "{\"messages\":[{\"role\":\"system\",\"content\":\"${parameters.system_prompt}\"},{\"role\":\"user\",\"content\":\"${parameters.user_prompt}\"}]}",
      "post_process_function": "def text = params.choices[0].message.content; return '{\"name\":\"response\",\"dataAsMap\":{\"response\":\"' + escape(text) + '\"}}'"
    }
  ]
}
```

#### Sample response
```json
{
  "connector_id": "<CONNECTOR_ID>"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_llm_judgment",
    "description": "Model group for SRW LLM judgment models"
}
```

#### Sample response
```json
{
  "model_group_id": "<MODEL_GROUP_ID>",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "Azure OpenAI Chat model",
  "function_name": "remote",
  "model_group_id": "<MODEL_GROUP_ID>",
  "description": "Azure OpenAI Chat for SRW LLM judgments",
  "connector_id": "<CONNECTOR_ID>"
}
```

#### Sample response
```json
{
  "task_id": "<TASK_ID>",
  "status": "CREATED",
  "model_id": "<MODEL_ID>"
}
```

## 5. Test model inference

SRW emits the neutral `system_prompt` and `user_prompt`; this blueprint maps them into the Chat Completions `messages` array.

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "system_prompt": "You are a relevance rater. Reply with strict JSON only.",
    "user_prompt": "Rate the relevance of {\"id\":\"001\",\"text\":\"banana smoothie\"} to query banana on a 0.0-1.0 scale. Return {\"ratings\":[{\"id\":\"001\",\"rating_score\":<num>}]}."
  }
}
```

#### Sample response
The `post_process_function` copies `choices[0].message.content` into the neutral `response` field that SRW reads.

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "response": "{\"ratings\":[{\"id\":\"001\",\"rating_score\":0.9}]}"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
