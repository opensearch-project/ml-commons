# Topic

> An easy way is using python, check [AIConnectorHelper](./AIConnectorHelper.ipynb)

This doc introduces how to build semantic search in Amazon managed OpenSearch with [OpenAI embedding model](https://platform.openai.com/docs/guides/embeddings).
If you are not using Amazon OpenSearch, you can refer to [openai_connector_embedding_blueprint](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/remote_inference_blueprints/openai_connector_embedding_blueprint.md) and [OpenSearch semantic search](https://opensearch.org/docs/latest/search-plugins/semantic-search/).

Note: You should replace the placeholders with prefix `your_` with your own value

# Steps

## 0. Create OpenSearch cluster

Go to AWS OpenSearch console UI and create OpenSearch domain.

Copy the domain ARN which will be used in later steps.

## 1. Create secret
Store your OpenAI API key in Secret Manager.

Use default value if not mentioned.

1. Choose "Other type of secret" type.
2. Create a "my_openai_key" key pais with your OpenAI API key as value.
3. On next page, input `my_test_openai_secret` as secret name

Copy the secret ARN which will be used in later steps.

## 2. Create IAM role
To use the secret created in Step1, we need to create an IAM role with read secret permission.
This IAM role will be configured in connector. Connector will use this role to read secret.

Go to IAM console, create IAM role `my_openai_secret_role` with:

- Custom trust policy:
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "es.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
```
- Permission
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "secretsmanager:GetSecretValue",
                "secretsmanager:DescribeSecret"
            ],
            "Effect": "Allow",
            "Resource": "your_secret_arn_created_in_step1"
        }
    ]
}
```

Copy the role ARN which will be used in later steps.

## 3. Configure IAM role in OpenSearch

### 3.1 Create IAM role for Signing create connector request

Generate a new IAM role specifically for signing your create connector request.


Create IAM role `my_create_openai_connector_role` with 
- Custom trust policy. Note: `your_iam_user_arn` is the IAM user which will run `aws sts assume-role` in step 4.1
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "your_iam_user_arn"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
```
- permission
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "iam:PassRole",
            "Resource": "your_iam_role_arn_created_in_step2"
        },
        {
            "Effect": "Allow",
            "Action": "es:ESHttpPost",
            "Resource": "your_opensearch_domain_arn_created_in_step0"
        }
    ]
}
```

Copy this role ARN which will be used in later steps.

### 3.2 Map backend role

1. Log in to your OpenSearch Dashboard and navigate to the "Security" page, which you can find in the left-hand menu.
2. Then click "Roles" on security page (you can find it on left-hand), then find "ml_full_access" role and click it. 
3. On "ml_full_access" role detail page, click "Mapped users", then click "Manage mapping". Paste IAM role ARN created in Step 3.1 to backend roles part.
Click "Map", then the IAM role configured successfully in your OpenSearch cluster.

![Alt text](images/semantic_search/mapping_iam_role_arn.png)

## 4. Create Connector

Find more details on [connector](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/connectors/)


### 4.1 Get temporary credential of the role created in step 3.1:
```
aws sts assume-role --role-arn your_iam_role_arn_created_in_step3.1 --role-session-name your_session_name
```

Configure the temporary credential in `~/.aws/credentials` like this

```
[default]
AWS_ACCESS_KEY_ID=your_access_key_of_role_created_in_step3.1
AWS_SECRET_ACCESS_KEY=your_secret_key_of_role_created_in_step3.1
AWS_SESSION_TOKEN=your_session_token_of_role_created_in_step3.1
```

### 4.2 Create connector

Run this python code with the temporary credential configured in `~/.aws/credentials`
 
```
import boto3
import requests 
from requests_aws4auth import AWS4Auth

host = 'your_amazon_opensearch_domain_endpoint_created_in_step0'
region = 'your_amazon_opensearch_domain_region'
service = 'es'

credentials = boto3.Session().get_credentials()
awsauth = AWS4Auth(credentials.access_key, credentials.secret_key, region, service, session_token=credentials.token)

path = '/_plugins/_ml/connectors/_create'
url = host + path

payload = {
  "name": "OpenAI embedding model connector",
  "description": "Connector for OpenAI embedding model",
  "version": "1.0",
  "protocol": "http",
  "credential": {
    "secretArn": "your_secret_arn_created_in_step1",
    "roleArn": "your_iam_role_arn_created_in_step2"
  },
  "parameters": {
    "model": "text-embedding-ada-002"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.openai.com/v1/embeddings",
      "headers": {
        "Authorization": "Bearer ${credential.secretArn.my_openai_key}"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\" }",
      "pre_process_function": "connector.pre_process.openai.embedding",
      "post_process_function": "connector.post_process.openai.embedding"
    }
  ]
}

headers = {"Content-Type": "application/json"}

r = requests.post(url, auth=awsauth, json=payload, headers=headers)
print(r.text)
```
The script will output connector id.

sample output
```
{"connector_id":"OBUSRI0BTaDH9c7tUxfU"}
```
Copy connector id which will be used in later steps.
## 5. Create Model and test

Login your OpenSearch Dashboard, open DevTools, then run these

1. Create model group
```
POST /_plugins/_ml/model_groups/_register
{
    "name": "OpenAI_embedding_model",
    "description": "Test model group for OpenAI embedding model"
}
```
Sample output
```
{
  "model_group_id": "ORUSRI0BTaDH9c7t9heA",
  "status": "CREATED"
}
```

2. Register model

```
POST /_plugins/_ml/models/_register
{
  "name": "OpenAI embedding model",
  "function_name": "remote",
  "description": "test embedding model",
  "model_group_id": "ORUSRI0BTaDH9c7t9heA",
  "connector_id": "OBUSRI0BTaDH9c7tUxfU"
}
```
Sample output
```
{
  "task_id": "OhUTRI0BTaDH9c7tLhcv",
  "status": "CREATED",
  "model_id": "OxUTRI0BTaDH9c7tLhdE"
}
```

3. Deploy model
```
POST /_plugins/_ml/models/OxUTRI0BTaDH9c7tLhdE/_deploy
```
Sample output
```
{
  "task_id": "PkoTRI0BOhavBOmfkCmF",
  "task_type": "DEPLOY_MODEL",
  "status": "COMPLETED"
}
```
4. Predict
```
POST /_plugins/_ml/models/OxUTRI0BTaDH9c7tLhdE/_predict
{
  "parameters": {
    "input": ["hello world", "how are you"]
  }
}
```
Sample response
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1536
          ],
          "data": [
            -0.014907048,
            0.0013432145,
            -0.01851529,
            ...]
        },
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1536
          ],
          "data": [
            -0.014011521,
            -0.0067330617,
            -0.011700075,
            ...]
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 6. Semantic search

