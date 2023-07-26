# Topic
This doc explains how to use remote inference feature in ml-commons (This doc works for OpenSearch 2.9+).

As remote inference needs credential to ML service, always use this feature on security enabled cluster to protect your credential.

# Background
In [OpenSearch 2.4](https://opensearch.org/blog/opensearch-2-4-is-available-today/), ml-commons released an experimental model-serving framework which allows user to [upload text 
embedding model](https://github.com/opensearch-project/ml-commons/blob/main/docs/model_serving_framework/text_embedding_model_examples.md) and run it inside OpenSearch cluster. 
We call such models as local model. 
As model generally takes a lot of resources like memory/CPU, we suggest user always run model on [dedicated ML node](https://opensearch.org/docs/latest/ml-commons-plugin/index/#ml-node) for production environment.
[GPU acceleration](https://github.com/opensearch-project/ml-commons/blob/main/docs/model_serving_framework/GPU_support.md) also supported.


For some use case, user may prefer to run model outside OpenSearch cluster, for example
- User already have ML model running outside OpenSearch. For example, they already have model running on Amazon Sagemaker.
- User have big ML models which can't run inside OpenSearch cluster, like LLM.
- User prefer to use public ML service like OpenAI, Cohere, Anthropic etc.

In [OpenSearch 2.9](https://opensearch.org/blog/introducing-opensearch-2.9.0/), ml-commons introduces a virtual model to represent a model running outside OpenSearch cluster. We call such model as remote model.
To support remote model, ml-commons introduces a general connector concept which will defines protocol between ml-commons and external ML service. Remote model can leverage connector to communicate with external ML services.

As remote model is just invoking some ML service, it consumes little resources, so dedicated ML node is not so critical. You can disable this setting
[plugins.ml_commons.only_run_on_ml_node](https://opensearch.org/docs/latest/ml-commons-plugin/cluster-settings/#run-tasks-and-models-on-ml-nodes-only) to run remote model on data nodes
```
PUT /_cluster/settings
{
    "persistent" : {
        "plugins.ml_commons.only_run_on_ml_node" : false 
  }
}
```

# Model group
By default, ml-commons only checks the API level permission. For example user "A" created one model. If user "B" has permission to
update/delete model API, user "B" can update/delete user A's model, same for other APIs like deploy/undeploy etc. 

In 2.9, we released [ML model access control](https://opensearch.org/docs/latest/ml-commons-plugin/model-access-control/) which supports model level access control.
User can enable the feature by 
```
PUT /_cluster/settings
{
    "persistent" : {
        "plugins.ml_commons.model_access_control_enabled" : true 
  }
}
```
If this feature enabled, ml-commons will always check two things before user access a model:
- User has permission to the model API or not. This is what we ready have before. 
- User has permission to that model or not. For example, even user has permission to delete model API, they can't delete a model if they don't have access to this specific model. 

User may have multiple versions for same model. To make the access control easier, ml-commons introduced "model group" concept.
A "model group" will wrap a set of model versions. Model access control only happens on model group level. 
That means if user have access to a model group, they have access to all model versions in the group.

# Connector

A connector defines protocol between ml-commons and external ML service. Read this [connector doc](https://opensearch.org/docs/latest/ml-commons-plugin/connectors/) for more details.

A connector consists of 7 parts

1. `name`: string
2. `description`: string
3. `version`: string
4. `protocol`: string, In 2.9, we support two protocols : `http` and `aws_sigv4`
    * use "http" for any non-AWS services which support http, for example openAI , Cohere
    * use "aws_sigv4" for any AWS services which supports SigV4. For example, Amazon Sagemaker
5. `credential`: map<string, string>,  all variables in this block will be encrypted with "AES/GCM/NoPadding" symmetric encryption algorithm. The encryption key will be generated when the cluster first start and persisted in system index. User has no way to read/set that encryption key.  Credentials can only be used by `headers` of action.
6. `parameters`: map<string, object>. all variables in this block will be overridable in predict request. In predict request, user can provide same name parameter to override the default parameter value defined in connector.  Parameters can be used by `url` , `headers` and `request_body` of action.
7. `actions`: list,  it contains a list of action. For one action, it consists of 7 parts:
    * `action_type`: string,  we only support predict in 2.9
    * `method`: string, http method, we only support POST and GET in 2.9
    * `url`:  string, remote service url. User can use ${parameters.<key>} to use variables defined in parameters.
    * `headers`: map<string, string>, define http header, such as `Content-Type`. User can use `${credential.<key>}` to use variables defined in `credential` or use `${parameters.<key>}` to use variables defined in `parameters`.
    * `request_body`: string, http request body template, User can use `${parameters.<key>}` to use variables defined in `parameters`.
    * `pre_process_function`: string: painless script, pre-process the input data. We have built-in pre-process functions for cohere and openAI embedding models connector.pre_process.cohere.embedding and connector.pre_process.openai.embedding. You can also writer your own functions.
    * `post_process_function`: string: painless script, post-process the model output data. We have built-in post-process functions for cohere and openAI embedding models connector.post_process.cohere.embedding and connector.post_process.openai.embedding. You can also writer your own functions.

## connector settings
### connector access control
Similar to model access control, ml-commons also support access control for connector, you can enable by
```
PUT /_cluster/settings
{
    "persistent" : {
        "plugins.ml_commons.connector_access_control_enabled" : true 
  }
}
```
### trusted connector endpoint
ml-commons has a trusted connector endpoint regex setting. By default, it supports Amazon Sagemaker, OpenAI and Cohere. You can add your own regex by updating the setting.

For example, you can use this to add `my-test.ml.service.com` to trusted regex.
```
PUT /_cluster/settings
{
	"persistent": {
		"plugins.ml_commons.trusted_connector_endpoints_regex": [
			"^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
			"^https://api\\.openai\\.com/.*$",
			"^https://api\\.cohere\\.ai/.*$"
			"^https://my-test.ml.service.com/.*$"
		]
	}
}
```

## aws_sigv4 connector

We have some reserved parameter/credential names for connectors with `aws_sigv4` protocol:

* `access_key`: mandatory, must put into `credential` block.
* `secret_key`: mandatory, must put into `credential` block.
* `session_token`: optional, must put into `credential` block.
* `region: mandatory`, can put into `credential` or `parameters` block.
* `service_name`: mandatory, can put into `credential` or `parameters` block.

Example to create an Amazon Sagemaker connector:
```
POST /_plugins/_ml/connectors/_create
{
    "name": "sagemaker: embedding",
    "description": "Test connector for Sagemaker embedding model",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "<Your AWS account access key>",
        "secret_key": "<Your AWS account secret key>",
        "session_token": "<Your AWS account session token>"
    },
    "parameters": {
        "region": "<Your Amazon Sagemaker Region, for example us-west-2>",
        "service_name": "sagemaker"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "<Your Sagemaker inference endpoint>",
            "request_body": "<Your Sagemaker model model input, for example {\"input\": \"${parameters.inputText}\"}>"
        }
    ]
}
```
## http connector

No reserved parameter/credential names. 

Example to create an OpenAI Chat model connector:
```
POST /_plugins/_ml/connectors/_create
{
    "name": "OpenAI Chat Connector",
    "description": "The connector to public OpenAI model service for GPT 3.5",
    "version": 1,
    "protocol": "http",
    "parameters": {
        "endpoint": "api.openai.com",
        "model": "gpt-3.5-turbo"
    },
    "credential": {
        "openAI_key": "<Your OpenAI API key>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://${parameters.endpoint}/v1/chat/completions",
            "headers": {
                "Authorization": "Bearer ${credential.openAI_key}"
            },
            "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
        }
    ]
}
```


# Remote model

User can define a remote model with connector. User have two ways to create a remote model

- Remote model with connector id
- Remote model with an internal connector

Suggest you create a model group first, it will be used by other APIs later.

Sample request to create model group:
```
POST /_plugins/_ml/model_groups/_register
{
  "name": "my_remote_model_group",
  "description": "This is an example description"
}
```
Sample response
```
{
  "model_group_id": "WSmckYkBrQ7TyjgDAhjb",
  "status": "CREATED"
}
```

## Remote model with connector id

User can run `POST /_plugins/_ml/connectors/_create` to register a connector first. Then use the connector id to create a remote model.

### Step1: Create connector
Sample request: create OpenAI Chat model connector
```
POST /_plugins/_ml/connectors/_create
{
    "name": "OpenAI Chat Connector",
    "description": "The connector to public OpenAI model service for GPT 3.5",
    "version": 1,
    "protocol": "http",
    "parameters": {
        "model": "gpt-3.5-turbo"
    },
    "credential": {
        "openAI_key": "..."
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://api.openai.com/v1/chat/completions",
            "headers": {
                "Authorization": "Bearer ${credential.openAI_key}"
            },
            "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
        }
    ]
}
```
Sample response
```
{
  "connector_id": "a1eMb4kBJ1eYAeTMAljY"
}
```
### Step2: Create remote model
Then use the model group id `WSmckYkBrQ7TyjgDAhjb` and connector id `a1eMb4kBJ1eYAeTMAljY` to create a remote model.

Sample request:
```
POST /_plugins/_ml/models/_register
{
    "name": "openAI-gpt-3.5-turbo",
    "function_name": "remote",
    "model_group_id": "WSmckYkBrQ7TyjgDAhjb",
    "description": "test model",
    "connector_id": "a1eMb4kBJ1eYAeTMAljY"
}
```
Sample response
```
{
  "task_id": "cVeMb4kBJ1eYAeTMFFgj",
  "status": "CREATED"
}
```

Use get task API to find model id
```
GET /_plugins/_ml/tasks/cVeMb4kBJ1eYAeTMFFgj
```
Sample response, we can see the model id is `cleMb4kBJ1eYAeTMFFg4`
```
{
  "model_id": "cleMb4kBJ1eYAeTMFFg4",
  "task_type": "REGISTER_MODEL",
  "function_name": "REMOTE",
  "state": "COMPLETED",
  "worker_node": [
    "XPcXLV7RQoi5m8NI_jEOVQ"
  ],
  "create_time": 1689793598499,
  "last_update_time": 1689793598530,
  "is_async": false
}
```

### Step3: Deploy model
This step is not to deploy remote model to your external service. This step will read the remote model from model index, then decrypt credentials
and initialize remote model instance in OpenSearch cluster.

Sample request
```
POST /_plugins/_ml/models/cleMb4kBJ1eYAeTMFFg4/_deploy
```

Sample response
```
{
  "task_id": "vVePb4kBJ1eYAeTM7ljG",
  "status": "CREATED"
}
```
Use get task API to check if deploy is done.
```
GET /_plugins/_ml/tasks/vVePb4kBJ1eYAeTM7ljG
```
Sample response
```
{
  "model_id": "cleMb4kBJ1eYAeTMFFg4",
  "task_type": "DEPLOY_MODEL",
  "function_name": "REMOTE",
  "state": "COMPLETED",
  "worker_node": [
    "n-72khvBTBi3bnIIR8FTTw"
  ],
  "create_time": 1689793851077,
  "last_update_time": 1689793851101,
  "is_async": true
}
```

### Step4: Predict

Refer to OpenAI document: https://platform.openai.com/docs/api-reference/chat

Sample request
```
POST /_plugins/_ml/models/cleMb4kBJ1eYAeTMFFg4/_predict
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
          "name": "response",
          "dataAsMap": {
            "id": "chatcmpl-7e6s5DYEutmM677UZokF9eH40dIY7",
            "object": "chat.completion",
            "created": 1689793889,
            "model": "gpt-3.5-turbo-0613",
            "choices": [
              {
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today?"
                },
                "finish_reason": "stop"
              }
            ],
            "usage": {
              "prompt_tokens": 19,
              "completion_tokens": 9,
              "total_tokens": 28
            }
          }
        }
      ]
    }
  ]
}
```

## Remote model with internal connector
For standalone connector, user can reuse the connector id to create multiple remote models. 
User can also create remote model with some internal connector, if they don't need that reusability. 

### Step1: Create remote model
Don't input connector id, just define a connector inside model.

Sample request
```
POST /_plugins/_ml/models/_register
{
  "name": "openAI-GPT-3.5 completions: internal connector",
  "function_name": "remote",
  "model_group_id": "WSmckYkBrQ7TyjgDAhjb",
  "description": "test model",
  "connector": {
    "name": "OpenAI Chat Connector",
    "description": "The connector to public OpenAI model service for GPT 3.5",
    "version": 1,
    "protocol": "http",
    "parameters": {
      "model": "gpt-3.5-turbo"
    },
    "credential": {
      "openAI_key": "..."
    },
    "actions": [
      {
        "action_type": "predict",
        "method": "POST",
        "url": "https://api.openai.com/v1/chat/completions",
        "headers": {
          "Authorization": "Bearer ${credential.openAI_key}"
        },
        "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
      }
    ]
  }
}
```
Sample response
```
{
  "task_id": "_1eTb4kBJ1eYAeTMSVha",
  "status": "CREATED"
}
```

Use get task API to find model id
```
GET /_plugins/_ml/tasks/_1eTb4kBJ1eYAeTMSVha
```
Sample response
```
{
  "model_id": "AFeTb4kBJ1eYAeTMSVl0",
  "task_type": "REGISTER_MODEL",
  "function_name": "REMOTE",
  "state": "COMPLETED",
  "worker_node": [
    "XPcXLV7RQoi5m8NI_jEOVQ"
  ],
  "create_time": 1689794070873,
  "last_update_time": 1689794070911,
  "is_async": false
}
```

### Step2: Deploy model
Same as "Remote model with connector id",

Sample request
```
POST /_plugins/_ml/models/AFeTb4kBJ1eYAeTMSVl0/_deploy
```

### Step3: Predict
Same as "Remote model with connector id",

Sample request
```
POST _plugins/_ml/models/AFeTb4kBJ1eYAeTMSVl0/_predict
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
  }
}
```