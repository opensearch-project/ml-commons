```json
{
  "name": "sagemaker: embedding",
  "description": "Test connector for Sagemaker embedding model",
  "version": 1,
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
      "url": "https://runtime.sagemaker.${parameters.region}.amazonaws.com/endpoints/lmi-model-2023-06-24-01-35-32-275/invocations",
      "request_body": "[\"${parameters.inputs}\"]"
    }
  ]
}
```
