# Bedrock connector blueprint example for Cohere embed-multilingual-v3 model

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
    "name": "Amazon Bedrock Connector: Cohere embed-multilingual-v3",
    "description": "Test connector for Amazon Bedrock Cohere embed-multilingual-v3",
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
        "truncate": "<NONE|START|END>",
        "input_type": "<search_document|search_query|classification|clustering>",
        "model": "cohere.embed-multilingual-v3"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "x-amz-content-sha256": "required",
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"input_type\": \"${parameters.input_type}\" }",
            "pre_process_function": "connector.pre_process.cohere.embedding",
            "post_process_function": "connector.post_process.cohere.embedding"
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
      "truncate": "<NONE|START|END>",
      "input_type": "<search_document|search_query|classification|clustering>",
      "model": "cohere.embed-multilingual-v3"
    },
    "actions": [
        {
          "action_type": "predict",
          "method": "POST",
          "headers": {
            "x-amz-content-sha256": "required",
            "content-type": "application/json"
          },
          "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
          "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"input_type\": \"${parameters.input_type}\" }",
          "pre_process_function": "connector.pre_process.cohere.embedding",
          "post_process_function": "connector.post_process.cohere.embedding"
        }
    ]
}
```

Sample response:
```json
{
  "connector_id": "ISj-wZABNrAVdFa9cLju"
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
    "name": "cohere.embed-multilingual-v3",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "cohere embed-multilingual v3 model",
    "connector_id": "ISj-wZABNrAVdFa9cLju"
}
```

Sample response:
```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "KSj-wZABNrAVdFa937iS"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/KSj-wZABNrAVdFa937iS/_predict
{
  "parameters": {
    "texts" : ["上海", "This is a test"]
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
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            0.0027389526,
            0.025527954,
            0.009681702,
            0.0018558502
            ...
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
