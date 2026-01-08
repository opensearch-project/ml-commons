# Bedrock connector blueprint example for Nova MultiModal Model

## 1. Create connector for Amazon Bedrock:

You have two options:

### Option A: Unified Multimodal Connector (Supported in OpenSearch 3.5+ version)
Use a single connector that automatically detects and handles all modality types (text, image, video, audio).

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock Nova multimodal model",
    "description": "Test connector for Amazon Bedrock Nova multimodal model",
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
        "truncationMode": "<START|END|NONE>",  // Text embedding
        "imageFormat": "<png|jpeg|gif|webp>",  // Image embedding
        "detailLevel": "<STANDARD_IMAGE|DOCUMENT_IMAGE>",  // Image embedding
        "videoFormat": "<mp4|mov|mkv|webm|flv|mpeg|mpg|wmv|3gp>",  // Video embedding
        "embeddingMode": "<AUDIO_VIDEO_COMBINED|AUDIO_VIDEO_SEPARATE>",  // Video embedding
        "audioFormat": "<mp3|wav|ogg>"  // Audio embedding
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
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n    \"embeddingPurpose\": \"GENERIC_INDEX\",\n    \"embeddingDimension\": ${parameters.dimensions},\n    \"text\": {\n      \"truncationMode\": \"${parameters.truncationMode:-null}\",\n      \"value\": \"${parameters.text:-null}\"\n    },\n    \"image\": {\n      \"detailLevel\": \"${parameters.detailLevel:-null}\",\n      \"format\": \"${parameters.imageFormat:-null}\",\n      \"source\": {\n        \"bytes\": \"${parameters.image:-null}\"\n      }\n    },\n    \"video\": {\n      \"format\": \"${parameters.videoFormat:-null}\",\n      \"source\": {\n        \"bytes\": \"${parameters.video:-null}\"\n      },\n      \"embeddingMode\": \"${parameters.embeddingMode:-null}\"\n    },\n    \"audio\": {\n      \"format\": \"${parameters.audioFormat:-null}\",\n      \"source\": {\n        \"bytes\": \"${parameters.audio:-null}\"\n      }\n    }\n  }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.embedding",
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
    "name": "Amazon Bedrock Nova multimodal model",
    "description": "Test connector for Amazon Bedrock Nova multimodal model",
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
        "truncationMode": "<START|END|NONE>",  // Text embedding
        "imageFormat": "<png|jpeg|gif|webp>",  // Image embedding
        "detailLevel": "<STANDARD_IMAGE|DOCUMENT_IMAGE>",  // Image embedding
        "videoFormat": "<mp4|mov|mkv|webm|flv|mpeg|mpg|wmv|3gp>",  // Video embedding
        "embeddingMode": "<AUDIO_VIDEO_COMBINED|AUDIO_VIDEO_SEPARATE>",  // Video embedding
        "audioFormat": "<mp3|wav|ogg>"  // Audio embedding
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
            "request_body": "{\n  \"taskType\": \"SINGLE_EMBEDDING\",\n  \"singleEmbeddingParams\": {\n    \"embeddingPurpose\": \"GENERIC_INDEX\",\n    \"embeddingDimension\": ${parameters.dimensions},\n    \"text\": {\n      \"truncationMode\": \"${parameters.truncationMode:-null}\",\n      \"value\": \"${parameters.text:-null}\"\n    },\n    \"image\": {\n      \"detailLevel\": \"${parameters.detailLevel:-null}\",\n      \"format\": \"${parameters.imageFormat:-null}\",\n      \"source\": {\n        \"bytes\": \"${parameters.image:-null}\"\n      }\n    },\n    \"video\": {\n      \"format\": \"${parameters.videoFormat:-null}\",\n      \"source\": {\n        \"bytes\": \"${parameters.video:-null}\"\n      },\n      \"embeddingMode\": \"${parameters.embeddingMode:-null}\"\n    },\n    \"audio\": {\n      \"format\": \"${parameters.audioFormat:-null}\",\n      \"source\": {\n        \"bytes\": \"${parameters.audio:-null}\"\n      }\n    }\n  }\n}",
            "pre_process_function": "connector.pre_process.bedrock.nova.embedding",
            "post_process_function": "connector.post_process.bedrock.nova.embedding"
        }
    ]
}
```

### Option B: Individual Modality Connectors
Create separate connectors for each modality type you plan to use: text embedding, image embedding, video embedding, or audio embedding.

#### Text Embedding

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

#### Image Embedding

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

#### Video Embedding

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

#### Audio Embedding

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

##### For AOS 2.17, 2.19, and 3.1:

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

##### For AOS 3.3+:

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

## 2. Register remote model:

Register a separate model for each connector created in step 1.

```json
POST /_plugins/_ml/models/_register
{
  "name": "Amazon Bedrock Nova Multi-Modal Embedding model",
  "function_name": "remote",
  "description": "Test model for Amazon Bedrock Nova embedding model",
  "connector_id": "<connector_id>"
}
```

## 3. Test model inference:

### Input Format Guide

**Unified Multimodal Connector (Option A):**
- Single input: Use `parameters` with modality-specific keys (`text`, `image`, `video`, `audio`)
- Multiple inputs: Use `text_docs` array with JSON-wrapped values

**Individual Modality Connectors (Option B):**
- Single input: Use `parameters` with prefixed keys (`inputText`, `inputImage`, `inputVideo`, `inputAudio`)
- Multiple inputs: Use `text_docs` array with raw values

### Unified Multimodal Connector

**Note:** Each request can only contain ONE modality type. You cannot mix different modalities in a single request.

**Single input:**

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "text": "hello world"  // Text embedding OR
    // "image": "{{image_base64}}"  // Image embedding OR
    // "video": "{{video_base64}}"  // Video embedding OR
    // "audio": "{{audio_base64}}" // Audio embedding
  }
}
```
**Multiple inputs:**

