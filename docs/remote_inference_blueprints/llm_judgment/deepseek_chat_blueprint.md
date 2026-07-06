# DeepSeek connector blueprint example for LLM judgment

This blueprint integrates the [DeepSeek Chat Model](https://api-docs.deepseek.com/api/create-chat-completion) for Search Relevance Workbench (SRW) LLM judgments. It adds a `post_process_function` that returns the model text in a neutral `response` field so SRW can read any provider's output the same way. DeepSeek's API is OpenAI-compatible, so SRW's legacy `messages` parameter is used directly.

## Available models

Set the `model` parameter to a current DeepSeek model:

| Model | Model ID |
|---|---|
| DeepSeek-V3 (general chat) | `deepseek-chat` |
| DeepSeek-R1 (reasoning) | `deepseek-reasoner` |

For the authoritative, current list see the [DeepSeek API docs](https://api-docs.deepseek.com/quick_start/pricing).

## 1. Add connector endpoint to trusted URLs:
Note: skip this step starting 2.19.0

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
          "^https://api\\.deepseek\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for DeepSeek Chat:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "DeepSeek Chat",
  "description": "DeepSeek Chat connector for SRW LLM judgments",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "endpoint": "api.deepseek.com",
    "model": "<MODEL_ID>"  // example: deepseek-chat
  },
  "credential": {
    "deepSeek_key": "<PLEASE ADD YOUR DEEPSEEK API KEY HERE>"
  },
  "client_config": {
    "max_retry_times": 3,
    "retry_backoff_policy": "exponential_full_jitter"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "Bearer ${credential.deepSeek_key}"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }",
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
  "name": "DeepSeek Chat model",
  "function_name": "remote",
  "model_group_id": "<MODEL_GROUP_ID>",
  "description": "DeepSeek Chat for SRW LLM judgments",
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

SRW emits `messages` (and the neutral `system_prompt` / `user_prompt`) on every call; this blueprint references `messages`.

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a relevance rater. Reply with strict JSON only."
      },
      {
        "role": "user",
        "content": "Rate the relevance of {\"id\":\"001\",\"text\":\"banana smoothie\"} to query banana on a 0.0-1.0 scale. Return {\"ratings\":[{\"id\":\"001\",\"rating_score\":<num>}]}."
      }
    ]
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
