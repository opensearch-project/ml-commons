# Bedrock connector blueprint example for Nova MultiModal Model

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

Note: Different modality types require separate connectors. Create one connector for each modality you plan to use: text embedding, image embedding, video embedding, or audio embedding.


### Text Embedding

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - text embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - text embedding",
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
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "truncationMode": "<START|END|NONE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"GENERIC_INDEX\",\n   \"embeddingDimension\": ${parameters.dimensions},\n    \"text\": {\n      \"truncationMode\": \"${parameters.truncationMode}\",\n      \"value\": \"${parameters.inputText}\"\n    }\n  }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.text_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html) 

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - text embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - text embedding",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "truncationMode": "<START|END|NONE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"GENERIC_INDEX\",\n   \"embeddingDimension\": ${parameters.dimensions},\n    \"text\": {\n      \"truncationMode\": \"${parameters.truncationMode}\",\n      \"value\": \"${parameters.inputText}\"\n    }\n  }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.text_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

### Image Embedding

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - image embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - image embedding",
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
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "imageFormat": "<png|jpeg|gif|webp>",
        "detailLevel": "<STANDARD_IMAGE|DOCUMENT_IMAGE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"IMAGE_RETRIEVAL\",\n \"embeddingDimension\": ${parameters.dimensions},\n    \"image\": {\n      \"format\": \"${parameters.imageFormat}\",\n      \"detailLevel\": \"${parameters.detailLevel}\",\n      \"source\": {\n        \"bytes\": \"${parameters.inputImage}\"\n      }\n    }\n  }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.image_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - image embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - image embedding",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "imageFormat": "<png|jpeg|gif|webp>",
        "detailLevel": "<STANDARD_IMAGE|DOCUMENT_IMAGE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"IMAGE_RETRIEVAL\",\n \"embeddingDimension\": ${parameters.dimensions},\n    \"image\": {\n      \"format\": \"${parameters.imageFormat}\",\n      \"detailLevel\": \"${parameters.detailLevel}\",\n      \"source\": {\n        \"bytes\": \"${parameters.inputImage}\"\n      }\n    }\n  }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.image_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

### Video Embedding

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - video embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - video embedding",
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
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "videoFormat": "<mp4|mov|mkv|webm|flv|mpeg|mpg|wmv|3gp>",
        "embeddingMode": "<AUDIO_VIDEO_COMBINED|AUDIO_VIDEO_SEPARATE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n \"embeddingPurpose\": \"VIDEO_RETRIEVAL\",       \"embeddingDimension\": ${parameters.dimensions},\n        \"video\": {\n            \"format\": \"${parameters.videoFormat}\",\n            \"embeddingMode\": \"${parameters.embeddingMode}\",\n            \"source\": {\n                \"bytes\": \"${parameters.inputVideo}\"\n            }\n        }\n    }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.video_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - video embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - video embedding",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "videoFormat": "<mp4|mov|mkv|webm|flv|mpeg|mpg|wmv|3gp>",
        "embeddingMode": "<AUDIO_VIDEO_COMBINED|AUDIO_VIDEO_SEPARATE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n \"embeddingPurpose\": \"VIDEO_RETRIEVAL\",       \"embeddingDimension\": ${parameters.dimensions},\n        \"video\": {\n            \"format\": \"${parameters.videoFormat}\",\n            \"embeddingMode\": \"${parameters.embeddingMode}\",\n            \"source\": {\n                \"bytes\": \"${parameters.inputVideo}\"\n            }\n        }\n    }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.video_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

### Audio Embedding

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - audio embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - audio embedding",
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
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "audioFormat": "<mp3|wav|ogg>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"AUDIO_RETRIEVAL\",      \"embeddingDimension\": ${parameters.dimensions},\n        \"audio\": {\n            \"format\": \"${parameters.audioFormat}\",\n\"source\": {\n                \"bytes\": \"${parameters.inputAudio}\"\n            }\n        }\n    }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.audio_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

#### For AOS 2.17, 2.19, and 3.1

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - audio embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - audio embedding",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "audioFormat": "<mp3|wav|ogg>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"AUDIO_RETRIEVAL\",      \"embeddingDimension\": ${parameters.dimensions},\n        \"audio\": {\n            \"format\": \"${parameters.audioFormat}\",\n\"source\": {\n                \"bytes\": \"${parameters.inputAudio}\"\n            }\n        }\n    }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.video_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

#### For AOS 3.3+

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model - audio embedding",
    "description": "Test connector for Amazon Bedrock Nova multimodal model - audio embedding",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
      "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
    },
    "parameters": {
        "region": "<PLEASE ADD YOUR AWS REGION HERE>",
        "service_name": "bedrock",
        "model": "amazon.nova-2-multimodal-embeddings-v1:0",
        "input_docs_processed_step_size": 1,
        "dimensions": 1024,
        "normalize": true,
        "embeddingTypes": [
          "float"
        ],
        "audioFormat": "<mp3|wav|ogg>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
              "content-type": "application/json",
              "x-amz-content-sha256": "required"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n  \"embeddingPurpose\": \"AUDIO_RETRIEVAL\",      \"embeddingDimension\": ${parameters.dimensions},\n        \"audio\": {\n            \"format\": \"${parameters.audioFormat}\",\n\"source\": {\n                \"bytes\": \"${parameters.inputAudio}\"\n            }\n        }\n    }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.audio_embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

## 3. Register remote model:

Register a separate model for each connector created in step 2. Use the corresponding connector_id for each modality:

```json
POST /_plugins/_ml/models/_register
{
  "name": "Amazon Bedrock Nova Multi-Modal Embedding model",
  "function_name": "remote",
  "description": "Test model for Amazon Bedrock Nova embedding model",
  "connector_id": "<connector_id>"
}
```

## 4. Test model inference:

### Text Embedding

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputText": "hello world"
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            -0.014445321,
            0.029572023,
            -0.024120959,
            ...
          ]
        }
      ]
    }
  ]
}
```

### Image Embedding

Replace `{{image_base64}}` with your base64-encoded image string:

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputImage": "{{image_base64}}"
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            0.0012908162,
            -2.3238199E-5,
            -0.04998639,
            ...
          ]
        }
      ]
    }
  ]
}
```

### Video Embedding

Replace `{{video_base64}}` with your base64-encoded video string:


```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputVideo": "{{video_base64}}"
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            0.014660299,
            -0.023394907,
            0.022433588,
            ...
          ]
        }
      ]
    }
  ]
}
```

### Audio Embedding

Replace `{{audio_base64}}` with your base64-encoded audio string:


```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputAudio": "{{audio_base64}}"
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
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            -0.019146109,
            -0.02901372,
            -0.041826885,
            ...
          ]
        }
      ]
    }
  ]
}
```