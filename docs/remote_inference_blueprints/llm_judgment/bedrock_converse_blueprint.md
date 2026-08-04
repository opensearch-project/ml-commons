# Amazon Bedrock connector blueprint example for Converse (LLM judgment)

This blueprint integrates Amazon Bedrock chat models through the [Converse API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html) for Search Relevance Workbench (SRW) LLM judgments. The Converse API uses one request and response format for every chat model on Bedrock, so this single blueprint works for Anthropic Claude, Amazon Nova, Meta Llama, Mistral, AI21, Cohere, and OpenAI GPT-OSS on Bedrock — just change the `model` parameter.

It maps SRW's neutral `system_prompt` / `user_prompt` parameters into the Converse shape and adds a `post_process_function` that returns the model text in a neutral `response` field.

## Available models

The Converse API works with any chat model on Amazon Bedrock. Set the `model` parameter to the model's **cross-region inference profile ID** (the `us.` / `eu.` / `apac.` prefixed form required for on-demand invocation). Verified examples (US regions):

| Provider | Inference profile IDs |
|---|---|
| Anthropic Claude | `us.anthropic.claude-haiku-4-5-20251001-v1:0`, `us.anthropic.claude-sonnet-4-5-20250929-v1:0`, `us.anthropic.claude-opus-4-6-v1`, `us.anthropic.claude-opus-4-8` |
| Amazon Nova | `us.amazon.nova-pro-v1:0`, `us.amazon.nova-lite-v1:0`, `us.amazon.nova-micro-v1:0` |
| Meta Llama | `us.meta.llama3-3-70b-instruct-v1:0` |

Converse also supports additional models (Cohere Command, Mistral, AI21, OpenAI GPT-OSS on Bedrock, and more). The exact ID format differs per model, so always copy the precise ID from the model's AWS card rather than guessing. For the complete, current catalog and each model's exact ID and Regional availability, see the [Amazon Bedrock supported models](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html), or list what is enabled for your account with:

```bash
aws bedrock list-inference-profiles --region <region> \
  --query "inferenceProfileSummaries[].inferenceProfileId"
```

## 1. Add connector endpoint to trusted URLs:
Note: this step is only necessary for OpenSearch versions prior to 2.11.0.

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

If you are using self-managed OpenSearch, supply your AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Converse",
    "description": "Amazon Bedrock Converse for SRW LLM judgments",
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
        "model": "<INFERENCE_PROFILE_ID>"  // example: us.anthropic.claude-haiku-4-5-20251001-v1:0
    },
    "client_config": {
        "max_retry_times": 3,
        "retry_backoff_policy": "exponential_full_jitter"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
            "request_body": "{\"system\":[{\"text\":\"${parameters.system_prompt}\"}],\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.user_prompt}\"}]}],\"inferenceConfig\":{\"maxTokens\":${parameters.max_tokens}}}",
            "post_process_function": "def text = params.output.message.content[0].text; return '{\"name\":\"response\",\"dataAsMap\":{\"response\":\"' + escape(text) + '\"}}'"
        }
    ]
}
```

If you are using the AWS OpenSearch Service, you can provide an IAM role ARN that allows access to the Bedrock service. Refer to the [AWS documentation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html).

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Converse",
    "description": "Amazon Bedrock Converse for SRW LLM judgments",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "max_tokens": 8000,
        "model": "<INFERENCE_PROFILE_ID>"  // example: us.anthropic.claude-haiku-4-5-20251001-v1:0
    },
    "client_config": {
        "max_retry_times": 3,
        "retry_backoff_policy": "exponential_full_jitter"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
            "request_body": "{\"system\":[{\"text\":\"${parameters.system_prompt}\"}],\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.user_prompt}\"}]}],\"inferenceConfig\":{\"maxTokens\":${parameters.max_tokens}}}",
            "post_process_function": "def text = params.output.message.content[0].text; return '{\"name\":\"response\",\"dataAsMap\":{\"response\":\"' + escape(text) + '\"}}'"
        }
    ]
}
```

#### Sample response
```json
{
  "connector_id": "<CONNECTOR_ID>"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_llm_judgment",
    "description": "Model group for SRW LLM judgment models"
}
```

#### Sample response
```json
{
  "model_group_id": "<MODEL_GROUP_ID>",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Amazon Bedrock Converse model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Amazon Bedrock Converse for SRW LLM judgments",
    "connector_id": "<CONNECTOR_ID>"
}
```

#### Sample response
```json
{
  "task_id": "<TASK_ID>",
  "status": "CREATED",
  "model_id": "<MODEL_ID>"
}
```

## 5. Test model inference

SRW emits the neutral `system_prompt` and `user_prompt`; this blueprint maps them into the Converse `system` array and `messages` content.

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "system_prompt": "You are a relevance rater. Reply with strict JSON only.",
    "user_prompt": "Rate the relevance of {\"id\":\"001\",\"text\":\"banana smoothie\"} to query banana on a 0.0-1.0 scale. Return {\"ratings\":[{\"id\":\"001\",\"rating_score\":<num>}]}."
  }
}
```

#### Sample response
The `post_process_function` copies `output.message.content[0].text` into the neutral `response` field that SRW reads.

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "response": "{\"ratings\":[{\"id\":\"001\",\"rating_score\":0.9}]}"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
