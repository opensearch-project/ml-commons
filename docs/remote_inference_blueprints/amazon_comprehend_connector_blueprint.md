# Amazon Comprehend connector blueprint example for metadata embedding

Amazon Comprehend uses natural language processing (NLP) to extract insights about the content of documents without the need for any special preprocessing. For more information about Amazon Comprehend, see  [Amazon Comprehend](https://docs.aws.amazon.com/comprehend/).

## Language detection with DetectDominantLanguage API

This tutorial shows how to create an OpenSearch connector for an Amazon Comprehend model. The model examines the input text, detects the language using the Amazon Comrehend [DetectDominantLanguage API](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectDominantLanguage.html), and sets a corresponding language code.

Note: This functionality is available in OpenSearch 2.14.0 or later.

### Step 1: Add the connector endpoint to trusted URLs

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://comprehend\\..*[a-z0-9-]\\.amazonaws\\.com$"
        ]
    }
}
```

### Step 2: Create a connector for Amazon Comprehend

- If you are using self-managed Opensearch, supply your AWS credentials. Credential owner has to have a permission to access Amazon Comprehend [DetectDominantLanguage API](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectDominantLanguage.html) API. For more information, see [AWS documentation](https://docs.aws.amazon.com/comprehend/latest/dg/security-iam-awsmanpol.html)

- You can choose any region from [supported regions](https://docs.aws.amazon.com/general/latest/gr/comprehend.html)

- AWS recommends to lock API version for production to avoid disruption by new API version released [1]. You can find the latest API version from SDK reference [2] or botocore code [3].
  - [1] https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/locking-api-versions.html
  - [2] https://docs.aws.amazon.com/AWSJavaScriptSDK/latest/AWS/Comprehend.html
  - [3] https://github.com/boto/botocore/tree/master/botocore/data

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Comprehend",
  "description": "Test connector for Amazon Comprehend",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "parameters": {
    "service_name": "comprehend",
    "region": "us-east-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api_version": "20171127",
    "api_name": "DetectDominantLanguage",
    "api": "Comprehend_${parameters.api_version}.${parameters.api_name}",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1"
      },
      "request_body": "{ \"Text\": \"${parameters.Text}\"}"
    }
  ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role ARN that allows access to the Amazon Comprehend service. For more information, see following documents:
- [AWS documentation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)
- [AIConnectorHelper tutorial](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/AIConnectorHelper.ipynb)
- [Semantic search with Amazon Bedrock Titan embedding text model](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/semantic_search_with_bedrock_titan_embedding_model.md)


```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Comprehend",
  "description": "Test connector for Amazon Comprehend",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
  },
  "parameters": {
    "service_name": "comprehend",
    "region": "us-east-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api_version": "20171127",
    "api_name": "DetectDominantLanguage",
    "api": "Comprehend_${parameters.api_version}.${parameters.api_name}",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1"
      },
      "request_body": "{ \"Text\": \"${parameters.Text}\"}"
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "k9Agno8Bk4Evvk2cEVR1"
}
```

### Step 3: Create a model group

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "amazon_comprehend_model_group",
    "description": "This is an example description"
}
```

Sample response:
```json
{
  "model_group_id": "c9AVno8Bk4Evvk2cz1Sf",
  "status": "CREATED"
}
```

