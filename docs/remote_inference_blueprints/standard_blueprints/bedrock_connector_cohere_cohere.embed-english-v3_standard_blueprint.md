# Bedrock connector standard blueprint example for Cohere text embedding model
This blueprint demonstrates how to deploy a cohere.embed-english-v3 using the Bedrock connector without pre and post processing functions.
This is recommended for models to use the ML inference processor to handle input/output mapping.
Note that if using a model that requires pre and post processing functions, you must provide the functions in the blueprint. Please refer to legacy blueprint: [Bedrock connector blueprint example for Cohere embed-english-v3 model](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_cohere_cohere.embed-english-v3_blueprint.md)

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
    "name": "Amazon Bedrock Connector: Cohere embed-english-v3",
    "description": "Test connector for Amazon Bedrock Cohere embed-english-v3 model",
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
          "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"input_type\": \"${parameters.input_type}\" }"
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
For more information of the model inference parameters in the connector, please refer to this [AWS doc](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html)

## 3. Create model group:

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
POST /_plugins/_ml/models/lyjxwZABNrAVdFa9zrcZ/_predict
{
  "parameters": {
    "texts" : ["Hello world", "This is a test"]
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
                        "id": "2fe5b994-f343-49cb-94a5-a7c8c5af77e6",
                        "texts": [
                            "上 海",
                            "this is a test"
                        ],
                        "embeddings": [
                            [
                                -0.034820557,
                                0.013900757,
                                -0.036987305, 
                                ...
                            ],
                          [
                            -0.013885498,
                            0.009994507,
                            -0.03253174,
                            -0.024993896,
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

