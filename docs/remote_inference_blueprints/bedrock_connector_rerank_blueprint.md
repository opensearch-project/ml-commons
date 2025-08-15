# Bedrock connector blueprint example for Rerank

Note: This blueprint is available in OpenSearch since 2.19.0.

## 1. Add connector endpoint to trusted URLs:

Note: This step should be skippable since 2.16.0.

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://bedrock-agent-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for Amazon Bedrock Rerank:

If you are using self-managed OpenSearch, supply your AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Rerank API",
  "description": "Test connector for Amazon Bedrock Rerank API",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
      "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
      "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
      "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "parameters": {
    "service_name": "bedrock",
    "endpoint": "bedrock-agent-runtime",
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "api_name": "rerank",
    "model_id": "amazon.rerank-v1:0"
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "POST",
      "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/${parameters.api_name}",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "pre_process_function": "connector.pre_process.bedrock.rerank",
      "request_body": """
        {
          "queries": ${parameters.queries},
          "rerankingConfiguration": {
            "bedrockRerankingConfiguration": {
              "modelConfiguration": {
                "modelArn": "arn:aws:bedrock:${parameters.region}::foundation-model/${parameters.model_id}"
              }
            },
            "type": "BEDROCK_RERANKING_MODEL"
          },
          "sources": ${parameters.sources}
        }
      """,
      "post_process_function": "connector.post_process.bedrock.rerank"
    }
  ]
}
```

If you are using Amazon OpenSearch Service, you can provide an AWS Identity and Access Management (IAM) role Amazon Resource Name (ARN) that allows access to Amazon Bedrock. For more information, see the [AWS documentation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html). Use the following request to create a connector:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Rerank API",
  "description": "Test connector for Amazon Bedrock Rerank API",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
  },
  "parameters": {
    "service_name": "bedrock",
    "endpoint": "bedrock-agent-runtime",
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "api_name": "rerank",
    "model_id": "amazon.rerank-v1:0"
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "POST",
      "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/${parameters.api_name}",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "pre_process_function": "connector.pre_process.bedrock.rerank",
      "request_body": """
        {
          "queries": ${parameters.queries},
          "rerankingConfiguration": {
            "bedrockRerankingConfiguration": {
              "modelConfiguration": {
                "modelArn": "arn:aws:bedrock:${parameters.region}::foundation-model/${parameters.model_id}"
              }
            },
            "type": "BEDROCK_RERANKING_MODEL"
          },
          "sources": ${parameters.sources}
        }
      """,
      "post_process_function": "connector.post_process.bedrock.rerank"
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
  "name": "remote_model_group_rerank",
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
  "name": "Amazon Bedrock Rerank API",
  "function_name": "remote",
  "model_group_id": "IMobmY8B8aiZvtEZeO_i",
  "description": "test Amazon Bedrock Rerank API",
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
POST _plugins/_ml/_predict/text_similarity/rcormY8B8aiZvtEZIe89
{
  "query_text": "What is the capital city of America?",
  "text_docs": [
    "Carson City is the capital city of the American state of Nevada.",
    "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
    "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
    "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
  ]
}
```

Sample response:
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.0025114636
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            2.487649e-05
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.7711549
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            6.3392104e-06
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
