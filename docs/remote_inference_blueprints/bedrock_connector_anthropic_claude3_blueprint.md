# Bedrock connector blueprint example for Claude V3 model

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

## 2. Create connector for Amazon Bedrock:

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v3",
    "description": "Test connector for Amazon Bedrock claude v3",
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
        "auth": "Sig_V4",
        "response_filter": "$.content[0].text",
        "max_tokens_to_sample": "8000",
        "anthropic_version": "bedrock-2023-05-31",
        "model": "anthropic.claude-3-sonnet-20240229-v1:0"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${parameters.inputs}\"}]}],\"anthropic_version\":\"${parameters.anthropic_version}\",\"max_tokens\":${parameters.max_tokens_to_sample}}"
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
        "service_name": "bedrock",
        "auth": "Sig_V4",
        "response_filter": "$.content[0].text",
        "max_tokens_to_sample": "8000",
        "anthropic_version": "bedrock-2023-05-31",
        "model": "anthropic.claude-3-sonnet-20240229-v1:0"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.us-east-1.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${parameters.inputs}\"}]}],\"anthropic_version\":\"${parameters.anthropic_version}\",\"max_tokens\":${parameters.max_tokens_to_sample}}"
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
    "name": "remote_model_group_claude3",
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
    "name": "anthropic.claude-v3",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "claude v3 model",
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
    "inputs": "What is the meaning of life?"
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
            "response": "There is no single, universally accepted answer to the meaning of life. It's a question that has been pondered by philosophers, theologians, and thinkers across cultures for centuries. Here are some of the major perspectives on deriving meaning in life:\n\n- Religious/spiritual views - Many religions provide a framework for finding meaning through connection to the divine, fulfilling religious teachings/duties, and an afterlife.\n\n- Existentialist philosophy - Thinkers like Sartre and Camus emphasized that we each have the freedom and responsibility to create our own subjective meaning in an objectively meaningless universe.\n\n- Hedonism - The view that the pursuit of pleasure and avoiding suffering is the highest good and most meaningful way to live.\n\n- Virtue ethics - Finding meaning through living an ethical life based on virtues like courage, temperance, justice, and wisdom.\n\n- Humanistic psychology - Psychologists like Maslow and Frankl emphasized fulfillment from reaching one's full human potential and finding a sense of purpose.\n\n- Naturalism/Nihilism - Some believe life itself has no inherent meaning beyond the physical/natural world we empirically experience.\n\nUltimately, the \"meaning of life\" is an existential question that challenges each individual to decide what makes their own life feel meaningful, based on their own worldview, beliefs, and values. There is no objectively \"correct\" universal answer."
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
