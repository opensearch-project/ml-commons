# Topic

[Reranking pipeline](https://opensearch.org/docs/latest/search-plugins/search-relevance/reranking-search-results/) is a feature released in OpenSearch 2.12.
It can rerank search results, providing a relevance score for each document in the search results with respect to the search query.
The relevance score is calculated by a cross-encoder model. 

This tutorial explains how to use the [Huggingface cross-encoder/ms-marco-MiniLM-L-6-v2](https://huggingface.co/cross-encoder/ms-marco-MiniLM-L-6-v2) model in a reranking pipeline. 

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 0. Deploy Model on Sagemaker
Use this code to deploy model on Sagemaker.
```python
import sagemaker
import boto3
from sagemaker.huggingface import HuggingFaceModel
sess = sagemaker.Session()
role = sagemaker.get_execution_role()

hub = {
    'HF_MODEL_ID':'cross-encoder/ms-marco-MiniLM-L-6-v2',
    'HF_TASK':'text-classification'
}
huggingface_model = HuggingFaceModel(
    transformers_version='4.37.0',
    pytorch_version='2.1.0',
    py_version='py310',
    env=hub,
    role=role, 
)
predictor = huggingface_model.deploy(
    initial_instance_count=1, # number of instances
    instance_type='ml.m5.xlarge' # ec2 instance type
)
```
Find the model inference endpoint and note it. We will use it to create connector in next step

## 1. Create Connector and Model

If you are using self-managed Opensearch, you should supply AWS credentials:
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Sagemakre cross-encoder model",
  "description": "Test connector for Sagemaker cross-encoder model",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "your_access_key",
    "secret_key": "your_secret_key",
    "session_token": "your_session_token"
  },
  "parameters": {
    "region": "your_sagemkaer_model_region_like_us-west-2",
    "service_name": "sagemaker"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "your_sagemaker_model_inference_endpoint_created_in_last_step",
      "headers": {
        "content-type": "application/json"
      },
      "request_body": "{ \"inputs\": ${parameters.inputs} }",
      "pre_process_function": "\n    String escape(def input) { \n       if (input.contains(\"\\\\\")) {\n        input = input.replace(\"\\\\\", \"\\\\\\\\\");\n      }\n      if (input.contains(\"\\\"\")) {\n        input = input.replace(\"\\\"\", \"\\\\\\\"\");\n      }\n      if (input.contains('\r')) {\n        input = input = input.replace('\r', '\\\\r');\n      }\n      if (input.contains(\"\\\\t\")) {\n        input = input.replace(\"\\\\t\", \"\\\\\\\\\\\\t\");\n      }\n      if (input.contains('\n')) {\n        input = input.replace('\n', '\\\\n');\n      }\n      if (input.contains('\b')) {\n        input = input.replace('\b', '\\\\b');\n      }\n      if (input.contains('\f')) {\n        input = input.replace('\f', '\\\\f');\n      }\n      return input;\n    }\n\n   String query = params.query_text;\n   StringBuilder builder = new StringBuilder('[');\n    \n    for (int i=0; i<params.text_docs.length; i ++) {\n      builder.append('\"');\n      builder.append(escape(query));\n      builder.append(' . ');\n      builder.append(escape(params.text_docs[i]));\n      builder.append('\"');\n      if (i<params.text_docs.length - 1) {\n        builder.append(',');\n      }\n    }\n    builder.append(']');\n    \n    def parameters = '{ \"inputs\": ' + builder + ' }';\n    return  '{\"parameters\": ' + parameters + '}';\n     ",
      "post_process_function": "\n      \n      def dataType = \"FLOAT32\";\n      \n      \n      if (params.result == null)\n      {\n          return 'no result generated';\n          //return params.response;\n      }\n      def outputs = params.result;\n      \n      \n      def resultBuilder = new StringBuilder('[ ');\n      for (int i=0; i<outputs.length; i++) {\n        resultBuilder.append(' {\"name\": \"similarity\", \"data_type\": \"FLOAT32\", \"shape\": [1],');\n        //resultBuilder.append('{\"name\": \"similarity\"}');\n        \n        resultBuilder.append('\"data\": [');\n        resultBuilder.append(outputs[i].score);\n        resultBuilder.append(']}');\n        if (i<outputs.length - 1) {\n          resultBuilder.append(',');\n        }\n      }\n      resultBuilder.append(']');\n      \n      return resultBuilder.toString();\n    "
    }
  ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the Sagemaker model inference endpoint. Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html), [tutorial](../aws/semantic_search_with_sagemaker_embedding_model.md), [connector helper notebook](../aws/AIConnectorHelper.ipynb)
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Sagemakre cross-encoder model",
  "description": "Test connector for Sagemaker cross-encoder model",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "roleArn": "your_role_arn_which_allows_access_to_sagemaker_model_inference_endpoint"
  },
  "parameters": {
    "region": "your_sagemkaer_model_region_like_us-west-2",
    "service_name": "sagemaker"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "your_sagemaker_model_inference_endpoint_created_in_last_step",
      "headers": {
        "content-type": "application/json"
      },
      "request_body": "{ \"inputs\": ${parameters.inputs} }",
      "pre_process_function": "\n    String escape(def input) { \n       if (input.contains(\"\\\\\")) {\n        input = input.replace(\"\\\\\", \"\\\\\\\\\");\n      }\n      if (input.contains(\"\\\"\")) {\n        input = input.replace(\"\\\"\", \"\\\\\\\"\");\n      }\n      if (input.contains('\r')) {\n        input = input = input.replace('\r', '\\\\r');\n      }\n      if (input.contains(\"\\\\t\")) {\n        input = input.replace(\"\\\\t\", \"\\\\\\\\\\\\t\");\n      }\n      if (input.contains('\n')) {\n        input = input.replace('\n', '\\\\n');\n      }\n      if (input.contains('\b')) {\n        input = input.replace('\b', '\\\\b');\n      }\n      if (input.contains('\f')) {\n        input = input.replace('\f', '\\\\f');\n      }\n      return input;\n    }\n\n   String query = params.query_text;\n   StringBuilder builder = new StringBuilder('[');\n    \n    for (int i=0; i<params.text_docs.length; i ++) {\n      builder.append('\"');\n      builder.append(escape(query));\n      builder.append(' . ');\n      builder.append(escape(params.text_docs[i]));\n      builder.append('\"');\n      if (i<params.text_docs.length - 1) {\n        builder.append(',');\n      }\n    }\n    builder.append(']');\n    \n    def parameters = '{ \"inputs\": ' + builder + ' }';\n    return  '{\"parameters\": ' + parameters + '}';\n     ",
      "post_process_function": "\n      \n      def dataType = \"FLOAT32\";\n      \n      \n      if (params.result == null)\n      {\n          return 'no result generated';\n          //return params.response;\n      }\n      def outputs = params.result;\n      \n      \n      def resultBuilder = new StringBuilder('[ ');\n      for (int i=0; i<outputs.length; i++) {\n        resultBuilder.append(' {\"name\": \"similarity\", \"data_type\": \"FLOAT32\", \"shape\": [1],');\n        //resultBuilder.append('{\"name\": \"similarity\"}');\n        \n        resultBuilder.append('\"data\": [');\n        resultBuilder.append(outputs[i].score);\n        resultBuilder.append(']}');\n        if (i<outputs.length - 1) {\n          resultBuilder.append(',');\n        }\n      }\n      resultBuilder.append(']');\n      \n      return resultBuilder.toString();\n    "
    }
  ]
}
```

Use the connector ID from the response to create a model:
```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Sagemaker Cross-Encoder model",
    "function_name": "remote",
    "description": "test rerank model",
    "connector_id": "your_connector_id"
}
```
Note the model ID in the response; you will use it in the following steps.

Test the model using the Predict API:
```json
POST _plugins/_ml/models/your_model_id/_predict
{
  "parameters":  {
    "inputs": ["I kike you . I hate you", "I kike you . I love you"]
  }
}
```

Each item in the inputs array comprises the 'query text' and a 'text doc', separated by a ` . `

The API can also be tested similarly to a [local cross-encoder model](https://opensearch.org/docs/latest/ml-commons-plugin/pretrained-models/#cross-encoder-models).
The connector `pre_process_function` transforms the input into the format required by `inputs` parameter shown above. 
```json
POST _plugins/_ml/_predict/text_similarity/your_model_id
{
  "query_text": "I kike you",
  "text_docs": ["I hate you", "I love you"]
}
```

By default, the Sagemaker model output is in the following format:
```json
[
  {
    "label": "LABEL_0",
    "score": 0.00964462198317051
  },
  {
    "label": "LABEL_0",
    "score": 0.01644575409591198
  }
]
```
The connector `pre_process_function` transforms the model's output into a format that the [Reranker processor](https://opensearch.org/docs/latest/search-plugins/search-pipelines/rerank-processor/) can interpret. This adapted format is as follows:
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
            0.002032809890806675
          ]
        },
        {
          "name": "similarity",
          "data_type": "FLOAT32",
          "shape": [
            1
          ],
          "data": [
            0.0026099851820617914
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

Explanation of the response:
1. The response contains 2 `similarity` outputs. For each `similarity` output, the `data` array contains a relevance score between each document and the query.
2. The `similarity` outputs are provided in the order of the input documents; the first result of similarity pertains to the first document.


## 2. Reranking pipeline
### 2.1 Ingest test data
```json
POST _bulk
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "Carson City is the capital city of the American state of Nevada." }
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan." }
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district." }
{ "index": { "_index": "my-test-data" } }
{ "passage_text" : "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states." }

