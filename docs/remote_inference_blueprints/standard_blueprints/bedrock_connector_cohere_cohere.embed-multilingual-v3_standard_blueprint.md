# Bedrock connector standard blueprint example for Cohere embed-multilingual-v3 model
This blueprint demonstrates how to deploy a cohere.embed-multilingual-v3 using the Bedrock connector without pre and post processing functions.
This is recommended for version after OS 2.14.0 for models to use the ML inference processor to handle input/output mapping.
Note that if using a model that requires pre and post processing functions, you must provide the functions in the blueprint. Please refer to legacy blueprint: [Bedrock connector blueprint example for Cohere embed-multilingual-v3 model](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_cohere_cohere.embed-multilingual-v3_blueprint.md)

## 1. Create connector for Amazon Bedrock:

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
            "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"input_type\": \"${parameters.input_type}\" }"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Connector: Cohere embed-multilingual-v3",
    "description": "Test connector for Amazon Bedrock Cohere embed-multilingual-v3 model",
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
          "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"input_type\": \"${parameters.input_type}\" }"
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
For more information of the model inference parameters in the connector, please refer to this [AWS doc](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html)

## 2. Create model group(optional):

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_cohere",
    "description": "model group for cohere models"
}
```

Sample response:
```json
{
  "model_group_id": "IMobmY8B8aiZvtEZeO_i",
  "status": "CREATED"
}
```

## 3. Register model to model group & deploy model:

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

## 4. Test model inference

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
                    "dataAsMap": {
                        "id": "eef43d65-1328-47d6-a95d-7f814b6b393b",
                        "texts": [
                            "上海",
                            "This is a test"
                        ],
                        "embeddings": [
                            [
                                0.019195557,
                                0.045715332,
                                0.0022392273,
                                ...
                            ],
                          [
                            0.0012359619,
                            0.013084412,
                            -0.04067993, 
                            ...
                          ]
                        ],
                      "response_type": "embeddings_floats"
                    }
                }
            ],
          "status_code": 200
        }
    ]
}
```