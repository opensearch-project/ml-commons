### Bedrock connector blueprint example

1. Add connector endpoint to trusted URLs:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
        ]
    }
}
```

2. Create connector for Amazon Bedrock:

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock",
    "description": "Test connector for Amazon Bedrock",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
        "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
        "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke",
            "request_body": "{\"prompt\":\"\\n\\nHuman: ${parameters.inputs}\\n\\nAssistant:\",\"max_tokens_to_sample\":300,\"temperature\":0.5,\"top_k\":250,\"top_p\":1,\"stop_sequences\":[\"\\\\n\\\\nHuman:\"]}"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock",
    "description": "Test connector for Amazon Bedrock",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke",
            "request_body": "{\"prompt\":\"\\n\\nHuman: ${parameters.inputs}\\n\\nAssistant:\",\"max_tokens_to_sample\":300,\"temperature\":0.5,\"top_k\":250,\"top_p\":1,\"stop_sequences\":[\"\\\\n\\\\nHuman:\"]}"
        }
    ]
}
```

Response:
```json
{"connector_id":"SHDj-ooB0wiuGR4S5sM4"}
```

3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
    "description": "This is an example description"
}
```

Response:
```json
{"model_group_id":"SXDn-ooB0wiuGR4SrcNN","status":"CREATED"}
```

4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
    "name": "anthropic.claude-v2",
    "function_name": "remote",
    "model_group_id": "SXDn-ooB0wiuGR4SrcNN",
    "description": "test model",
    "connector_id": "SHDj-ooB0wiuGR4S5sM4"
}
```

Response:
```json
{"task_id":"SnDo-ooB0wiuGR4SfMNS","status":"CREATED"}
```

```json
GET /_plugins/_ml/tasks/SnDo-ooB0wiuGR4SfMNS
```

```json
POST /_plugins/_ml/models/S3Do-ooB0wiuGR4SfcNv/_deploy
```

5. Test model inference

```json
POST /_plugins/_ml/models/S3Do-ooB0wiuGR4SfcNv/_predict
{
  "parameters": {
    "inputs": "What is the meaning of life?"
  }
}
```

Response:
```json
{"inference_results":[{"output":[{"name":"response","dataAsMap":{"completion":" There is no single, universally agreed upon meaning of life. The meaning of life is subjective and personal. Some common perspectives include finding happiness, purpose, spiritual fulfillment, connecting with others, contributing value, and leaving a positive legacy. Ultimately, the meaning of life is what you make of it.","stop_reason":"stop_sequence"}}]}]}
```