```
### 2.2 Create reranking pipeline
```json
PUT /_search/pipeline/rerank_pipeline_sagemaker
{
    "description": "Pipeline for reranking with Sagemaker cross-encoder model",
    "response_processors": [
        {
            "rerank": {
                "ml_opensearch": {
                    "model_id": "your_model_id_created_in_step1"
                },
                "context": {
                    "document_fields": ["passage_text"]
                }
            }
        }
    ]
}
```
Note: if you provide multiple filed names in `document_fields`, it will concat the value of all fields then do rerank.
### 2.2 Test reranking

You can tune `size` if you want to return less result. For example, set `"size": 2` if you want to return top 2 documents.

```json
GET my-test-data/_search?search_pipeline=rerank_pipeline_sagemaker
{
  "query": {
    "match_all": {}
  },
  "size": 4,
  "ext": {
    "rerank": {
      "query_context": {
         "query_text": "What is the capital of the United States?"
      }
    }
  }
}
```
Response:
```json
{
  "took": 3,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": 0.99424136,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "tYLLdZABHToP7ahNFqmx",
        "_score": 0.99424136,
        "_source": {
          "passage_text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
          "title": "title3"
        }
      },
      {
        "_index": "my-test-data",
        "_id": "toLLdZABHToP7ahNFqmx",
        "_score": 0.69457644,
        "_source": {
          "passage_text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states.",
          "title": "title4"
        }
      },
      {
        "_index": "my-test-data",
        "_id": "s4LLdZABHToP7ahNFqmx",
        "_score": 0.41946858,
        "_source": {
          "passage_text": "Carson City is the capital city of the American state of Nevada.",
          "title": "title1"
        }
      },
      {
        "_index": "my-test-data",
        "_id": "tILLdZABHToP7ahNFqmx",
        "_score": 0.2727688,
        "_source": {
          "passage_text": "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
          "title": "title2"
        }
      }
    ]
  },
  "profile": {
    "shards": []
  }
}
```
Test without reranking pipeline:
```
GET my-test-data/_search
{
  "query": {
    "match_all": {}
  },
  "ext": {
    "rerank": {
      "query_context": {
         "query_text": "What is the capital of the United States?"
      }
    }
  }
}
```
The first document in the response is `Carson City is the capital city of the American state of Nevada`, which is incorrect.
```json
{
  "took": 2,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": 1.0,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "s4LLdZABHToP7ahNFqmx",
        "_score": 1.0,
        "_source": {
          "passage_text": "Carson City is the capital city of the American state of Nevada.",
          "title": "title1"
        }
      },
      {
        "_index": "my-test-data",
        "_id": "tILLdZABHToP7ahNFqmx",
        "_score": 1.0,
        "_source": {
          "passage_text": "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
          "title": "title2"
        }
      },
      {
        "_index": "my-test-data",
        "_id": "tYLLdZABHToP7ahNFqmx",
        "_score": 1.0,
        "_source": {
          "passage_text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
          "title": "title3"
        }
      },
      {
        "_index": "my-test-data",
        "_id": "toLLdZABHToP7ahNFqmx",
        "_score": 1.0,
        "_source": {
          "passage_text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states.",
          "title": "title4"
        }
      }
    ]
  }
}
```