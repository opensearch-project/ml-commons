# Enhancing Multi-Language Search with an ML Inference Ingest Processor

The ML Inference ingest processor allows you to perform inference using a trained model as part of a preprocessing pipeline. This is useful for tasks like enriching documents with information derived from their content before indexing them.

This tutorial explains how to use the ML Inference ingest processor to perform language identification on text fields within ingested documents. We will deploy a pre-trained language identification model on Amazon SageMaker, connect it to OpenSearch, and then use an ingest pipeline to automatically identify the language of specific fields and store it in the document. This enables powerful multi-language search capabilities.

**Note:** Replace the placeholders that start with `your_` with your own values.

# Steps to connect the SageMaker model with OpenSearch

## 1. Deploy Language Identification Model in SageMaker

First, we'll deploy the `papluca/xlm-roberta-base-language-detection` model from Hugging Face to a SageMaker endpoint. Run the following Python code in a SageMaker Notebook.

```python
import sagemaker
import boto3
from sagemaker.huggingface import HuggingFaceModel

try:
    role = sagemaker.get_execution_role()
except ValueError:
    iam = boto3.client('iam')
    # Replace with your SageMaker execution role name if different
    role = iam.get_role(RoleName='sagemaker_execution_role')['Role']['Arn']

# Hub Model configuration from Hugging Face
hub = {
    'HF_MODEL_ID':'papluca/xlm-roberta-base-language-detection',
    'HF_TASK':'text-classification'
}

# Create Hugging Face Model Class
huggingface_model = HuggingFaceModel(
    transformers_version='4.37.0',
    pytorch_version='2.1.0',
    py_version='py310',
    env=hub,
    role=role,
)

# Deploy model to SageMaker Inference
predictor = huggingface_model.deploy(
    initial_instance_count=1,
    instance_type='ml.m7g.xlarge'
)

# After deployment, you can find your endpoint name in the
# Amazon SageMaker > Inference > Endpoints console.
# It will look something like: huggingface-pytorch-inference-2024-07-08-12-34-56-789
print(f"SageMaker Endpoint Name: {predictor.endpoint_name}")

# Test model prediction
prediction = predictor.predict({
    "inputs": "I like you. I love you",
})
print(prediction)
```

Copy the `SageMaker Endpoint Name` from the output. You will need it in the following steps.

## 2. Create the Connector

Based on your platform, there are different steps to create a connector. Regardless of the platform, the outcome will be a `connector_id` for model registration.

### A. Using Amazon OpenSearch Service
This is the recommended approach when using Amazon's managed service.

#### a. Create an IAM Role for the OpenSearch Connector
In the AWS IAM console, create a new role with the following trust relationship. This allows the OpenSearch service to assume this role.

- **Custom trust policy:**
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sts:AssumeRole",
            "Effect": "Allow",
            "Principal": {
                "Service": "opensearchservice.amazonaws.com"
            }
        }
    ]
}
```

#### b. Attach Permissions
Create and attach the following two inline policies to the role.

- **Policy 1: Allow SageMaker Invocation**
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "sagemaker:InvokeEndpoint"
            ],
            "Effect": "Allow",
            "Resource": "arn:aws:sagemaker:your_region:your_account_id:endpoint/your_sagemaker_endpoint_name"
        }
    ]
}
```

- **Policy 2: Allow OpenSearch and IAM PassRole**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "arn:aws:iam::your_account_id:role/your_opensearch_sagemaker_role_name"
    },
    {
      "Effect": "Allow",
      "Action": "es:ESHttpPost",
      "Resource": "arn:aws:es:your_region:your_account_id:domain/your_domain_name/*"
    }
  ]
}
```
After creating the role, copy its ARN. You will need it to create the connector.

#### c. Map the IAM Role in OpenSearch Dashboards
Follow the documentation to map the IAM role you just created to the `ml_full_access` role in your OpenSearch domain.

#### d. Create the Connector
Now, create the connector in OpenSearch to connect to the SageMaker model endpoint.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Sagemaker language identification model connector",
  "description": "Connector for language identification model on SageMaker",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "your_aws_region",
    "service_name": "sagemaker"
  },
  "credential": {
    "roleArn": "your_iam_role_arn_from_step_2a"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://runtime.sagemaker.your_aws_region.amazonaws.com/endpoints/your_sagemaker_endpoint_name/invocations",
      "headers": {
        "content-type": "application/json"
      },
      "request_body": "{"inputs":"${parameters.inputs}"}"
    }
  ]
}
```

