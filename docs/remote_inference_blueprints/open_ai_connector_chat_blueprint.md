```json
{
  "name": "OpenAI Chat Connector",
  "description": "The connector to public OpenAI model service for GPT 3.5",
  "version": 1,
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
