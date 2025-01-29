# Topic


This tutorial introduces how to build RAG in Amazon managed OpenSearch with [DeepSeek Chat Model](https://api-docs.deepseek.com/api/create-chat-completion).
If you are not using Amazon OpenSearch, you can refer to [deepseek_connector_chat_blueprint](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/deepseek_connector_chat_blueprint.md).

Note: You should replace the placeholders with prefix `your_` with your own value


# Steps

## 0. Preparation

### Apply for DeepSeek API key

Apply for DeepSeek API key if you don't have one yet. 

### Create OpenSearch Cluster

Go to AWS OpenSearch console UI and create OpenSearch domain.

Note the domain ARN and URL. They will be used in following steps.

## 1. Create Secret
Store your DeepSeek API key in Secret Manager.

Use default value if not mentioned.

1. Open Secret Manager UI, click "Store a new secret".
2. Choose "Other type of secret" type.
2. Create "my_deepseek_key" key pais with your DeepSeek API key as value.
3. On next page, input `my_test_deepseek_secret` as secret name

Note the secret ARN which will be used in later steps.

## 2. Create IAM role
To use the secret created in Step1, we need to create an IAM role with read secret permission.
This IAM role will be configured in connector. Connector will use this role to read secret.

Go to IAM console, create IAM role `my_deepseek_secret_role` with:

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

Note the role ARN which will be used in later steps.

## 3. Configure IAM role in OpenSearch

### 3.1 Create IAM role for Signing create connector request

Generate a new IAM role specifically for signing your create connector request.


Create IAM role `my_create_deepseek_connector_role` with 
- Custom trust policy. Note: `your_iam_user_arn` is the IAM user which will run `aws sts assume-role` in step 3.1
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

Note this role ARN. It will be used in following steps.

### 3.2 Map backend role

1. Log in to your OpenSearch Dashboard and navigate to the "Security" page, which you can find in the left-hand menu.
2. Then click "Roles" on security page (you can find it on left-hand), then find "ml_full_access" role and click it. 
3. On "ml_full_access" role detail page, click "Mapped users", then click "Manage mapping". Paste IAM role ARN created in Step 3.1 to backend roles part.
Click "Map", then the IAM role configured successfully in your OpenSearch cluster.

![Alt text](images/semantic_search/mapping_iam_role_arn.png)

## 4. Create Connector

Find more details on [connector](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/connectors/)


### 4.1 Get temporary credential of the role created in step 2.1:

Use the credential of IAM user used in Step 3.1 to assume role

```
aws sts assume-role --role-arn your_iam_role_arn_created_in_step3.1 --role-session-name your_session_name
```

Copy the temporary credential from response and configure in `~/.aws/credentials` like this

```
[default]
AWS_ACCESS_KEY_ID=your_access_key_of_role_created_in_step2.1
AWS_SECRET_ACCESS_KEY=your_secret_key_of_role_created_in_step2.1
AWS_SESSION_TOKEN=your_session_token_of_role_created_in_step2.1
```

### 4.2 Create connector

Add DeepSeek API endpoint to trusted URLs
```
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
          "^https://api\\.deepseek\\.com/.*$"
        ]
    }
}
```

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
  "name": "DeepSeek Chat",
  "description": "Test connector for DeepSeek Chat",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "endpoint": "api.deepseek.com",
    "model": "deepseek-chat"
  },
  "credential": {
    "secretArn": "your_secret_arn_created_in_step1",
    "roleArn": "your_iam_role_arn_created_in_step2"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "Bearer ${credential.secretArn.my_deepseek_key}"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
    }
  ]
}

headers = {"Content-Type": "application/json"}