### B. Using a local OpenSearch instance
This approach is for users running OpenSearch on their own infrastructure.

#### a. Create an IAM User
Create an IAM user with programmatic access and attach a policy that allows invoking the SageMaker endpoint.
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sagemaker:InvokeEndpoint",
            "Effect": "Allow",
            "Resource": "arn:aws:sagemaker:your_region:your_account_id:endpoint/your_sagemaker_endpoint_name"
        }
    ]
}
```
Generate an access key and secret key for this user.

#### b. Create the Connector
Use the IAM user's credentials to create the connector.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Sagemaker language identification model connector",
  "description": "Connector for language identification model on SageMaker",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "aws_access_key_id": "your_aws_access_key_id",
    "aws_secret_access_key": "your_aws_secret_access_key"
  },
  "parameters": {
    "region": "your_aws_region",
    "service_name": "sagemaker"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://runtime.sagemaker.your_aws_region.amazonaws.com/endpoints/your_sagemaker_endpoint_name/invocations",
      "headers": {
        "content-type": "application/json"
      },
      "request_body": "{"inputs":"${parameters.inputs}"}"
    }
  ]
}
```
Sample response:
```json
{
  "connector_id": "bLYgeJEBXV92Z6od5rXM"
}
```
Copy the `connector_id` from the response.

## 3. Register and Deploy the Model in OpenSearch

Use the `connector_id` from the previous step to register the model in OpenSearch.

```json
POST /_plugins/_ml/models/_register
{
  "name": "sagemaker-language-identification",
  "version": "1",
  "function_name": "remote",
  "description": "Remote model for language identification",
  "connector_id": "your_connector_id"
}
```
Sample response:
```json
{
  "task_id": "hbYheJEBXV92Z6oda7Xb",
  "status": "CREATED",
  "model_id": "hrYheJEBXV92Z6oda7X7"
}
```
Copy the `model_id` and deploy the model.

```json
POST /_plugins/_ml/models/your_model_id/_deploy
```

## 4. Test the Remote Model
You can now test the model directly to ensure it's working correctly.

