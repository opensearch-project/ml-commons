# Bedrock connector blueprint example for Cohere Rerank model

This blueprint shows how to create a connector for the [Cohere Rerank v3.5](https://docs.aws.amazon.com/bedrock/latest/userguide/rerank.html) model on Amazon Bedrock. The Cohere Rerank model calculates relevance scores for documents with respect to a query, enabling you to reorder search results for improved accuracy in RAG pipelines and enterprise search.

For a complete end-to-end tutorial including search pipeline setup, see [Reranking search results using Cohere Rerank on Amazon Bedrock](https://opensearch.org/docs/latest/tutorials/reranking/reranking-cohere-bedrock/).

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

### 2.1 Self-managed OpenSearch with AWS credentials

If you are using self-managed OpenSearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Connector: Cohere Rerank",
    "description": "Connector for Cohere Rerank v3.5 model on Amazon Bedrock",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
        "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
        "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
    },
    "parameters": {
        "service_name": "bedrock",
        "endpoint": "bedrock-runtime",
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "model_name": "cohere.rerank-v3-5:0",
        "api_version": 2
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "x-amz-content-sha256": "required",
                "content-type": "application/json"
            },
            "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
            "request_body": "{ \"documents\": ${parameters.documents}, \"query\": \"${parameters.query}\", \"api_version\": ${parameters.api_version} }",
            "pre_process_function": "connector.pre_process.cohere.rerank",
            "post_process_function": "connector.post_process.cohere.rerank"
        }
    ]
}
```

### 2.2 AWS OpenSearch Service with IAM role

If using the AWS OpenSearch Service, you can provide an IAM role ARN that allows access to the Amazon Bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html).

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Connector: Cohere Rerank",
    "description": "Connector for Cohere Rerank v3.5 model on Amazon Bedrock",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "service_name": "bedrock",
        "endpoint": "bedrock-runtime",
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "model_name": "cohere.rerank-v3-5:0",
        "api_version": 2
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "x-amz-content-sha256": "required",
                "content-type": "application/json"
            },
            "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
            "request_body": "{ \"documents\": ${parameters.documents}, \"query\": \"${parameters.query}\", \"api_version\": ${parameters.api_version} }",
            "pre_process_function": "connector.pre_process.cohere.rerank",
            "post_process_function": "connector.post_process.cohere.rerank"
        }
    ]
}
```

Sample response:
```json
{
    "connector_id": "aB1cD2eF3gH4iJ5kL6mN"
}
```

### 2.3 Using custom Painless scripts (for OpenSearch versions before built-in function support)

