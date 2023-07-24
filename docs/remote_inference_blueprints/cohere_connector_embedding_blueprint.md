```json
{
  "name": "Cohere Connector: embedding",
  "description": "The connector to cohere embedding model",
  "version": 1,
  "protocol": "http",
  "credential": {
    "cohere_key": "<PLEASE ADD YOUR Cohere API KEY HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.ai/v1/embed",
      "headers": {
        "Authorization": "Bearer ${credential.cohere_key}"
      },
      "request_body": "{ \"texts\": ${parameters.prompt}, \"truncate\": \"END\" }"
    }
  ]
}
```