### Step 4: Register and deploy the model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "amazon_comprehend_detect_dominant_language",
    "function_name": "remote",
    "model_group_id": "c9AVno8Bk4Evvk2cz1Sf",
    "description": "test model",
    "connector_id": "k9Agno8Bk4Evvk2cEVR1"
}
```

Sample response:
```json
{
  "task_id": "l9Beno8Bk4Evvk2c4lQm",
  "status": "CREATED",
  "model_id": "mNBeno8Bk4Evvk2c4lRF"
}
```
### Step 5: Test the model inference

```json
POST /_plugins/_ml/models/mNBeno8Bk4Evvk2c4lRF/_predict
{
  "parameters": {
    "Text": "Bonjour"
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
            "response": {
              "Languages": [
                {
                  "LanguageCode": "fr", 
                  "Score": 0.6898566484451294
                },
                {
                  "LanguageCode": "en",
                  "Score": 0.29757627844810486
                }
              ]
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### Step 6: Create an ingest pipeline with an `ml_inference` processor

The [ML inference processor](https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/) passes a source field to a model, extracts the model output, and inserts it into a target field. 

This example processor works as follows:
1. Extracts values from the `message` field and passes the values to the `Text` parameter.
1. Invokes the Amazon Comprehend DetectDominantLanguage API providing the `Text` parameter.
1. Extracts values from the DetectDominantLanguage API response.
1. Inserts the extracted values into the `detected_dominant_language` field.

```json
PUT /_ingest/pipeline/detect_dominant_language_pipeline
{
  "processors": [
    {
      "ml_inference": {
        "model_id": "mNBeno8Bk4Evvk2c4lRF",
        "input_map": [
          {
            "Text": "message" 
          }
        ],
        "output_map": [
          {
            "detected_dominant_language": "$.response.Languages[0].LanguageCode"
          }
        ]
      }
    }
  ]
}
```

Sample response:
```json
{
  "acknowledged": true
}
```

### Step 7: Test the ingest pipeline processor

```json
POST /_ingest/pipeline/detect_dominant_language_pipeline/_simulate
{
  "docs": [
    {
      "_index": "testindex1",
      "_id": "1",
      "_source":{
         "message": "Bonjour"
      }
    },
    {
      "_index": "testindex1",
      "_id": "2",
      "_source":{
         "message": "你好"
      }
    },
    {
      "_index": "testindex1",
      "_id": "3",
      "_source":{
         "message": "Hello"
      }
    },
    {
      "_index": "testindex1",
      "_id": "4",
      "_source":{
         "message": "안녕하세요"
      }
    },
    {
      "_index": "testindex1",
      "_id": "5",
      "_source":{
         "message": "こんにちは"
      }
    }
  ]
}
```

Sample response:
```json
{
  "docs": [
    {
      "doc": {
        "_index": "testindex1",
        "_id": "1",
        "_source": {
          "detected_dominant_language": "fr",
          "message": "Bonjour"
        },
        "_ingest": {
          "timestamp": "2024-05-22T07:52:18.257268252Z"
        }
      }
    },
    {
      "doc": {
        "_index": "testindex1",
        "_id": "2",
        "_source": {
          "detected_dominant_language": "zh",
          "message": "你好"
        },
        "_ingest": {
          "timestamp": "2024-05-22T07:52:18.257271404Z"
        }
      }
    },
    {
      "doc": {
        "_index": "testindex1",
        "_id": "3",
        "_source": {
          "detected_dominant_language": "en",
          "message": "Hello"
        },
        "_ingest": {
          "timestamp": "2024-05-22T07:52:18.25727557Z"
        }
      }
    },
    {
      "doc": {
        "_index": "testindex1",
        "_id": "4",
        "_source": {
          "detected_dominant_language": "ko",
          "message": "안녕하세요"
        },
        "_ingest": {
          "timestamp": "2024-05-22T07:52:18.257276936Z"
        }
      }
    },
    {
      "doc": {
        "_index": "testindex1",
        "_id": "5",
        "_source": {
          "detected_dominant_language": "ja",
          "message": "こんにちは"
        },
        "_ingest": {
          "timestamp": "2024-05-22T07:52:18.257278224Z"
        }
      }
    }
  ]
}
```

You have now successfully created an Amazon Comprehend Detect Dominant Language connector and ingest pipeline.

## Entity detection with the DetectEntities API

This tutorial shows how to create an OpenSearch connector that can extract entities from input text using the [DetectEntities API](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectEntities.html).

Note: This functionality is available in OpenSearch 2.14.0 or later.

### Step 1: Add the connector endpoint to trusted URLs

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://comprehend\\..*[a-z0-9-]\\.amazonaws\\.com$"
        ]
    }
}
```

### Step 2: Create a connector for Amazon Comprehend

- If you are using self-managed Opensearch, supply your AWS credentials. Credential owner has to have a permission to access Amazon Comprehend [DetectEntities API](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectEntities.html) API. For more information, see [AWS documentation](https://docs.aws.amazon.com/comprehend/latest/dg/security-iam-awsmanpol.html)

- You can choose any region from [supported regions](https://docs.aws.amazon.com/general/latest/gr/comprehend.html)

- AWS recommends to lock API version for production to avoid disruption by new API version released [1]. You can find the latest API version from SDK reference [2] or botocore code [3].
  - [1] https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/locking-api-versions.html
  - [2] https://docs.aws.amazon.com/AWSJavaScriptSDK/latest/AWS/Comprehend.html
  - [3] https://github.com/boto/botocore/tree/master/botocore/data

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Comprehend",
  "description": "Test connector for Amazon Comprehend",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "parameters": {
    "service_name": "comprehend",
    "region": "us-east-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api_version": "20171127",
    "api_name": "DetectEntities",
    "api": "Comprehend_${parameters.api_version}.${parameters.api_name}",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1"
      },
      "request_body": "{ \"Text\": \"${parameters.Text}\", \"LanguageCode\": \"${parameters.LanguageCode}\"}"
    }
  ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role ARN that allows access to the Amazon Comprehend service. For more information, see following documents:
- [AWS documentation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)
- [AIConnectorHelper tutorial](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/AIConnectorHelper.ipynb)
- [Semantic search with Amazon Bedrock Titan embedding text model](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/semantic_search_with_bedrock_titan_embedding_model.md)


```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Comprehend",
  "description": "Test connector for Amazon Comprehend",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
  },
  "parameters": {
    "service_name": "comprehend",
    "region": "us-east-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api_version": "20171127",
    "api_name": "DetectEntities",
    "api": "Comprehend_${parameters.api_version}.${parameters.api_name}",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1"
      },
      "request_body": "{ \"Text\": \"${parameters.Text}\", \"LanguageCode\": \"${parameters.LanguageCode}\"}"
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "k9Agno8Bk4Evvk2cEVR1"
}
```

### Step 3: Create a model group

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "amazon_comprehend_model_group",
    "description": "This is an example description"
}
```

