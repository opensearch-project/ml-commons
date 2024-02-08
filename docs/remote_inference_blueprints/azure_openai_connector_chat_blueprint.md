# Azure OpenAI connector Blueprint for Chat Completion:

## 1. Add Azure OpenAI Endpoint to Trusted URLs:

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

## 2. Create Connector for Azure OpenAI Chat Model:

Refer to [Azure OpenAI Service REST API reference - Chat Completion](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#chat-completions).

If you are using self-managed Opensearch, you should supply OpenAI API key:


```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "parameters": {
    "endpoint": "<YOUR RESOURCE NAME>.openai.azure.com/",
    "deploy-name": "<YOUR DEPLOYMENT NAME>",
    "model": "gpt-4",
    "api-version": "<YOUR API VERSION>",
    "temperature": 0.7
  },
  "credential": {
    "openAI_key": "<YOUR API KEY>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/openai/deployments/${parameters.deploy-name}/chat/completions?api-version=${parameters.api-version}",
      "headers": {
        "api-key": "${credential.openAI_key}"
      },
      "request_body": "{ \"messages\": ${parameters.messages}, \"temperature\": ${parameters.temperature} }"
    }
  ]
}
```

### Sample response:
```json
{
  "connector_id": "EapnEY0BpYxvPx3Hxpmp"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
    "description": "This is an example description"
}
```

### Sample response
```json
{
  "model_group_id": "YyvcbYsBjU568JRbdHqv",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
    "name": "azure-openAI-gpt-4",
    "function_name": "remote",
    "model_group_id": "YyvcbYsBjU568JRbdHqv",
    "description": "Azure OpenAI GPT 4",
    "connector_id": "EapnEY0BpYxvPx3Hxpmp"
}
```

### Sample response
```json
{
  "task_id": "E6ppEY0BpYxvPx3HZZkL",
  "status": "CREATED",
  "model_id": "FKppEY0BpYxvPx3HZZk0"
}
```

Check if the task is completed
```
GET /_plugins/_ml/tasks/E6ppEY0BpYxvPx3HZZkL
```

When model registration is completed, deploy it
```
POST /_plugins/_ml/models/FKppEY0BpYxvPx3HZZk0/_deploy
```

## 5. Test model
```json
POST /_plugins/_ml/models/FKppEY0BpYxvPx3HZZk0/_predict
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

### Sample response

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "id": "chatcmpl-8hZDlKJIaFLrC2sGTI2EYBiHQUhdI",
            "object": "chat.completion",
            "created": 1705394185,
            "model": "gpt-35-turbo",
            "prompt_filter_results": [
              {
                "prompt_index": 0,
                "content_filter_results": {
                  "hate": {
                    "filtered": false,
                    "severity": "safe"
                  },
                  "self_harm": {
                    "filtered": false,
                    "severity": "safe"
                  },
                  "sexual": {
                    "filtered": false,
                    "severity": "safe"
                  },
                  "violence": {
                    "filtered": false,
                    "severity": "safe"
                  }
                }
              }
            ],
            "choices": [
              {
                "finish_reason": "stop",
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today?"
                },
                "content_filter_results": {
                  "hate": {
                    "filtered": false,
                    "severity": "safe"
                  },
                  "self_harm": {
                    "filtered": false,
                    "severity": "safe"
                  },
                  "sexual": {
                    "filtered": false,
                    "severity": "safe"
                  },
                  "violence": {
                    "filtered": false,
                    "severity": "safe"
                  }
                }
              }
            ],
            "usage": {
              "prompt_tokens": 19,
              "completion_tokens": 9,
              "total_tokens": 28
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

