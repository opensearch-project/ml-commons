# Topic

Note: Since 2.19, OpenSearch supports built-in pre and post process function for easy integration. For more detais, see [rerank_pipeline_with_Bedrock_Rerank_model.md](../rerank_pipeline_with_Bedrock_Rerank_model.md)

[Reranking pipeline](https://opensearch.org/docs/latest/search-plugins/search-relevance/reranking-search-results/) is a feature released in OpenSearch 2.12. It can rerank search results, providing a relevance score with respect to the search query for each matching document. The relevance score is calculated by a cross-encoder model. 

This tutorial illustrates using the [Amazon Rerank 1.0 model in Amazon Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/rerank-supported.html) in a reranking pipeline. 

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 0. Test the model on Amazon Bedrock
You can perform a reranking test using the following code.

```python
import json
import boto3
bedrock_region = "your_bedrock_model_region_like_us-west-2"
bedrock_runtime_client = boto3.client("bedrock-runtime", region_name=bedrock_region)

modelId = "amazon.rerank-v1:0"
contentType = "application/json"
accept = "*/*"

body = json.dumps({
    "query": "What is the capital city of America?",
    "documents": [
        "Carson City is the capital city of the American state of Nevada.",
        "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
        "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
        "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
    ]
})

response = bedrock_runtime_client.invoke_model(
    modelId=modelId,
    contentType=contentType,
    accept=accept, 
    body=body
)
results = json.loads(response.get('body').read())["results"]
print(json.dumps(results, indent=2))
```

The reranking result is ordering by the highest score first:
```
[
  {
    "index": 2,
    "relevance_score": 0.7711548724998493
  },
  {
    "index": 0,
    "relevance_score": 0.0025114635138098534
  },
  {
    "index": 1,
    "relevance_score": 2.4876490010363496e-05
  },
  {
    "index": 3,
    "relevance_score": 6.339210403977635e-06
  }
]
```


You can sort the result by index number.

```python
print(json.dumps(sorted(results, key=lambda x: x['index']),indent=2))
```

The results are as follows:
```
[
  {
    "index": 0,
    "relevance_score": 0.0025114635138098534
  },
  {
    "index": 1,
    "relevance_score": 2.4876490010363496e-05
  },
  {
    "index": 2,
    "relevance_score": 0.7711548724998493
  },
  {
    "index": 3,
    "relevance_score": 6.339210403977635e-06
  }
]
```

## 1. Create a connector and register the model

To create a connector for the model, send the following request. If you are using self-managed OpenSearch, supply your AWS credentials:
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock rerank model",
  "description": "Test connector for Amazon Bedrock rerank model",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "your_access_key",
    "secret_key": "your_secret_key",
    "session_token": "your_session_token"
  },
  "parameters": {
    "service_name": "bedrock",
    "endpoint": "bedrock-runtime",
    "region": "your_bedrock_model_region_like_us-west-2",
    "model_name": "amazon.rerank-v1:0" 
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "POST",
      "url": "https://${parameters. endpoint}.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "pre_process_function": """
        def query_text = params.query_text;
        def text_docs = params.text_docs;
        def textDocsBuilder = new StringBuilder('[');
        for (int i=0; i<text_docs.length; i++) {
          textDocsBuilder.append('"');
          textDocsBuilder.append(text_docs[i]);
          textDocsBuilder.append('"');
          if (i<text_docs.length - 1) {
            textDocsBuilder.append(',');
          }
        }
        textDocsBuilder.append(']');
        def parameters = '{ "query": "' + query_text + '",  "documents": ' + textDocsBuilder.toString() + ' }';
        return  '{"parameters": ' + parameters + '}';
        """,
      "request_body": """
        { 
          "documents": ${parameters.documents},
          "query": "${parameters.query}"
        }
        """,
      "post_process_function": """
        if (params.results == null || params.results.length == 0) {
          throw new IllegalArgumentException("Post process function input is empty.");
        }
        def outputs = params.results;
        def relevance_scores = new Double[outputs.length];
        for (int i=0; i<outputs.length; i++) {
          def index = new BigDecimal(outputs[i].index.toString()).intValue();
          relevance_scores[index] = outputs[i].relevance_score;
        }
        def resultBuilder = new StringBuilder('[');
        for (int i=0; i<relevance_scores.length; i++) {
          resultBuilder.append(' {"name": "similarity", "data_type": "FLOAT32", "shape": [1],');
          resultBuilder.append('"data": [');
          resultBuilder.append(relevance_scores[i]);
          resultBuilder.append(']}');
          if (i<outputs.length - 1) {
            resultBuilder.append(',');
          }
        }
        resultBuilder.append(']');
        return resultBuilder.toString();
      """
    }
  ]
}
```

If using the Amazon Opensearch Service, you can provide an IAM role ARN that allows access to the Amazon Bedrock service. For more information, see [AWS documentation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html):

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock rerank model",
  "description": "Test connector for Amazon Bedrock rerank model",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "roleArn": "your_role_arn_which_allows_access_to_bedrock_model"
  },
  "parameters": {
    "service_name": "bedrock",
    "endpoint": "bedrock-runtime",
    "region": "your_bedrock_model_region_like_us-west-2",
    "model_name": "amazon.rerank-v1:0" 
  },
  "actions": [
    {
      "action_type": "PREDICT",
      "method": "POST",
      "url": "https://${parameters.endpoint}.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke",
      "headers": {
        "x-amz-content-sha256": "required",
        "content-type": "application/json"
      },
      "pre_process_function": """
        def query_text = params.query_text;
        def text_docs = params.text_docs;
        def textDocsBuilder = new StringBuilder('[');
        for (int i=0; i<text_docs.length; i++) {
          textDocsBuilder.append('"');
          textDocsBuilder.append(text_docs[i]);
          textDocsBuilder.append('"');
          if (i<text_docs.length - 1) {
            textDocsBuilder.append(',');
          }
        }
        textDocsBuilder.append(']');
        def parameters = '{ "query": "' + query_text + '",  "documents": ' + textDocsBuilder.toString() + ' }';
        return  '{"parameters": ' + parameters + '}';
        """,
      "request_body": """
        { 
          "documents": ${parameters.documents},
          "query": "${parameters.query}"
        }
        """,
      "post_process_function": """
        if (params.results == null || params.results.length == 0) {
          throw new IllegalArgumentException("Post process function input is empty.");
        }
        def outputs = params.results;
        def relevance_scores = new Double[outputs.length];
        for (int i=0; i<outputs.length; i++) {
          def index = new BigDecimal(outputs[i].index.toString()).intValue();
          relevance_scores[index] = outputs[i].relevance_score;
        }
        def resultBuilder = new StringBuilder('[');
        for (int i=0; i<relevance_scores.length; i++) {
          resultBuilder.append(' {"name": "similarity", "data_type": "FLOAT32", "shape": [1],');
          resultBuilder.append('"data": [');
          resultBuilder.append(relevance_scores[i]);
          resultBuilder.append(']}');
          if (i<outputs.length - 1) {
            resultBuilder.append(',');
          }
        }
        resultBuilder.append(']');
        return resultBuilder.toString();
      """
    }
  ]
}
```

Use the connector ID from the response to register and deploy the model:
```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Amazon Bedrock rerank model",
    "function_name": "remote",
    "description": "test rerank model",
    "connector_id": "your_connector_id"
}
```
Note the model ID in the response; you'll use it in the following steps.

Test the model by using the Predict API:
```json
POST _plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "query": "What is the capital city of America?",
    "documents": [
      "Carson City is the capital city of the American state of Nevada.",
      "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
      "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
      "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
    ]
  }
}
```

Alternatively, you can test the model as follows:
```json
POST _plugins/_ml/_predict/text_similarity/your_model_id
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

The connector `pre_process_function` transforms the input into the format required by the previously shown parameters.

By default, the Amazon Bedrock Rerank API output has the following format:
```json
[
  {
    "index": 2,
    "relevance_score": 0.7711548724998493
  },
  {
    "index": 0,
    "relevance_score": 0.0025114635138098534
  },
  {
    "index": 1,
    "relevance_score": 2.4876490010363496e-05
  },
  {
    "index": 3,
    "relevance_score": 6.339210403977635e-06
  }
]
```

The connector `post_process_function` transforms the model's output into a format that the [Reranker processor](https://opensearch.org/docs/latest/search-plugins/search-pipelines/rerank-processor/) can interpret, and orders the results by index. This adapted format is as follows:
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

Explanation of the response:
1. The response contains four `similarity` outputs. For each `similarity` output, the `data` array contains a relevance score of each document against the query.
2. The `similarity` outputs are provided in the order of the input documents; the first similarity result pertains to the first document.

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
### 2.2 Create a reranking pipeline
```json
PUT /_search/pipeline/rerank_pipeline_bedrock
{
    "description": "Pipeline for reranking with Bedrock rerank model",
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

Note: if you provide multiple field names in `document_fields`, the values of all fields are first concatenated and then reranking is performed.

### 2.2 Test reranking

First, test the query without using the reranking pipeline:
```json
POST my-test-data/_search
{
  "query": {
    "match": {
      "passage_text": "What is the capital city of America?"
    }
  },
  "highlight": {
    "pre_tags": ["<strong>"],
    "post_tags": ["</strong>"],
    "fields": {"passage_text": {}}
  },
  "_source": false,
  "fields": ["passage_text"]
}
```

The first document in the response is `Carson City is the capital city of the American state of Nevada`, which is incorrect:
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
    "max_score": 2.5045562,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "1",
        "_score": 2.5045562,
        "fields": {
          "passage_text": [
            "Carson City is the capital city of the American state of Nevada."
          ]
        },
        "highlight": {
          "passage_text": [
            "Carson <strong>City</strong> <strong>is</strong> <strong>the</strong> <strong>capital</strong> <strong>city</strong> <strong>of</strong> <strong>the</strong> American state <strong>of</strong> Nevada."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "2",
        "_score": 0.5807494,
        "fields": {
          "passage_text": [
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
          ]
        },
        "highlight": {
          "passage_text": [
            "<strong>The</strong> Commonwealth <strong>of</strong> <strong>the</strong> Northern Mariana Islands <strong>is</strong> a group <strong>of</strong> islands in <strong>the</strong> Pacific Ocean.",
            "Its <strong>capital</strong> <strong>is</strong> Saipan."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "3",
        "_score": 0.5261191,
        "fields": {
          "passage_text": [
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
          ]
        },
        "highlight": {
          "passage_text": [
            "(also known as simply Washington or D.C., and officially as <strong>the</strong> District <strong>of</strong> Columbia) <strong>is</strong> <strong>the</strong> <strong>capital</strong>",
            "<strong>of</strong> <strong>the</strong> United States.",
            "It <strong>is</strong> a federal district."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "4",
        "_score": 0.5083029,
        "fields": {
          "passage_text": [
            "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
          ]
        },
        "highlight": {
          "passage_text": [
            "<strong>Capital</strong> punishment (<strong>the</strong> death penalty) has existed in <strong>the</strong> United States since beforethe United States",
            "As <strong>of</strong> 2017, <strong>capital</strong> punishment <strong>is</strong> legal in 30 <strong>of</strong> <strong>the</strong> 50 states."
          ]
        }
      }
    ]
  }
}
```

Next, test the query using the reranking pipeline:
```json
POST my-test-data/_search?search_pipeline=rerank_pipeline_bedrock
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
  },
  "highlight": {
    "pre_tags": ["<strong>"],
    "post_tags": ["</strong>"],
    "fields": {"passage_text": {}}
  },
  "_source": false,
  "fields": ["passage_text"]
}
```

The first document in the response is `"Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."`, which is correct:

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
    "max_score": 0.7711549,
    "hits": [
      {
        "_index": "my-test-data",
        "_id": "3",
        "_score": 0.7711549,
        "fields": {
          "passage_text": [
            "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
          ]
        },
        "highlight": {
          "passage_text": [
            "(also known as simply Washington or D.C., and officially as <strong>the</strong> District <strong>of</strong> Columbia) <strong>is</strong> <strong>the</strong> <strong>capital</strong>",
            "<strong>of</strong> <strong>the</strong> United States.",
            "It <strong>is</strong> a federal district."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "1",
        "_score": 0.0025114636,
        "fields": {
          "passage_text": [
            "Carson City is the capital city of the American state of Nevada."
          ]
        },
        "highlight": {
          "passage_text": [
            "Carson <strong>City</strong> <strong>is</strong> <strong>the</strong> <strong>capital</strong> <strong>city</strong> <strong>of</strong> <strong>the</strong> American state <strong>of</strong> Nevada."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "2",
        "_score": 02.487649e-05,
        "fields": {
          "passage_text": [
            "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan."
          ]
        },
        "highlight": {
          "passage_text": [
            "<strong>The</strong> Commonwealth <strong>of</strong> <strong>the</strong> Northern Mariana Islands <strong>is</strong> a group <strong>of</strong> islands in <strong>the</strong> Pacific Ocean.",
            "Its <strong>capital</strong> <strong>is</strong> Saipan."
          ]
        }
      },
      {
        "_index": "my-test-data",
        "_id": "4",
        "_score": 6.3392104e-06,
        "fields": {
          "passage_text": [
            "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
          ]
        },
        "highlight": {
          "passage_text": [
            "<strong>Capital</strong> punishment (<strong>the</strong> death penalty) has existed in <strong>the</strong> United States since beforethe United States",
            "As <strong>of</strong> 2017, <strong>capital</strong> punishment <strong>is</strong> legal in 30 <strong>of</strong> <strong>the</strong> 50 states."
          ]
        }
      }
    ]
  },
  "profile": {
    "shards": []
  }
}
```

Note: You can avoid writing the query twice by using the `query_text_path` instead of `query_text` as follows:
```json
POST my-test-data/_search?search_pipeline=rerank_pipeline_bedrock
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
  },
  "highlight": {
    "pre_tags": ["<strong>"],
    "post_tags": ["</strong>"],
    "fields": {"passage_text": {}}
  },
  "_source": false,
  "fields": ["passage_text"]
}
```
