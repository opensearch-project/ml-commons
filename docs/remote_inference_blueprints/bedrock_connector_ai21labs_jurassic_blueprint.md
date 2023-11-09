# Bedrock connector blueprint example for AI21 Labs Jurassic-2 Mid model

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
    "name": "Amazon Bedrock",
    "description": "Test connector for Amazon Bedrock",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
        "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "model_name": "ai21.j2-mid-v1"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
           "request_body": "{\r\n\"prompt\":\"${parameters.inputs}\",\r\n\"maxTokens\":200,\r\n\"temperature\":0.7,\r\n\"topP\":1,\r\n\"stopSequences\":[\r\n\r\n],\r\n\"countPenalty\":{\r\n\"scale\":0\r\n},\r\n\"presencePenalty\":{\r\n\"scale\":0\r\n},\r\n\"frequencyPenalty\":{\r\n\"scale\":0\r\n}\r\n}",
           "post_process_function": "\n  return params['completions'][0].data.text; \n"
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
        "model_name": "ai21.j2-mid-v1"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
           "request_body": "{\r\n\"prompt\":\"${parameters.inputs}\",\r\n\"maxTokens\":200,\r\n\"temperature\":0.7,\r\n\"topP\":1,\r\n\"stopSequences\":[\r\n\r\n],\r\n\"countPenalty\":{\r\n\"scale\":0\r\n},\r\n\"presencePenalty\":{\r\n\"scale\":0\r\n},\r\n\"frequencyPenalty\":{\r\n\"scale\":0\r\n}\r\n}",
           "post_process_function": "\n  return params['completions'][0].data.text; \n"
        }
    ]
}
```

Sample response:
```json
{
  "connector_id": "ya7VtYsB7ksezaHUlnwc"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
    "description": "This is an example description"
}
```

Sample response:
```json
{
  "model_group_id": "yK7UtYsB7ksezaHU_nyt",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
    "name": "anthropic.claude-v2",
    "function_name": "remote",
    "model_group_id": "yK7UtYsB7ksezaHU_nyt",
    "description": "test model",
    "connector_id": "ya7VtYsB7ksezaHUlnwc"
}
```

Sample response:
```json
{
  "task_id": "yq7WtYsB7ksezaHUSHyZ",
  "status": "CREATED",
  "model_id": "y67WtYsB7ksezaHUSHzq"
}```

Get model id from task
```json
GET /_plugins/_ml/tasks/yq7WtYsB7ksezaHUSHyZ
```
Deploy model, in this demo the model id is `y67WtYsB7ksezaHUSHzq`

```json
POST /_plugins/_ml/models/y67WtYsB7ksezaHUSHzq/_deploy
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/y67WtYsB7ksezaHUSHzq/_predict
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
            "response": """
The meaning of life is a question that has puzzled philosophers and theologians for centuries. There is no single, definitive answer, as different people may have different meanings in life. However, some common themes include the quest for happiness, the desire for personal growth and fulfillment, and the quest to find purpose and meaning in one's existence. Ultimately, the meaning of life is a personal and subjective matter, and each individual must determine their own sense of purpose and fulfillment."""
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```