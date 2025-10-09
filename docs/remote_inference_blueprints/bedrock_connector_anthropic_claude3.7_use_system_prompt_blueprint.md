# Bedrock connector blueprint example for Claude 3.7 model

Anthropic's Claude 3.7 Sonnet model is now available on Amazon Bedrock. For more details, check out this [blog](https://aws.amazon.com/blogs/aws/anthropics-claude-3-7-sonnet-the-first-hybrid-reasoning-model-is-now-available-in-amazon-bedrock/).

Claude 3.7 is Anthropic's first hybrid reasoning model, supporting two modes: standard and extended thinking. This doc covers both modes.

## 1. Add connector endpoint to trusted URLs:

Note: This step is only necessary for OpenSearch versions prior to 2.11.0.

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

## 2. Standard mode
### 2.1 Create connector

If you are using self-managed Opensearch, you should supply AWS credentials:

Note:
1. User needs to use [inference profile](https://docs.aws.amazon.com/bedrock/latest/userguide/inference-profiles-support.html) for invocation of this model. We can see the profile ID for Claude 3.7 is `us.anthropic.claude-3-7-sonnet-20250219-v1:0` for three available US regions `us-east-1`, `us-east-2`, `us-west-2`.

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v3.7",
    "description": "Test connector for Amazon Bedrock claude v3.7",
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
        "max_tokens": 8000,
        "temperature": 1,
        "anthropic_version": "bedrock-2023-05-31",
        "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
        "use_system_prompt": true
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{ \"system\": \"${parameters.system_prompt}\", \"anthropic_version\": \"${parameters.anthropic_version}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature}, \"messages\": ${parameters.messages} }"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v3.7",
    "description": "Test connector for Amazon Bedrock claude v3.7",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "max_tokens": 8000,
        "temperature": 1,
        "anthropic_version": "bedrock-2023-05-31",
        "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
        "use_system_prompt": true
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{ \"system\": \"${parameters.system_prompt}\", \"anthropic_version\": \"${parameters.anthropic_version}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature}, \"messages\": ${parameters.messages} }"
        }
    ]
}
```

Sample response:
```json
{
  "connector_id": "fa5tP5UBX2k07okSp89B"
}
```

### 2.2 Register model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "anthropic.claude-v3.7",
    "function_name": "remote",
    "description": "claude v3.7 model",
    "connector_id": "fa5tP5UBX2k07okSp89B"
}
```

Sample response:
```json
{
  "task_id": "fq5uP5UBX2k07okSFM__",
  "status": "CREATED",
  "model_id": "f65uP5UBX2k07okSFc8P"
}
```

### 2.3 Test model inference

```json
POST /_plugins/_ml/models/f65uP5UBX2k07okSFc8P/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant.",
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "text",
            "text": "hello world"
          }
        ]
      }
    ]
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
            "id": "msg_bdrk_012spGFGr4CcD1PWb2TSfYut",
            "type": "message",
            "role": "assistant",
            "model": "claude-3-7-sonnet-20250219",
            "content": [
              {
                "type": "text",
                "text": "Hello! It's nice to meet you. How can I help you today?"
              }
            ],
            "stop_reason": "end_turn",
            "stop_sequence": null,
            "usage": {
              "input_tokens": 9.0,
              "cache_creation_input_tokens": 0.0,
              "cache_read_input_tokens": 0.0,
              "output_tokens": 19.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 3. Extended thinking mode

Extended thinking mode allows Claude 3.7 to perform more in-depth reasoning before providing a response.

### 3.1 Create connector

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v3.7",
    "description": "Test connector for Amazon Bedrock claude v3.7",
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
        "max_tokens": 8000,
        "temperature": 1,
        "anthropic_version": "bedrock-2023-05-31",
        "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
        "budget_tokens": 1024,
        "use_system_prompt": true
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{ \"system\": \"${parameters.system_prompt}\", \"anthropic_version\": \"${parameters.anthropic_version}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature}, \"messages\": ${parameters.messages}, \"thinking\": {\"type\": \"enabled\", \"budget_tokens\": ${parameters.budget_tokens} } }"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v3.7",
    "description": "Test connector for Amazon Bedrock claude v3.7",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "max_tokens": 8000,
        "temperature": 1,
        "anthropic_version": "bedrock-2023-05-31",
        "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
        "budget_tokens": 1024,
        "use_system_prompt": true
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{ \"system\": \"${parameters.system_prompt}\", \"anthropic_version\": \"${parameters.anthropic_version}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature}, \"messages\": ${parameters.messages}, \"thinking\": {\"type\": \"enabled\", \"budget_tokens\": ${parameters.budget_tokens} } }"
        }
    ]
}
```

Sample response:
```json
{
  "connector_id": "1652P5UBX2k07okSys_J"
}
```

### 3.2 Register model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "anthropic.claude-v3.7",
    "function_name": "remote",
    "description": "claude v3.7 model",
    "connector_id": "1652P5UBX2k07okSys_J"
}
```

Sample response:
```json
{
  "task_id": "5K53P5UBX2k07okSXc-7",
  "status": "CREATED",
  "model_id": "5a53P5UBX2k07okSXc_M"
}
```

### 3.3 Test model inference

```json
POST /_plugins/_ml/models/5a53P5UBX2k07okSXc_M/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant.",
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "text",
            "text": "hello world"
          }
        ]
      }
    ]
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
            "id": "msg_bdrk_01TqgZsyqsxhNGAGVjRjCP6N",
            "type": "message",
            "role": "assistant",
            "model": "claude-3-7-sonnet-20250219",
            "content": [
              {
                "type": "thinking",
                "thinking": "This is a simple greeting phrase \"hello world\" which is often the first program someone writes when learning a new programming language. The person could be:\n1. Simply greeting me casually\n2. Making a reference to programming\n3. Testing if I'm working\n\nI'll respond with a friendly greeting that acknowledges the \"hello world\" phrase and its connection to programming culture, while being conversational.",
                "signature": "<THOUGHT_SIGNATURE>"
              },
              {
                "type": "text",
                "text": "Hello! It's nice to meet you. \"Hello world\" is such a classic phrase - it's often the first program many people write when learning to code! How are you doing today? Is there something I can help you with?"
              }
            ],
            "stop_reason": "end_turn",
            "stop_sequence": null,
            "usage": {
              "input_tokens": 37.0,
              "cache_creation_input_tokens": 0.0,
              "cache_read_input_tokens": 0.0,
              "output_tokens": 143.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