```json
POST /_plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "inputs": "Say this is a test"
  }
}
```
The response should show the predicted language. Note the path to the label, `response[0].label`, which we will use in the ingest pipeline.
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "response": [
              {
                "label": "en",
                "score": 0.9411176443099976
              }
            ]
          }
        }
      ]
    }
  ]
}
```

---

# Building an Ingest Pipeline for Language Identification

## 1. Create an Ingest Pipeline

Before defining the pipeline, it's helpful to understand a few key principles of the `ml_inference` processor to avoid common configuration errors:

*   **Input and Output Mapping:** The `input_map` and `output_map` are arrays that are processed in order. The first entry in `input_map` corresponds to the first entry in `output_map`, the second to the second, and so on. Therefore, they must contain the same number of entries.
*   **Parsing Model Output:** The value in the `output_map` (e.g., `response[0].label`) is a path expression that extracts data from the model's JSON response for each prediction. You must inspect your model's prediction output to determine the correct path for the data you want to store.
*   **Processing Results:** It is a common pattern to follow the `ml_inference` processor with other processors, like `copy` processor, to act on the inference results. In this tutorial, for each field where we identify the language, we use a corresponding `copy` processor to create a new, language-specific field.

With these principles in mind, let's create the pipeline:

This pipeline uses the `ml_inference` processor to identify the language of the `name` and `notes` fields. It then uses a `copy` processor to create a new, language-specific field (e.g., `name_en`, `name_de`).

```json
PUT _ingest/pipeline/language_identification_pipeline
{
  "description": "Ingest pipeline to identify language and create language-specific fields",
  "processors": [
    {
      "ml_inference": {
        "model_id": "your_model_id",
        "input_map": [
          {
            "inputs": "name"
          },
          {
            "inputs": "notes"
          }
        ],
        "output_map": [
          {
            "predicted_name_language": "response[0].label"
          },
          {
            "predicted_notes_language": "response[0].label"
          }
        ]
      }
    },
    {
      "copy": {
        "source_field": "name",
        "target_field": "name_{{predicted_name_language}}",
        "ignore_missing": true
      }
    },
    {
      "copy": {
        "source_field": "notes",
        "target_field": "notes_{{predicted_notes_language}}",
        "ignore_missing": true
      }
    }
  ]
}
```

## 2. Create an Index with the Ingest Pipeline

Create a target index that uses the ingest pipeline by default. The index mapping includes language-specific analyzers for the fields that the pipeline will create.

```json
PUT /task_index
{
  "settings": {
    "index": {
      "default_pipeline": "language_identification_pipeline"
    }
  },
  "mappings": {
    "properties": {
      "name_en": { "type": "text", "analyzer": "english" },
      "name_es": { "type": "text", "analyzer": "spanish" },
      "name_de": { "type": "text", "analyzer": "german" },
      "notes_en": { "type": "text", "analyzer": "english" },
      "notes_es": { "type": "text", "analyzer": "spanish" },
      "notes_de": { "type": "text", "analyzer": "german" }
    }
  }
}
```

## 3. Ingest Documents

Now, ingest documents in different languages. The pipeline will automatically process them.

**English Document:**
```json
PUT /task_index/_doc/1
{
  "name": "Buy catnip",
  "notes": "Mittens really likes the stuff from Humboldt."
}
```
**Resulting Document:**
```json
{
  "_source": {
    "predicted_notes_language": "en",
    "name_en": "Buy catnip",
    "notes": "Mittens really likes the stuff from Humboldt.",
    "predicted_name_language": "en",
    "name": "Buy catnip",
    "notes_en": "Mittens really likes the stuff from Humboldt."
  }
}
```

**German Document:**
```json
PUT /task_index/_doc/2
{
  "name": "Kaufen Sie Katzenminze",
  "notes": "Mittens mag die Sachen von Humboldt wirklich."
}
```
**Resulting Document:**
```json
{
  "_source": {
    "predicted_notes_language": "de",
    "name_de": "Kaufen Sie Katzenminze",
    "notes": "Mittens mag die Sachen von Humboldt wirklich.",
    "predicted_name_language": "de",
    "name": "Kaufen Sie Katzenminze",
    "notes_de": "Mittens mag die Sachen von Humboldt wirklich."
  }
}
```

**Spanish Document:**
```json
PUT /task_index/_doc/3
{
  "name": "comprar hierba gatera",
  "notes": "A Mittens le gustan mucho las cosas de Humboldt."
}
```
**Resulting Document:**
```json
{
  "_source": {
    "predicted_notes_language": "es",
    "name_es": "comprar hierba gatera",
    "notes": "A Mittens le gustan mucho las cosas de Humboldt.",
    "predicted_name_language": "es",
    "name": "comprar hierba gatera",
    "notes_es": "A Mittens le gustan mucho las cosas de Humboldt."
  }
}
```

## 4. Search Documents

Thanks to the language-specific fields created by the pipeline, you can now perform multi-language searches using the appropriate analyzers.

```json
GET /task_index/_search
{
  "query": {
    "multi_match": {
      "query": "comprar",
      "fields": ["name_*"]
    }
  }
}
```
This search correctly finds the Spanish document because the `name_es` field is analyzed using the Spanish analyzer.
```json
{
  "hits": {
    "total": { "value": 1, "relation": "eq" },
    "max_score": 0.9331132,
    "hits": [
      {
        "_index": "task_index",
        "_id": "3",
        "_score": 0.9331132,
        "_source": {
          "name_es": "comprar hierba gatera",
          "notes": "A Mittens le gustan mucho las cosas de Humboldt.",
          "predicted_notes_language": "es",
          "predicted_name_language": "es",
          "name": "comprar hierba gatera",
          "notes_es": "A Mittens le gustan mucho las cosas de Humboldt."
        }
      }
    ]
  }
}
```
