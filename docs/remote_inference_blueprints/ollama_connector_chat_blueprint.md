# Ollama (OpenAI compatible) connector blueprint example for chat

This is an AI connector blueprint for Ollama or any other local/self-hosted LLM as long as it is OpenAI compatible (Ollama, llama.cpp, vLLM, etc)

## 1. Add connector endpoint to trusted URLs

Adjust the Regex to your local IP.

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://127\\.0\\.0/.*$"
        ]
    }
}
```

## 2. Enable private addresses

```json
PUT /_cluster/settings
{
    "persistent": {
      "plugins.ml_commons.connector.private_ip_enabled": true
    }
}
```

## 3. Create the connector

In a local setting, `openAI_key` might not be needed. In case you can either set it to something irrelevant, or if removed, you need to update the `Authorization` header in the `actions`.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "parameters": {
    "endpoint": "127.0.0.1:11434",
    "model": "qwen3:4b"
  },
  "credential": {
    "openAI_key": "<YOUR API KEY HERE IF NEEDED>"
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

### Sample response

```json
{
  "connector_id": "Keq5FpkB72uHgF272LWj"
}
```

## 4. Register the model

```json
POST /_plugins/_ml/models/_register
{
  "name": "Local LLM Model",
  "function_name": "remote",
  "description": "Ollama model",
  "connector_id": "Keq5FpkB72uHgF272LWj"
}
```

### Sample response

Take note of the `model_id`. It is going to be needed going forward.

```json
{
  "task_id": "oEdPqZQBQwAL8-GOCJbw",
  "status": "CREATED",
  "model_id": "oUdPqZQBQwAL8-GOCZYL"
}
```

## 5. Deploy the model

Use `model_id` in place of the `<MODEL_ID>` placeholder.

```json
POST /_plugins/_ml/models/<MODEL_ID>/_deploy
```

### Sample response

```json
POST /_plugins/_ml/models/WWQI44MBbzI2oUKAvNUt/_deploy
{
    "node_ids": ["4PLK7KJWReyX0oWKnBA8nA"]
}
```

### 6. Corresponding Predict request example

Notice how you have to create the whole message structure, not just the message to send.
Use `model_id` in place of the `<MODEL_ID>` placeholder.

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Why is the sky blue"
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
            "choices": [
              {
                "finish_reason": "stop",
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": """The sky appears blue due to a phenomenon called Rayleigh scattering. Here's a simple explanation:

1. **Sunlight Composition**: Sunlight appears white, but it's actually a mix of all colors of the visible spectrum (red, orange, yellow, green, blue, indigo, violet).

2. **Atmospheric Scattering**: When sunlight enters Earth's atmosphere, it interacts with the gas molecules and tiny particles in the air. Shorter wavelengths of light (like blue and violet) are scattered more than other colors because they travel in shorter, smaller waves.

3. **Why Blue Dominates**: Although violet light is scattered even more than blue light, the sky appears blue, not violet, because:
   - Our eyes are more sensitive to blue light than violet light.
   - The sun emits more blue light than violet light.
   - Some of the violet light gets absorbed by the upper atmosphere.

4. **Time of Day**: The sky appears blue during the day because we're seeing the scattered blue light from all directions. At sunrise or sunset, the light has to pass through more of the atmosphere, scattering the blue light away and leaving mostly red and orange hues.

This scattering effect is named after Lord Rayleigh, who mathematically described the phenomenon in the 19th century."""
                }
              }
            ],
            "created": 1757369906,
            "model": "qwen3:4b",
            "system_fingerprint": "b6259-cebb30fb",
            "object": "chat.completion",
            "usage": {
              "completion_tokens": 264,
              "prompt_tokens": 563,
              "total_tokens": 827
            },
            "id": "chatcmpl-iHioFpaxa8K2SXgAHd4FhQnbewLQ9PjB",
            "timings": {
              "prompt_n": 563,
              "prompt_ms": 293.518,
              "prompt_per_token_ms": 0.5213463587921847,
              "prompt_per_second": 1918.1106439809487,
              "predicted_n": 264,
              "predicted_ms": 5084.336,
              "predicted_per_token_ms": 19.258848484848485,
              "predicted_per_second": 51.92418439693993
            }
          }
        }
      ],
      "status_code": 200
    }
```