```json
POST /_plugins/_ml/models/<model_id>/_predict?algorithm=text_embedding
{
  "text_docs": [
    "hello", "world"  // Text embedding OR
    // "{\"image\": \"{{image_base64}}\"}", "{\"image\": \"{{image_base64}}\"}" // Image embedding OR
    // "{\"video\": \"{{video_bytes}}\"}", "{\"video\": \"{{video_bytes}}\"}"  // Video embedding OR
    // "{\"audio\": \"{{audio_bytes}}\"}", "{\"audio\": \"{{audio_bytes}}\"}"  // Audio embedding
  ]
}
```

**Sample response:**

Single input returns one embedding:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [-0.014445321, 0.029572023, ...]
    }],
    "status_code": 200
  }]
}
```

Multiple inputs return multiple embeddings:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [-0.039435633, 0.038590584, ...]
    }],
    "status_code": 200
  },
    {
      "output": [{
        "name": "sentence_embedding",
        "data_type": "FLOAT32",
        "shape": [1024],
        "data": [-0.0059986515, 0.0017826181, ...]
      }],
      "status_code": 200
    }
  ]
}
```

### Individual Modality Connector - Text Embedding

**Single text input:**

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputText": "hello world"
  }
}
```

**Multiple text inputs:**

```json
POST /_plugins/_ml/models/<model_id>/_predict?algorithm=text_embedding
{
  "text_docs": ["hello", "world"]
}
```

**Sample response:**

Single input returns one embedding:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [-0.014445321, 0.029572023, ...]
    }],
    "status_code": 200
  }]
}
```

Multiple inputs return multiple embeddings:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [-0.039435633, 0.038590584, ...]
    }],
    "status_code": 200
  },
    {
      "output": [{
        "name": "sentence_embedding",
        "data_type": "FLOAT32",
        "shape": [1024],
        "data": [-0.0059986515, 0.0017826181, ...]
      }],
      "status_code": 200
    }
  ]
}
```

### Individual Modality Connector - Image Embedding

Replace `{{image_base64}}` with your base64-encoded image string.

**Single image input:**

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputImage": "{{image_base64}}"
  }
}
```

**Multiple image inputs:**

```json
POST /_plugins/_ml/models/<model_id>/_predict?algorithm=text_embedding
{
  "text_docs": ["{{image_base64}}", "{{image_base64}}"]
}
```

**Sample response:**

Single input returns one embedding:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [0.0012908162, -2.3238199E-5, ...]
    }],
    "status_code": 200
  }]
}
```
Multiple inputs return multiple embeddings:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [0.0012908162, -2.3238199E-5, ...]
    }],
    "status_code": 200
  },
    {
      "output": [{
        "name": "sentence_embedding",
        "data_type": "FLOAT32",
        "shape": [1024],
        "data": [0.0012908162, -2.3238199E-5, ...]
      }],
      "status_code": 200
    }
  ]
}
```

### Individual Modality Connector - Video Embedding

Replace `{{video_base64}}` with your base64-encoded video string.

**Single video input:**

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputVideo": "{{video_base64}}"
  }
}
```

**Multiple video inputs:**

```json
POST /_plugins/_ml/models/<model_id>/_predict?algorithm=text_embedding
{
  "text_docs": ["{{video_base64}}", "{{video_base64}}"]
}
```

**Sample response:**

Single input returns one embedding:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [0.014660299, -0.023394907, ...]
    }],
    "status_code": 200
  }]
}
```

Multiple inputs return multiple embeddings:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [0.014660299, -0.023394907, ...]
    }],
    "status_code": 200
  },
    {
      "output": [{
        "name": "sentence_embedding",
        "data_type": "FLOAT32",
        "shape": [1024],
        "data": [0.014660299, -0.023394907, ...]
      }],
      "status_code": 200
    }
  ]
}
```

### Individual Modality Connector - Audio Embedding

Replace `{{audio_base64}}` with your base64-encoded audio string.

**Single audio input:**

```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "inputAudio": "{{audio_base64}}"
  }
}
```

**Multiple audio inputs:**

```json
POST /_plugins/_ml/models/<model_id>/_predict?algorithm=text_embedding
{
  "text_docs": ["{{audio_base64}}", "{{audio_base64}}"]
}
```

**Sample response:**

Single input returns one embedding:


```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [-0.019146109, -0.02901372, ...]
    }],
    "status_code": 200
  }]
}
```

Multiple inputs return multiple embeddings:

```json
{
  "inference_results": [{
    "output": [{
      "name": "sentence_embedding",
      "data_type": "FLOAT32",
      "shape": [1024],
      "data": [-0.019146109, -0.02901372, ...]
    }],
    "status_code": 200
  },
    {
      "output": [{
        "name": "sentence_embedding",
        "data_type": "FLOAT32",
        "shape": [1024],
        "data": [-0.019146109, -0.02901372, ...]
      }],
      "status_code": 200
    }
  ]
}
```