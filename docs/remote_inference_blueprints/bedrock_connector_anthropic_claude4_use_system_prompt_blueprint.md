# Bedrock connector blueprint example for Claude 4 models

Anthropic's Claude 4 models are now available on Amazon Bedrock. For more details, check out this [blog](https://www.aboutamazon.com/news/aws/anthropic-claude-4-opus-sonnet-amazon-bedrock).

Similar to Claude 3.7 Sonnet, Claude 4 offers both standard mode and [extended thinking mode](https://www.anthropic.com/news/visible-extended-thinking). Extended thinking mode directs the model to think more deeply about trickier questions by creating `thinking` content blocks for its internal reasoning. This also provides transparency into Claude's thought process before it delivers a final answer.

This blueprint will cover both the standard mode and the extended thinking mode.

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

If you would like to use the extended thinking mode, skip to [section 3](#section3).
### 2.1 Create connector

If you are using self-managed Opensearch, you should supply AWS credentials:

Note:
1. Users need to use an [inference profile](https://docs.aws.amazon.com/bedrock/latest/userguide/inference-profiles-support.html) to invoke this model. The profile IDs for Claude Sonnet 4 and Claude Opus 4 are `us.anthropic.claude-sonnet-4-20250514-v1:0` and `us.anthropic.claude-opus-4-20250514-v1:0` respectively, for three available US regions `us-east-1`, `us-east-2` and `us-west-2`.

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v4",
    "description": "Test connector for Amazon Bedrock claude v4",
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
        "model": "us.anthropic.claude-sonnet-4-20250514-v1:0",
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

If using AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v4",
    "description": "Test connector for Amazon Bedrock claude v4",
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
        "model": "us.anthropic.claude-sonnet-4-20250514-v1:0",
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
    "connector_id":"5kxp_5YBIvu8EdWRQuez"
}
```

### 2.2 Register model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "anthropic.claude-v4",
    "function_name": "remote",
    "description": "claude v4 model",
    "connector_id": "5kxp_5YBIvu8EdWRQuez"
}
```

Sample response:
```json
{
    "task_id":"6kxq_5YBIvu8EdWRwedJ",
    "status":"CREATED",
    "model_id":"7Exq_5YBIvu8EdWRwefI"
}
```

### 2.3 Test model inference

```json
POST /_plugins/_ml/models/7Exq_5YBIvu8EdWRwefI/_predict
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
    "inference_results": [{
        "output": [{
            "name": "response",
            "dataAsMap": {
                "id": "msg_bdrk_017wv2bnUmKroe7C48MHdu32",
                "type": "message",
                "role": "assistant",
                "model": "claude-sonnet-4-20250514",
                "content": [{
                    "type": "text",
                    "text": "Hello! Nice to meet you. How are you doing today? Is there anything I can help you with?"
                }],
                "stop_reason": "end_turn",
                "stop_sequence": null,
                "usage": {
                    "input_tokens": 9.0,
                    "cache_creation_input_tokens": 0.0,
                    "cache_read_input_tokens": 0.0,
                    "output_tokens": 25.0
                }
            }
        }],
        "status_code": 200
    }]
}
```

## <a id="section3"></a>3. Extended thinking mode

Extended thinking mode allows Claude 4 to perform more in-depth reasoning before providing a response. Note that `budget_tokens` can be specified in parameters, which determines the number of tokens Claude can use for its internal reasoning process. See Claude [documentation](https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking#how-to-use-extended-thinking) for more details.

### 3.1 Create connector

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v4",
    "description": "Test connector for Amazon Bedrock claude v4",
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
        "model": "us.anthropic.claude-sonnet-4-20250514-v1:0",
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
    "name": "Amazon Bedrock claude v4",
    "description": "Test connector for Amazon Bedrock claude v4",
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
        "model": "us.anthropic.claude-sonnet-4-20250514-v1:0",
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
    "connector_id":"DEx5_5YBIvu8EdWRTOiq"
}
```

### 3.2 Register model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "anthropic.claude-v4",
    "function_name": "remote",
    "description": "claude v4 model with extended thinking",
    "connector_id": "DEx5_5YBIvu8EdWRTOiq"
}
```

Sample response:
```json
{
    "task_id":"DUx6_5YBIvu8EdWRLuj1",
    "status":"CREATED",
    "model_id":"Dkx6_5YBIvu8EdWRL-gO"
}
```

### 3.3 Test model inference

```json
POST /_plugins/_ml/models/Dkx6_5YBIvu8EdWRL-gO/_predict
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
    "inference_results": [{
        "output": [{
            "name": "response",
            "dataAsMap": {
                "id": "msg_bdrk_0117MNj2HVP7dXmeDGeCSQaL",
                "type": "message",
                "role": "assistant",
                "model": "claude-sonnet-4-20250514",
                "content": [{
                    "type": "thinking",
                    "thinking": "The user has sent me a simple \"hello world\" message. This is a classic, friendly greeting that's often used as a first program or test message in programming and casual conversation. I should respond in a warm, welcoming way.",
                    "signature": "<THOUGHT_SIGNATURE>"
                }, {
                    "type": "text",
                    "text": "Hello! It's nice to meet you. How are you doing today? Is there anything I can help you with?"
                }],
                "stop_reason": "end_turn",
                "stop_sequence": null,
                "usage": {
                    "input_tokens": 37.0,
                    "cache_creation_input_tokens": 0.0,
                    "cache_read_input_tokens": 0.0,
                    "output_tokens": 84.0
                }
            }
        }],
        "status_code": 200
    }]
}
```
