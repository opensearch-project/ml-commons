### OpenAI connector blueprint example for chat:

#### this blueprint is created from OpenAI doc: https://platform.openai.com/docs/api-reference/chat
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR MODEL NAME>",
  "description": "<YOUR MODEL DESCRIPTION>",
  "version": "<YOUR MODEL VERSION>",
  "protocol": "http",
  "parameters": {
    "endpoint": "api.openai.com",
    "model": "gpt-3.5-turbo"
  },
  "credential": {
    "openAI_key": "<PLEASE ADD YOUR OPENAI API KEY HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Authorization": "Bearer ${credential.openAI_key}"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
    }
  ]
}
```

#### Sample response
```json
{
  "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```

### Corresponding Predict request example:

```json
POST /_plugins/_ml/models/<ENTER MODEL ID HERE>/_predict
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
            "id": "chatcmpl-7g0QJH6nuFW94l8tDkJzxm0ntaPNd",
            "object": "chat.completion",
            "created": 1690245759,
            "model": "gpt-3.5-turbo-0613",
            "choices": [
              {
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today?"
                },
                "finish_reason": "stop"
              }
            ],
            "usage": {
              "prompt_tokens": 19,
              "completion_tokens": 9,
              "total_tokens": 28
            }
          }
        }
      ]
    }
  ]
}
```