r = requests.post(url, auth=awsauth, json=payload, headers=headers)
print(r.status_code)
print(r.text)
```
The script will output connector id.

sample output
```
{"connector_id":"duRJsZQBFSAM-WcznrIw"}
```

Note connector id. It will be used in next step.

## 4. Create Model and test

Login your OpenSearch Dashboard, open DevTools, then run these

1. Create model group
```
POST /_plugins/_ml/model_groups/_register
{
    "name": "DeepSeek Chat model",
    "description": "Test model group for DeepSeek model"
}
```
Sample output
```
{
  "model_group_id": "UylKsZQBts7fa6byEx2M",
  "status": "CREATED"
}
```

2. Register model

```
POST /_plugins/_ml/models/_register
{
  "name": "DeepSeek Chat model",
  "function_name": "remote",
  "description": "DeepSeek Chat model",
  "model_group_id": "UylKsZQBts7fa6byEx2M",
  "connector_id": "duRJsZQBFSAM-WcznrIw"
}
```
Sample output
```
{
  "task_id": "VClKsZQBts7fa6bypR0a",
  "status": "CREATED",
  "model_id": "VSlKsZQBts7fa6bypR02"
}
```

3. Deploy model
```
POST /_plugins/_ml/models/VSlKsZQBts7fa6bypR02/_deploy
```
Sample output
```
{
  "task_id": "d-RKsZQBFSAM-Wcz3bKO",
  "task_type": "DEPLOY_MODEL",
  "status": "COMPLETED"
}
```
4. Predict
```
POST /_plugins/_ml/models/VSlKsZQBts7fa6bypR02/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Hello!"
      }
    ]
  },
  "stream": false
}
```
Sample response
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "id": "a351252c-7393-4c5d-9abe-1c47693ad336",
            "object": "chat.completion",
            "created": 1738141298,
            "model": "deepseek-chat",
            "choices": [
              {
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today? 😊"
                },
                "logprobs": null,
                "finish_reason": "stop"
              }
            ],
            "usage": {
              "prompt_tokens": 11,
              "completion_tokens": 11,
              "total_tokens": 22,
              "prompt_tokens_details": {
                "cached_tokens": 0
              },
              "prompt_cache_hit_tokens": 0,
              "prompt_cache_miss_tokens": 11
            },
            "system_fingerprint": "fp_3a5770e1b4"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 5. RAG

### 5.1 create search pipeline
Create search pipeline with [RAG processor](https://opensearch.org/docs/latest/search-plugins/search-pipelines/rag-processor/).

```
PUT /_search/pipeline/my-conversation-search-pipeline-deepseek-chat
{
  "response_processors": [
    {
      "retrieval_augmented_generation": {
        "tag": "Demo pipeline",
        "description": "Demo pipeline Using DeepSeek Chat",
        "model_id": "VSlKsZQBts7fa6bypR02",
        "context_field_list": [
          "text"
        ],
        "system_prompt": "You are a helpful assistant.",
        "user_instructions": "Generate a concise and informative answer in less than 100 words for the given question"
      }
    }
  ]
}
```
### 5.2 create vector database
Follow this [neural search tutorial](https://opensearch.org/docs/latest/search-plugins/neural-search-tutorial/) to create embedding model, K-NN index `my-nlp-index`, and ingest data
```
POST _bulk
{"index": {"_index": "my-nlp-index", "_id": "1"}}
{"text": "Chart and table of population level and growth rate for the Ogden-Layton metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of Ogden-Layton in 2023 is 750,000, a 1.63% increase from 2022.\nThe metro area population of Ogden-Layton in 2022 was 738,000, a 1.79% increase from 2021.\nThe metro area population of Ogden-Layton in 2021 was 725,000, a 1.97% increase from 2020.\nThe metro area population of Ogden-Layton in 2020 was 711,000, a 2.16% increase from 2019."}
{"index": {"_index": "my-nlp-index", "_id": "2"}}
{"text": "Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."}
{"index": {"_index": "my-nlp-index", "_id": "3"}}
{"text": "Chart and table of population level and growth rate for the Chicago metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Chicago in 2023 is 8,937,000, a 0.4% increase from 2022.\\nThe metro area population of Chicago in 2022 was 8,901,000, a 0.27% increase from 2021.\\nThe metro area population of Chicago in 2021 was 8,877,000, a 0.14% increase from 2020.\\nThe metro area population of Chicago in 2020 was 8,865,000, a 0.03% increase from 2019."}
{"index": {"_index": "my-nlp-index", "_id": "4"}}
{"text": "Chart and table of population level and growth rate for the Miami metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Miami in 2023 is 6,265,000, a 0.8% increase from 2022.\\nThe metro area population of Miami in 2022 was 6,215,000, a 0.78% increase from 2021.\\nThe metro area population of Miami in 2021 was 6,167,000, a 0.74% increase from 2020.\\nThe metro area population of Miami in 2020 was 6,122,000, a 0.71% increase from 2019."}
{"index": {"_index": "my-nlp-index", "_id": "5"}}
{"text": "Chart and table of population level and growth rate for the Austin metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Austin in 2023 is 2,228,000, a 2.39% increase from 2022.\\nThe metro area population of Austin in 2022 was 2,176,000, a 2.79% increase from 2021.\\nThe metro area population of Austin in 2021 was 2,117,000, a 3.12% increase from 2020.\\nThe metro area population of Austin in 2020 was 2,053,000, a 3.43% increase from 2019."}
{"index": {"_index": "my-nlp-index", "_id": "6"}}
{"text": "Chart and table of population level and growth rate for the Seattle metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Seattle in 2023 is 3,519,000, a 0.86% increase from 2022.\\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021.\\nThe metro area population of Seattle in 2021 was 3,461,000, a 0.82% increase from 2020.\\nThe metro area population of Seattle in 2020 was 3,433,000, a 0.79% increase from 2019."}
```


### 5.3 search
Run neural search to retrieve documents from vector database, then use DeepSeek model to do RAG.
```
GET /my-nlp-index/_search?search_pipeline=my-conversation-search-pipeline-deepseek-chat
{
  "query": {
    "neural": {
      "passage_embedding": {
        "query_text": "What's the population increase of New York City from 2021 to 2023?",
        "model_id": "USkHsZQBts7fa6bybx3G",
        "k": 5
      }
    }
  },
  "size": 4,
  "_source": [
    "text"
  ],
  "ext": {
    "generative_qa_parameters": {      
      "llm_model": "deepseek-chat",
      "llm_question": "What's the population increase of New York City from 2021 to 2023? How is the trending comparing with Miami?",
      "context_size": 5,
      "timeout": 15
    }
  }
}
```
Response
```
{
  "took": 5,
  "timed_out": false,
  "_shards": {
    "total": 5,
    "successful": 5,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 6,
      "relation": "eq"
    },
    "max_score": 0.05248103,
    "hits": [
      {
        "_index": "my-nlp-index",
        "_id": "2",
        "_score": 0.05248103,
        "_source": {
          "text": """Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."""
        }
      },
      {
        "_index": "my-nlp-index",
        "_id": "4",
        "_score": 0.029023321,
        "_source": {
          "text": """Chart and table of population level and growth rate for the Miami metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of Miami in 2023 is 6,265,000, a 0.8% increase from 2022.\nThe metro area population of Miami in 2022 was 6,215,000, a 0.78% increase from 2021.\nThe metro area population of Miami in 2021 was 6,167,000, a 0.74% increase from 2020.\nThe metro area population of Miami in 2020 was 6,122,000, a 0.71% increase from 2019."""
        }
      },
      {
        "_index": "my-nlp-index",
        "_id": "3",
        "_score": 0.028097045,
        "_source": {
          "text": """Chart and table of population level and growth rate for the Chicago metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of Chicago in 2023 is 8,937,000, a 0.4% increase from 2022.\nThe metro area population of Chicago in 2022 was 8,901,000, a 0.27% increase from 2021.\nThe metro area population of Chicago in 2021 was 8,877,000, a 0.14% increase from 2020.\nThe metro area population of Chicago in 2020 was 8,865,000, a 0.03% increase from 2019."""
        }
      },
      {
        "_index": "my-nlp-index",
        "_id": "6",
        "_score": 0.026973149,
        "_source": {
          "text": """Chart and table of population level and growth rate for the Seattle metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of Seattle in 2023 is 3,519,000, a 0.86% increase from 2022.\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021.\nThe metro area population of Seattle in 2021 was 3,461,000, a 0.82% increase from 2020.\nThe metro area population of Seattle in 2020 was 3,433,000, a 0.79% increase from 2019."""
        }
      }
    ]
  },
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "From 2021 to 2023, New York City's metro area population increased by 114,000, from 18,823,000 to 18,937,000, reflecting a growth rate of 0.61%. In comparison, Miami's metro area population grew by 98,000, from 6,167,000 to 6,265,000, with a higher growth rate of 1.59%. While New York City has a larger absolute population increase, Miami's population growth rate is significantly higher, indicating faster relative growth."
    }
  }
}
```