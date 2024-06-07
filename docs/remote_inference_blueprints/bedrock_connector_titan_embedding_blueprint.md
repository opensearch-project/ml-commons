# Bedrock connector blueprint example for Titan embedding model

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

If you are using Titan Text Embedding V2, change "model" to `amazon.titan-embed-text-v2:0`
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
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/amazon.titan-embed-text-v1/invoke",
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

If you are using AWS OpenSearch Service version 2.11, there are no built-in functions for pre_process_function and post_process_function.
So, you need to add the script as shown below.

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
      "url": "https://bedrock-runtime.us-east-1.amazonaws.com/model/amazon.titan-embed-text-v1/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{ \"inputText\": \"${parameters.inputText}\" }",
      "pre_process_function": """
        StringBuilder builder = new StringBuilder();
        builder.append("\"");
        String first = params.text_docs[0];
        if (first.contains("\"")) {
          first = first.replace("\"", "\\\"");
        }
        if (first.contains("\\t")) {
          first = first.replace("\\t", "\\\\\\t");
        }
        if (first.contains('')) {
          first = first.replace('', '\\n');
        }
        builder.append(first);
        builder.append("\"");
        def parameters = "{" +"\"inputText\":" + builder + "}";
        return "{" +"\"parameters\":" + parameters + "}";
      """,
      "post_process_function": """
        def name = "sentence_embedding";
        def dataType = "FLOAT32";
        if (params.embedding == null || params.embedding.length == 0) {
          return params.message;
        }
        def shape = [params.embedding.length];
        def json = "{" +
                  "\"name\":" + "\"" + name + "\"" + "," +
                  "\"data_type\":" + "\"" + dataType + "\"" + "," +
                  "\"shape\":" + shape + "," +
                  "\"data\":" + params.embedding +
                  "}";
        return json;
    """
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
  "model_group_id": "rqR9PIsBQRofe4CScErR",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "Bedrock embedding model",
  "function_name": "remote",
  "model_group_id": "rqR9PIsBQRofe4CScErR",
  "description": "test model",
  "connector_id": "nzh9PIsBnGXNcxYpPEcv"
}
```

Sample response:
```json
{
  "task_id": "r6R9PIsBQRofe4CSlUoG",
  "status": "CREATED"
}
```
Get model id from task
```json
GET /_plugins/_ml/tasks/r6R9PIsBQRofe4CSlUoG
```
Deploy model, in this demo the model id is `sKR9PIsBQRofe4CSlUov`
```json
POST /_plugins/_ml/models/sKR9PIsBQRofe4CSlUov/_deploy
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/sKR9PIsBQRofe4CSlUov/_predict
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1536
          ],
          "data": [
            0.41992188,
            -0.7265625,
            -0.080078125,
            ...
          ]
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            -0.041385926,
            0.08503958,
            0.0026220535,
            ...
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

