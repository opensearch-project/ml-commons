# Amazon Comprehend connector blueprint example for metadata embedding:

Amazon Comprehend uses natural language processing (NLP) to extract insights about the content of documents without the need of any special preprocessing. You can get details of Amazon Comprehend from [Amazon Comprehend Documentation](https://docs.aws.amazon.com/comprehend/).

## Language detection with [DetectDominantLanguage](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectDominantLanguage.html) API

This instruction shows how to create OpenSearch connector having capability to set detected language code based on input text using Amazon Comrehend DetectDominantLanguage API.

Note: Need to do this on 2.14.0 or later.

### 1. Add connector endpoint to trusted URLs:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://comprehend\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
        ]
    }
}
```

### 2. Create connector for Amazon Bedrock:

If you are using self-managed Opensearch, you should supply AWS credentials:

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
    "region": "ap-northeast-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api": "Comprehend_20171127.DetectDominantLanguage",
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

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

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
    "region": "ap-northeast-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api": "Comprehend_20171127.DetectDominantLanguage",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1",
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

### 3. Create model group:

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

### 4. Register model to model group & deploy model:

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
### 5. Test model inference:

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

### 6. Create ingest pipeline with ml_inference processor:

[ML inference processor](https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/) passes source field to a model, extracts model output and insert into target field. 

This example processor works as follows:
1. Extract values from `message` field and pass values to `Text` parameter.
1. Invoke Amazon Comprehend DetectDominantLanguage API with `Text` parameter.
1. Extract values from DetectDominantLanguage result.
1. Insert extracted values into `detected_dominant_language`.

```json
PUT /_ingest/pipeline/detect_dominant_language_pipeline
{
  "processors": [
    {
      "ml_inference": {
        "model_id": comprehend_model_id,
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

### 7. Test ingest pipeline processor

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

Congratulations! You've successfully created Amazon Comprehend DetectDominantLanguage connector and ingest pipeline.

## Entity detection with [DetectEntities](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectEntities.html) API

This instruction shows how to create OpenSearch connector having capability to extract entities from input text.

Note: Need to do this on 2.14.0 or later.

### 1. Add connector endpoint to trusted URLs:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://comprehend\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
        ]
    }
}
```

### 2. Create connector for Amazon Bedrock:

If you are using self-managed Opensearch, you should supply AWS credentials:

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
    "region": "ap-northeast-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api": "Comprehend_20171127.DetectEntities",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1",
      },
      "request_body": "{ \"Text\": \"${parameters.Text}\", \"LanguageCode\": \"${parameters.LanguageCode}\"}"
    }
  ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

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
    "region": "ap-northeast-1",
    "endpoint": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
    "api": "Comprehend_20171127.DetectEntities",
    "response_filter": "$"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "${parameters.endpoint}",
      "headers": {
        "X-Amz-Target": "${parameters.api}",
        "content-type": "application/x-amz-json-1.1",
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

### 3. Create model group:

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

### 4. Register model to model group & deploy model:

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
### 5. Test model inference:

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

### 6. Create ingest pipeline with ml_inference processor:

ml_inference passes source field to a model, extracts model output and insert into target field. Read more details on https://opensearch.org/docs/latest/ingest-pipelines/processors/ml-inference/

This example processor works as follows:
1. Extract values from `message` field and pass values to `Text` parameter.
1. Invoke Amazon Comprehend DetectEntities API with `Text` parameter and `LanguageCode` parameter. LanguageCode is specified in model_config section.
1. Extract values from DetectEntities result.
1. Insert extracted values into `detected_entities`.

```json
PUT /_ingest/pipeline/detect_entities_pipeline
{
  "processors": [
    {
      "ml_inference": {
        "model_id": mNBeno8Bk4Evvk2c4lRF,
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

Congratulations! You've successfully created Amazon Comprehend DetectEntities connector and ingest pipeline.
