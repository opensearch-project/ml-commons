# Bedrock connector blueprint example for Cohere embed-english-v3 model

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
    "name": "Amazon Bedrock Connector: Cohere embed-english-v3",
    "description": "Test connector for Amazon Bedrock Cohere embed-english-v3",
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
        "model": "cohere.embed-english-v3"
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
      "model": "cohere.embed-english-v3"
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
  "connector_id": "hijwwZABNrAVdFa9prf7"
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
    "name": "cohere.embed-english-v3",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "cohere embed-english v3 model",
    "connector_id": "hijwwZABNrAVdFa9prf7"
}
```

Sample response:
```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "lyjxwZABNrAVdFa9zrcZ"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/rcormY8B8aiZvtEZIe89/_predict
{
  "parameters": {
    "texts" : ["Hello world", "This is a test"]
  }
}
```
or
```json
POST /_plugins/_ml/_predict/text_embedding/rcormY8B8aiZvtEZIe89
{
    "text_docs":[ "today is sunny", "today is sunny"],
    "return_number": true,
    "target_response": ["sentence_embedding"]
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
            -0.029205322,
            -0.02357483,
            -0.05987549,
            -0.05819702,
            -0.03540039,
            ...
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
