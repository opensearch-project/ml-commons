# Google Gemini connector blueprint example for LLM judgment

This blueprint integrates a Google Gemini chat model through the [generateContent API](https://ai.google.dev/api/generate-content) for Search Relevance Workbench (SRW) LLM judgments. It maps SRW's neutral `system_prompt` / `user_prompt` parameters into Gemini's `systemInstruction` / `contents` shape and adds a `post_process_function` that returns the model text in a neutral `response` field. Get an API key from [Google AI Studio](https://aistudio.google.com/apikey).

The API key is passed in the `x-goog-api-key` header rather than the URL query string, because ml-commons substitutes `${credential.*}` into headers but not into the URL.

## Available models

Set the `model` parameter to any current Gemini model:

| Model | Model ID |
|---|---|
| Gemini 2.5 Pro | `gemini-2.5-pro` |
| Gemini 2.5 Flash | `gemini-2.5-flash` |
| Gemini 2.5 Flash-Lite | `gemini-2.5-flash-lite` |
| Gemini 2.0 Flash | `gemini-2.0-flash` |

For the authoritative, current list see [Gemini API models](https://ai.google.dev/gemini-api/docs/models).

## 1. Add connector endpoint to trusted URLs

```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://generativelanguage\\.googleapis\\.com/.*$"
    ]
  }
}
```

## 2. Create connector for Google Gemini Chat:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Google Gemini Chat",
  "description": "Google Gemini generateContent connector for SRW LLM judgments",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "model": "<MODEL_ID>"  // example: gemini-2.5-flash
  },
  "credential": {
    "gemini_key": "<PLEASE ADD YOUR GEMINI API KEY HERE>"
  },
  "client_config": {
    "max_retry_times": 3,
    "retry_backoff_policy": "exponential_full_jitter"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent",
      "headers": {
        "Content-Type": "application/json",
        "x-goog-api-key": "${credential.gemini_key}"
      },
      "request_body": "{\"systemInstruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt}\"}]},\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.user_prompt}\"}]}]}",
      "post_process_function": "def text = params.candidates[0].content.parts[0].text; return '{\"name\":\"response\",\"dataAsMap\":{\"response\":\"' + escape(text) + '\"}}'"
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
  "name": "Google Gemini Chat model",
  "function_name": "remote",
  "model_group_id": "<MODEL_GROUP_ID>",
  "description": "Google Gemini for SRW LLM judgments",
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

SRW emits the neutral `system_prompt` and `user_prompt`; this blueprint maps them into Gemini's `systemInstruction` and `contents`.

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
The `post_process_function` copies `candidates[0].content.parts[0].text` into the neutral `response` field that SRW reads.

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
