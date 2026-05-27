# Bedrock connector blueprint example for Converse

> **Note:** `supports_structured_output: true` enables tool-use constrained decoding for agentic memory fact extraction. When set, the memory pipeline injects a `toolConfig` into the Bedrock Converse request, forcing the model to call the `extract_facts` tool and return structured JSON. See [connector action parameters](../tutorials/remote_inference.md#connector) for details.

## 1. Add connector endpoint to trusted URLs:

Note: no need to do this after 2.11.0

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

## 2. Create connector for Amazon Bedrock Converse:

If you are using self-managed OpenSearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Converse",
    "description": "Test connector for Amazon Bedrock Converse",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
        "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
        "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "response_filter": "$.output.message.content[0].text",
        "model": "anthropic.claude-sonnet-4-5-20251101-v1:0"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "supports_structured_output": true,
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
            "request_body": "{\"system\":[{\"text\":\"${parameters.system_prompt}\"}],\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${parameters.user_prompt}\"}]}]}"
        }
    ]
}
```

If using the AWS OpenSearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Converse",
    "description": "Test connector for Amazon Converse",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "response_filter": "$.output.message.content[0].text",
        "model": "anthropic.claude-sonnet-4-5-20251101-v1:0"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "supports_structured_output": true,
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
            "request_body": "{\"system\":[{\"text\":\"${parameters.system_prompt}\"}],\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${parameters.user_prompt}\"}]}]}"
        }
    ]
}
```

Sample response:
```json
{
  "connector_id": "nMopmY8B8aiZvtEZLu9B"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_converse",
    "description": "This is an example description"
}
```

Sample response:
```json
{
  "model_group_id": "IMobmY8B8aiZvtEZeO_i",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "amazon bedrock converse",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "amazon bedrock converse",
    "connector_id": "nMopmY8B8aiZvtEZLu9B"
}
```

Sample response:
```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "rcormY8B8aiZvtEZIe89"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/rcormY8B8aiZvtEZIe89/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant.",
    "user_prompt": "What is the meaning of life?"
  }
}
```

Sample response:
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "response": "The meaning of life is a profound philosophical question..."
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
