# DeepSeek connector blueprint example for Chat
This blueprint integrates [DeepSeek Chat Model](https://api-docs.deepseek.com/api/create-chat-completion) for question-answering capabilities for standalone interactions. Full conversational functionality requires additional development. 
Adapt and extend this blueprint as needed for your specific use case.

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
  "description": "Test connector for DeepSeek Chat",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "endpoint": "api.deepseek.com",
    "model": "deepseek-chat"
  },
  "credential": {
    "deepSeek_key": "<PLEASE ADD YOUR DEEPSEEK API KEY HERE>"
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
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
    }
  ]
}
```

#### Sample response
```json
{
  "connector_id": "n0dOqZQBQwAL8-GO1pYI"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_chat",
    "description": "This is an example description"
}
```

#### Sample response
```json
{
  "model_group_id": "b0cjqZQBQwAL8-GOVJZ4",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "DeepSeek Chat model",
  "function_name": "remote",
  "model_group_id": "b0cjqZQBQwAL8-GOVJZ4",
  "description": "DeepSeek Chat",
  "connector_id": "n0dOqZQBQwAL8-GO1pYI"
}
```

#### Sample response
```json
{
  "task_id": "oEdPqZQBQwAL8-GOCJbw",
  "status": "CREATED",
  "model_id": "oUdPqZQBQwAL8-GOCZYL"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/oUdPqZQBQwAL8-GOCZYL/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Hello!"
      }
    ]
  }
}
```

#### Sample response
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "id": "9d9bd689-88a5-44b0-b73f-2daa92518761",
            "object": "chat.completion",
            "created": 1.738011126E9,
            "model": "deepseek-chat",
            "choices": [
              {
                "index": 0.0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today? 😊"
                },
                "finish_reason": "stop"
              }
            ],
            "usage": {
              "prompt_tokens": 11.0,
              "completion_tokens": 11.0,
              "total_tokens": 22.0,
              "prompt_tokens_details": {
                "cached_tokens": 0.0
              },
              "prompt_cache_hit_tokens": 0.0,
              "prompt_cache_miss_tokens": 11.0
            },
            "system_fingerprint": "fp_3a5770e1b4"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