If your OpenSearch version does not support the built-in `connector.pre_process.cohere.rerank` and `connector.post_process.cohere.rerank` functions, you can use custom Painless scripts instead:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Connector: Cohere Rerank",
    "description": "Connector for Cohere Rerank v3.5 model on Amazon Bedrock",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
        "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
        "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
    },
    "parameters": {
        "service_name": "bedrock",
        "endpoint": "bedrock-runtime",
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "model_name": "cohere.rerank-v3-5:0",
        "api_version": 2
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "x-amz-content-sha256": "required",
                "content-type": "application/json"
            },
            "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
            "request_body": "{ \"documents\": ${parameters.documents}, \"query\": \"${parameters.query}\", \"api_version\": ${parameters.api_version} }",
            "pre_process_function": "\n    def query_text = params.query_text;\n    def text_docs = params.text_docs;\n    def textDocsBuilder = new StringBuilder('[');\n    for (int i=0; i<text_docs.length; i++) {\n      textDocsBuilder.append('\"');\n      textDocsBuilder.append(text_docs[i]);\n      textDocsBuilder.append('\"');\n      if (i<text_docs.length - 1) {\n        textDocsBuilder.append(',');\n      }\n    }\n    textDocsBuilder.append(']');\n    def parameters = '{ \"query\": \"' + query_text + '\",  \"documents\": ' + textDocsBuilder.toString() + ' }';\n    return  '{\"parameters\": ' + parameters + '}';\n  ",
            "post_process_function": "\n    if (params.results == null || params.results.length == 0) {\n      throw new IllegalArgumentException(\"Post process function input is empty.\");\n    }\n    def outputs = params.results;\n    def relevance_scores = new Double[outputs.length];\n    for (int i=0; i<outputs.length; i++) {\n      def index = new BigDecimal(outputs[i].index.toString()).intValue();\n      relevance_scores[index] = outputs[i].relevance_score;\n    }\n    def resultBuilder = new StringBuilder('[');\n    for (int i=0; i<relevance_scores.length; i++) {\n      resultBuilder.append(' {\"name\": \"similarity\", \"data_type\": \"FLOAT32\", \"shape\": [1],');\n      resultBuilder.append('\"data\": [');\n      resultBuilder.append(relevance_scores[i]);\n      resultBuilder.append(']}');\n      if (i<outputs.length - 1) {\n        resultBuilder.append(',');\n      }\n    }\n    resultBuilder.append(']');\n    return resultBuilder.toString();\n  "
        }
    ]
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_rerank",
    "description": "Model group for reranking models"
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
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Cohere Rerank v3.5",
    "function_name": "remote",
    "model_group_id": "rqR9PIsBQRofe4CScErR",
    "description": "Cohere Rerank v3.5 model for reranking search results",
    "connector_id": "aB1cD2eF3gH4iJ5kL6mN"
}
```

Sample response:
```json
{
    "task_id": "r6R9PIsBQRofe4CSlUoG",
    "status": "CREATED",
    "model_id": "sKR9PIsBQRofe4CSlUov"
}
```

## 5. Test model inference

You can test the model using the Predict API:

```json
POST /_plugins/_ml/models/sKR9PIsBQRofe4CSlUov/_predict
{
    "parameters": {
        "query": "What is the capital city of America?",
        "documents": [
            "Carson City is the capital city of the American state of Nevada.",
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
            "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
        ]
    }
}
```

Alternatively, you can test the model using the text similarity API:

```json
POST /_plugins/_ml/_predict/text_similarity/sKR9PIsBQRofe4CSlUov
{
    "query_text": "What is the capital city of America?",
    "text_docs": [
        "Carson City is the capital city of the American state of Nevada.",
        "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
        "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
        "Capital punishment (the death penalty) has existed in the United States since before the United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
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
                    "shape": [1],
                    "data": [0.32418242]
                },
                {
                    "name": "similarity",
                    "data_type": "FLOAT32",
                    "shape": [1],
                    "data": [0.07456104]
                },
                {
                    "name": "similarity",
                    "data_type": "FLOAT32",
                    "shape": [1],
                    "data": [0.7190094]
                },
                {
                    "name": "similarity",
                    "data_type": "FLOAT32",
                    "shape": [1],
                    "data": [0.06124987]
                }
            ],
            "status_code": 200
        }
    ]
}
```

The `post_process_function` reorders the results by document index (not by relevance score), so the scores correspond to the original document order. The highest score (`0.7190094`) belongs to the third document ("Washington, D.C. ..."), which correctly answers the query.

## 6. Use with reranking search pipeline

You can use the deployed rerank model in a [reranking pipeline](https://opensearch.org/docs/latest/search-plugins/search-pipelines/rerank-processor/) to rerank search results:

```json
PUT /_search/pipeline/rerank_pipeline_bedrock
{
    "description": "Pipeline for reranking with Bedrock Cohere Rerank model",
    "response_processors": [
        {
            "rerank": {
                "ml_opensearch": {
                    "model_id": "sKR9PIsBQRofe4CSlUov"
                },
                "context": {
                    "document_fields": ["passage_text"]
                }
            }
        }
    ]
}
```

Test the reranking pipeline with a search query:

```json
POST /my-index/_search?search_pipeline=rerank_pipeline_bedrock
{
    "query": {
        "match": {
            "passage_text": "What is the capital city of America?"
        }
    },
    "ext": {
        "rerank": {
            "query_context": {
                "query_text": "What is the capital city of America?"
            }
        }
    }
}
```

To avoid writing the query twice, you can use `query_text_path` instead of `query_text`:

```json
POST /my-index/_search?search_pipeline=rerank_pipeline_bedrock
{
    "query": {
        "match": {
            "passage_text": "What is the capital city of America?"
        }
    },
    "ext": {
        "rerank": {
            "query_context": {
                "query_text_path": "query.match.passage_text.query"
            }
        }
    }
}
```

## Notes

1. **Model ID**: The Cohere Rerank v3.5 model ID on Bedrock is `cohere.rerank-v3-5:0`. Check [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/rerank-supported.html) for the latest available model versions.
2. **Region availability**: Cohere Rerank on Bedrock is available in select regions. Verify availability in your target region before creating the connector.
3. **Built-in functions**: The `connector.pre_process.cohere.rerank` and `connector.post_process.cohere.rerank` functions are available starting from OpenSearch 2.19. For earlier versions, use the custom Painless scripts shown in section 2.3.
4. **IAM permissions**: Ensure your IAM role or credentials have `bedrock:InvokeModel` permission for the Cohere Rerank model.
5. **Use case**: This connector is particularly useful for RAG pipelines where you want to rerank retrieved documents before passing them to a generative model, improving response quality while reducing token usage.
6. **Document fields**: If you provide multiple field names in `document_fields`, the values of all fields are concatenated before reranking is performed.
