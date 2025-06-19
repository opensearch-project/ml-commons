# OpenAI connector standard blueprint example for embedding model

This blueprint demonstrates how to deploy a text-embedding-ada-002 using the OpenAI connector without pre and post processing functions.
This is recommended for version after OS 2.14.0 for models to use the ML inference processor to handle input/output mapping.
Note that if using a model that requires pre and post processing functions, you must provide the functions in the blueprint. Please refer to legacy blueprint: [Bedrock connector blueprint example for Cohere embed-english-v3 model](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_cohere_cohere.embed-english-v3_blueprint.md)

## 1. Create connector for OpenAI embedding model:

Refer to OpenAI [official doc](https://platform.openai.com/docs/guides/embeddings).

If you are using self-managed Opensearch, you should supply OpenAI API key:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "OpenAI Connector: embedding",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "parameters": {
    "model": "text-embedding-ada-002"
  },
  "credential": {
    "openAI_key": "<PLEASE ADD YOUR OPENAI API KEY HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.openai.com/v1/embeddings",
      "headers": {
        "Authorization": "Bearer ${credential.openAI_key}"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\" }"
    }
  ]
}
```

If using the AWS Opensearch Service, you can provide Secret ARN and IAM role arn that allows access to the Secret ARN.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-external-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "parameters": {
    "model": "text-embedding-ada-002"
  },
  "credential": {
    "secretArn": "<YOUR SECRET ARN>",
    "roleArn": "<YOUR IAM ROLE ARN>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.openai.com/v1/embeddings",
      "headers": {
        "Authorization": "Bearer ${credential.secretArn.<YOUR OPENAI SECRET KEY IN SECRET MANAGER>}"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\" }"
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "OyB0josB2yd36FqHy3lO"
}
```

## 2. Create model group:

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
  "model_group_id": "TWR0josByE8GuSOJ629m",
  "status": "CREATED"
}
```

## 3. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "OpenAI embedding model",
  "function_name": "remote",
  "model_group_id": "TWR0josByE8GuSOJ629m",
  "description": "test model",
  "connector_id": "OyB0josB2yd36FqHy3lO"
}
```

Sample response:
```json
{
  "task_id": "HA-ojJUB_BtQcl4FDchE",
  "status": "CREATED",
  "model_id": "yQ9ZlZUB_BtQcl4Fp-UN"
}
```

Model should be deployed already. in this demo the model id is `Ir_6HIwBpSwPfTzcmemX`
If we still need to deploy the model

```json
POST /_plugins/_ml/models/yQ9ZlZUB_BtQcl4Fp-UN/_deploy
```

```json
{
    "task_id": "IQ-ojJUB_BtQcl4FyMhB",
    "task_type": "DEPLOY_MODEL",
    "status": "COMPLETED"
}
```

## 4. Test model inference

Get Text Embedding:

```json
POST /_plugins/_ml/models/yQ9ZlZUB_BtQcl4Fp-UN/_predict
{
  "parameters": {
    "input": ["Say this is a test"]
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
                        "object": "list",
                        "data": [
                            {
                                "object": "embedding",
                                "index": 0.0,
                                "embedding": [
                                    -0.014577465,
                                    -2.4000366E-4,
                                    0.0035545405,
                                    -0.002179883,
                                    ...
                                ]
                            }
                        ],
                      "model": "text-embedding-ada-002-v2",
                      "usage": {
                        "prompt_tokens": 5.0,
                        "total_tokens": 5.0
                      }
                    }
                }
            ],
          "status_code": 200
        }
    ]
}
```