### 6.1 create ingest pipeline
Find more details: [ingest pipeline](https://opensearch.org/docs/latest/ingest-pipelines/)

```
PUT /_ingest/pipeline/my_openai_embedding_pipeline
{
    "description": "text embedding pentest",
    "processors": [
        {
            "text_embedding": {
                "model_id": "your_embedding_model_id_created_in_step5",
                "field_map": {
                    "text": "text_knn"
                }
            }
        }
    ]
}
```
### 6.2 create k-NN index
Find more details: [k-NN index](https://opensearch.org/docs/latest/search-plugins/knn/knn-index/)

You should customize your k-NN index for better performance.
```
PUT my_index
{
  "settings": {
    "index": {
      "knn.space_type": "cosinesimil",
      "default_pipeline": "my_openai_embedding_pipeline",
      "knn": "true"
    }
  },
  "mappings": {
    "properties": {
      "text_knn": {
        "type": "knn_vector",
        "dimension": 1536
      }
    }
  }
}
```
### 6.3 ingest test data
```
POST /my_index/_doc/1000001
{
    "text": "hello world."
}
```
### 6.4 search
Find more details: [neural search](https://opensearch.org/docs/latest/search-plugins/neural-search/).
```
POST /my_index/_search
{
  "query": {
    "neural": {
      "text_knn": {
        "query_text": "hello",
        "model_id": "your_embedding_model_id_created_in_step5",
        "k": 100
      }
    }
  },
  "size": "1",
  "_source": ["text"]
}
```