# Ollama (OpenAI compatible) connector blueprint example for LLM judgment

This blueprint integrates a locally hosted, OpenAI-compatible LLM (Ollama, llama.cpp, vLLM, etc.) for Search Relevance Workbench (SRW) LLM judgments. It adds a `post_process_function` that returns the model text in a neutral `response` field. Any server exposing the OpenAI Chat Completions API works.

## Available models

Set the `model` parameter to any model you have pulled on your local server. Common Ollama examples:

| Model | Model ID |
|---|---|
| Qwen 3 | `qwen3` |
| Llama 3.3 | `llama3.3` |
| Mistral | `mistral` |
| Phi-4 | `phi4` |
| Gemma 3 | `gemma3` |

List what you have with `ollama list`, and browse the catalog at [ollama.com/library](https://ollama.com/library). For other servers (vLLM, llama.cpp), use whatever model name they expose.

## 1. Add connector endpoint to trusted URLs

Adjust the regex to your local IP. The following example allows all URLs.

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            ".*$"
        ]
    }
}
```

## 2. Enable private addresses

A local server uses a private IP, so allow it. Starting in OpenSearch 3.7, private IP access also requires an allowlist regex (`trusted_connector_private_endpoints_regex`); adjust the pattern to your local IP.

```json
PUT /_cluster/settings
{
    "persistent": {
      "plugins.ml_commons.connector.private_ip_enabled": true,
      "plugins.ml_commons.trusted_connector_private_endpoints_regex": ["^http://127\\.0\\.0\\.1:11434/.*$"]
    }
}
```

## 3. Create connector for the local model

In a local setting `openAI_key` might not be needed; set it to any value, or remove it and the `Authorization` header.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Local OpenAI-compatible Chat",
  "description": "Local OpenAI-compatible LLM connector for SRW LLM judgments",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "endpoint": "127.0.0.1:11434",
    "model": "<MODEL_ID>"  // example: qwen3:4b
  },
  "credential": {
    "openAI_key": "<YOUR API KEY HERE IF NEEDED>"
  },
  "client_config": {
    "max_retry_times": 3,
    "retry_backoff_policy": "exponential_full_jitter"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "http://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "Bearer ${credential.openAI_key}"
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

## 4. Create model group:

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

## 5. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "Local OpenAI-compatible model",
  "function_name": "remote",
  "model_group_id": "<MODEL_GROUP_ID>",
  "description": "Local model for SRW LLM judgments",
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

## 6. Test model inference

`response_format` is not sent because local-server support varies; the system prompt instructs strict JSON and SRW sanitises the response before parsing.

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
        "content": "Rate the relevance of {\"id\":\"001\",\"text\":\"banana smoothie\"} to query banana on a 0.0-1.0 scale."
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