Sample response:
```json
{
  "model_group_id": "c9AVno8Bk4Evvk2cz1Sf",
  "status": "CREATED"
}
```

### Step 4: Register and deploy the model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "amazon_comprehend_detect_entities",
    "function_name": "remote",
    "model_group_id": "c9AVno8Bk4Evvk2cz1Sf",
    "description": "test model",
    "connector_id": "k9Agno8Bk4Evvk2cEVR1"
}
```

Sample response:
```json
{
  "task_id": "l9Beno8Bk4Evvk2c4lQm",
  "status": "CREATED",
  "model_id": "mNBeno8Bk4Evvk2c4lRF"
}
```
### Step 5: Test the model inference

```json
POST /_plugins/_ml/models/mNBeno8Bk4Evvk2c4lRF/_predict
{
  "parameters": {
    "Text": "Bob ordered two sandwiches and three ice cream cones today from a store in Seattle.",
    "LanguageCode": "en"
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
            "response": {
              "Entities": [
                {
                  "BeginOffset": 0,
                  "EndOffset": 3,
                  "Score": 0.9987996816635132,
                  "Text": "Bob",
                  "Type": "PERSON"
                },
                {
                  "BeginOffset": 12,
                  "EndOffset": 26,
                  "Score": 0.9984013438224792,
                  "Text": "two sandwiches",
                  "Type": "QUANTITY"
                },
                {
                  "BeginOffset": 31,
                  "EndOffset": 52,
                  "Score": 0.9882009625434875,
                  "Text": "three ice cream cones",
                  "Type": "QUANTITY"
                },
                {
                  "BeginOffset": 53,
                  "EndOffset": 58,
                  "Score": 0.9878937602043152,
                  "Text": "today",
                  "Type": "DATE"
                },
                {
                  "BeginOffset": 75,
                  "EndOffset": 82,
                  "Score": 0.9978049397468567,
                  "Text": "Seattle",
                  "Type": "LOCATION"
                }
              ]
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### Step 6: Create an ingest pipeline with an `ml_inference` processor

The [ML inference processor](https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/) passes a source field to a model, extracts the model output, and inserts it into a target field. 

This example processor works as follows:
1. Extracts values from the `message` field and passes the values to the `Text` parameter.
1. Invokes the Amazon Comprehend DetectEntities API providing the `Text` parameter and the `LanguageCode` parameter. The `LanguageCode` is specified in the `model_config` section.
1. Extracts values from the Detect Entities API response.
1. Insert extracted values into the `detected_entities` field.

```json
PUT /_ingest/pipeline/detect_entities_pipeline
{
  "processors": [
    {
      "ml_inference": {
        "model_id": "mNBeno8Bk4Evvk2c4lRF",
        "input_map": [
          {
            "Text": "message"
          }
        ],
        "output_map": [
          {
            "detected_entities": "$.response.Entities"
          }
        ],
        "model_config":{
          "LanguageCode": "en"
        }
      }
    }
  ]
}
```

Sample response:
```json
{
  "acknowledged": true
}
```

### 7. Test ingest pipeline processor

```json
POST /_ingest/pipeline/detect_entities_pipeline/_simulate
{
  "docs": [
    {
      "_index": "testindex1",
      "_id": "1",
      "_source":{
         "message": "Bob ordered two sandwiches and three ice cream cones today from a store in Seattle."
      }
    }
  ]
}
```

Sample response:
```json
{
  "docs": [
    {
      "doc": {
        "_index": "testindex1",
        "_id": "1",
        "_source": {
          "message": "Bob ordered two sandwiches and three ice cream cones today from a store in Seattle.",
          "detected_entities": [
            {
              "EndOffset": 3,
              "BeginOffset": 0,
              "Score": 0.9987996816635132,
              "Type": "PERSON",
              "Text": "Bob"
            },
            {
              "EndOffset": 26,
              "BeginOffset": 12,
              "Score": 0.9984013438224792,
              "Type": "QUANTITY",
              "Text": "two sandwiches"
            },
            {
              "EndOffset": 52,
              "BeginOffset": 31,
              "Score": 0.9882009625434875,
              "Type": "QUANTITY",
              "Text": "three ice cream cones"
            },
            {
              "EndOffset": 58,
              "BeginOffset": 53,
              "Score": 0.9878937602043152,
              "Type": "DATE",
              "Text": "today"
            },
            {
              "EndOffset": 82,
              "BeginOffset": 75,
              "Score": 0.9978049397468567,
              "Type": "LOCATION",
              "Text": "Seattle"
            }
          ]
        },
        "_ingest": {
          "timestamp": "2024-05-22T08:18:16.014633507Z"
        }
      }
    }
  ]
}
```

You have now successfully created an Amazon Comprehend Detect Entities connector and ingest pipeline.
