# Amazon Rekognition Detect Text connector blueprint

Amazon Rekognition is a computer vision service that can analyze images and videos. This tutorial shows how to create an OpenSearch connector for Amazon Rekognition's Detect Text API, which can detect and extract text from images.

## Detect text with DetectText API

This tutorial demonstrates how to create an OpenSearch connector for an Amazon Rekognition model that detects text in images using the DetectText API.

Note: This functionality is available in OpenSearch 2.14 or later.

### Step 1: Add the connector endpoint to trusted URLs
```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://rekognition\\..*[a-z0-9-]\\.amazonaws\\.com$"
    ]
  }
}
```


### Step 2: Create a connector for Amazon Rekognition
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "rekognition: detect text",
  "description": "my test connector",
  "version": "1.0",
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "your_access_key",
    "secret_key": "your_secret_key",
    "session_token": "your_session_token"
  },
  "parameters": {
    "region": "us-west-2",
    "service_name": "rekognition"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "content-type": "application/x-amz-json-1.1",
        "X-Amz-Target": "RekognitionService.DetectText"
      },
      "url": "https://${parameters.service_name}.${parameters.region}.amazonaws.com",
      "request_body": "{ \"Image\": { \"Bytes\": \"${parameters.image_bytes}\" }  } "
    }
  ]
}
``` 
Sample response:
```json
{
    "connector_id": "o52l5pMB6Ebhud5_ypxu"
}
``` 

### Step 3: Register the model
```json
POST /_plugins/_ml/models/_register
{
    "name": "Amazon Rekoginition detect text model",
    "version": "1.0",
    "function_name": "remote",
    "description": "test model",
    "connector_id": "o52l5pMB6Ebhud5_ypxu"
}
``` 
Sample response:
```json
{
    "task_id": "pZ2n5pMB6Ebhud5_n5yK",
    "status": "CREATED",
    "model_id": "pp2n5pMB6Ebhud5_oJwF"
}
``` 

### Step 4: Deploy the model
```json
POST /_plugins/_ml/models/pp2n5pMB6Ebhud5_oJwF/_deploy
``` 
Sample response:
```json
{
    "task_id": "p52o5pMB6Ebhud5_dpyL",
    "task_type": "DEPLOY_MODEL",
    "status": "COMPLETED"
}
``` 

### Step 5: Test the model inference
```json
POST _plugins/_ml/models/pp2n5pMB6Ebhud5_oJwF/_predict
{
  "parameters": {
    "response_filter": "$.TextDetections.*.DetectedText",
    "image_bytes": "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"
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
            "response": [
              "Platform",
              "Search for anything",
              "Platform",
              "Search",
              "for",
              "anything"
            ]
          }
        }
      ],
      "status_code": 200
    }
  ]
}
``` 

### Step 6: Create an ingest pipeline with an ml_inference processor

- The ML inference processor passes a source field to a model, extracts the model output, and inserts it into a target field.

- This example processor works as follows:

  - [1] Extracts values from the image field and passes the values to the image_bytes parameter.
  - [2] Invokes the Amazon Rekognition DetectText API providing the image_bytes parameter.
  - [3] Extracts values from the DetectText API response with JSON path.
  - [4] Inserts the extracted values into the text field.
  - [5] Removes the original image field.


```json
PUT _ingest/pipeline/rekognition_detect_text_pipeline
{
  "description": "Test rekognition detect text",
  "processors": [
    {
      "ml_inference": {
        "model_id": "pp2n5pMB6Ebhud5_oJwF",
        "input_map": [
          {
            "image_bytes": "image"
          }
        ],
        "output_map": [
          {
            "text": "$.TextDetections.*.DetectedText"
          }
        ]
      }
    },
    {
      "remove": {
        "field": "image"
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

Step 7: Create an index with the ingest pipeline

Create an index that uses the ingest pipeline by default:

```json
PUT detect_text_test
{
  "settings": {
    "index": {
      "default_pipeline": "rekognition_detect_text_pipeline"
    }
  }
}
``` 

Sample response:
```json
{
    "acknowledged": true,
    "shards_acknowledged": true,
    "index": "detect_text_test"
}
``` 

Step 8: Test the ingest pipeline

Let's test the pipeline by indexing a document with an image:

```json
PUT detect_text_test/_doc/1
{
  "image": "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"
}
``` 

Sample response:
```json
{
  "_index": "detect_text_test",
  "_id": "1",
  "_version": 1,
  "result": "created",
  "_shards": {
    "total": 2,
    "successful": 1,
    "failed": 0
  },
  "_seq_no": 0,
  "_primary_term": 1
}

``` 
Let's get the indexed document and check if the extracted texts are showing under the `text` field`:

```json
GET detect_text_test/_doc/1
``` 
Sample response:

```json
{
    "_index": "detect_text_test",
    "_id": "1",
    "_version": 1,
    "_seq_no": 0,
    "_primary_term": 1,
    "found": true,
    "_source": {
        "text": [
            "Platform",
            "Search for anything",
            "Platform",
            "Search",
            "for",
            "anything"
        ]
    }
}
``` 