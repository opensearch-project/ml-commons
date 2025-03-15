# Bedrock connector standard blueprint example for Titan embedding model

This blueprint demonstrates how to deploy a Titan embedding model v1 and v2 using the Bedrock connector without pre and post processing functions. 
This is recommended for models to use the ML inference processor to handle input/output mapping. 
Note that if using a model that requires pre and post processing functions, you must provide the functions in the blueprint. Please refer to legacy blueprint: [Bedrock connector blueprint example for Titan embedding model](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_titan_embedding_blueprint.md)

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

Sample response:
```json
{
  "acknowledged": true,
  "persistent": {
    "plugins": {
      "ml_commons": {
        "trusted_connector_endpoints_regex": [
          "^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
        ]
      }
    }
  },
  "transient": {}
}
```

## 2. Create connector for Amazon Bedrock:
### 2.1 Titan text embedding model v1
If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: embedding",
  "description": "The connector to bedrock Titan embedding model",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "service_name": "bedrock",
    "model": "amazon.titan-embed-text-v1"
  },
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{ \"inputText\": \"${parameters.inputText}\" }",
      "pre_process_function": "connector.pre_process.bedrock.embedding",
      "post_process_function": "connector.post_process.bedrock.embedding"
    }
  ]
}
```

Sample response:
```json
{
    "connector_id": "oQ9Lh5UB_BtQcl4F-8E3"
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: embedding",
  "description": "The connector to bedrock Titan embedding model",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "service_name": "bedrock",
    "model": "amazon.titan-embed-text-v1"
  },
  "credential": {
    "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{ \"inputText\": \"${parameters.inputText}\" }"
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "nzh9PIsBnGXNcxYpPEcv"
}
```


### 2.2 Titan text embedding model v2

Follow Titan text embedding model v1, just change "model" to `amazon.titan-embed-text-v2:0` and configure extra parameters and request body as:


```
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: embedding",
  "description": "The connector to bedrock Titan embedding model",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "service_name": "bedrock",
    "model": "amazon.titan-embed-text-v2:0",
    "dimensions": 1024,
    "normalize": true,
    "embeddingTypes": ["float"]
  },
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"   "request_body": "{ \"inputText\": \"${parameters.inputText}\", \"dimensions\": ${parameters.dimensions}, \"normalize\": ${parameters.normalize}, \"embeddingTypes\": ${parameters.embeddingTypes} }"
  
      },
     }
  ]
}
```
Note:
1. for v2 embedding API, different from v1, you should supply `embeddingTypes` in request body, bedrock accepts `float` or `binary`. But if using neural-search plugin, neural-search plugin only support one embedding for one document now. So you should configure one embedding type in `embeddingTypes`. 
2. similar to v1, you should use `roleArn` in credential part on AWS OpenSearch Service

Sample response:
```json
{
  "connector_id": "nzh9PIsBnGXNcxYpPEcv"
}
```

## 3. Register model & deploy model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "Bedrock embedding model",
  "function_name": "remote",
  "description": "test model",
  "connector_id": "nzh9PIsBnGXNcxYpPEcv"
}
```
Sample response:
```json

{
    "task_id": "ow9Wh5UB_BtQcl4FkMEI",
    "status": "CREATED",
    "model_id": "pA9Wh5UB_BtQcl4FkMEo"
}
```
Get model id from response. Deploy model, in this demo the model id is `pA9Wh5UB_BtQcl4FkMEo`

```json
POST /_plugins/_ml/models/pA9Wh5UB_BtQcl4FkMEo/_deploy
```
Sample response:

```json
{
    "task_id": "aQ9QjJUB_BtQcl4FS8dH",
    "task_type": "DEPLOY_MODEL",
    "status": "COMPLETED"
}
```

## 4. Test model inference

```json
POST /_plugins/_ml/models/pA9Wh5UB_BtQcl4FkMEo/_predict
{
  "parameters": {
    "inputText": "What is the meaning of life?"
  }
}
```

Sample response of Titan Text Embedding V1:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "embedding": [
            0.41992188,
            -0.7265625,
            -0.080078125,
            ...
            ],
            "inputTextTokenCount": 7.0
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

Sample response of Titan Text Embedding V2:
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "embedding": [
            -0.041385926,
            0.08503958,
            0.0026220535,
            ...
            ],
            "embeddingsByType": {
              "float": [
              -0.04138592630624771,
              0.08503957837820053,
              0.0026220534928143024, 
              ...
              ]
            },
            "inputTextTokenCount": 8.0
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```