### Sagemaker connector blueprint example for embedding:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION TOKEN HERE>",
    "service_name": "sagemaker"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "content-type": "application/json"
      },
      "url": "<PLEASE ADD YOUR Sagemaker MODEL ENDPOINT URL>",
      "request_body": "<PLEASE ADD YOUR REQUEST BODY. Example: ${parameters.inputs}>"
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

