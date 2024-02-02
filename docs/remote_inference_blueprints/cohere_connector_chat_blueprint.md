### Cohere Chat Connector Blueprint:

This blueprint will show you how to connect a Cohere chat model to your Opensearch instance. You will require a Cohere API key.

It is suggested to use the default `command` model for using Chat. Cohere's Chat endpoint also features Retrievel Augmented Generation (or RAG) parameters, by adding `connectors` or `documents` in your request body, in addition to allowing you to pass a `conversation_id` to provide context to Cohere's model. 

See [Cohere's /chat API docs](https://docs.cohere.com/reference/chat) for more details.

#### 1; Update your Opensearch cluster settings

```json
PUT /_cluster/settings

{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
          "^https://api\\.cohere\\.ai/.*$",
        ]
    }
}
```

If you have existing cluster settings, make sure to add them inside the array of endpoints.

#### 2. Create your Model connector and Model group

##### 2a. Register your Model group

```json
POST /_plugins/_ml/model_groups/_register

{
  "name": "cohere_model_group",
  "description": "Your Cohere model group"
}
```

This request response will return the `model_group_id`, note it down.

##### 2b. Create your Model connector

Refer to the [/chat API docs](https://docs.cohere.com/reference/chat) for more parameters. The below API request will create a connector that sets `connectors=[{"id": "web-search"}]`, by default, this will enable Web Search. You can restrict domains to search from, or add custom Cohere connectors as well. 

Later, when calling the newly created Predict action, you will pass in a `message` and `conversation_id` to the request. The `conversation_id` can be any string value.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Cohere Chat Model",
  "description": "The connector to Cohere's public chat API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "cohere_key": "<ENTER_COHERE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "command",
    "connectors": [{"id": "web-search"}]
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.ai/v1/chat",
      "headers": {
        "Authorization": "Bearer ${credential.cohere_key}",
        "Request-Source": "unspecified:opensearch"
      },
      "request_body": "{ \"message\": ${parameters.message}, \"model\": \"${parameters.model}\", \"connectors\": \"${parameters.connectors}\", \"conversation_id\": \"${parameters.conversation_id}\" }"
    }
  ]
}
```

This request response will return the `connector_id`, note it down.

##### 2c. Register your Model connector

You will now register the model you created using the `model_group_id` and `connector_id` from the previous requests.

```json
POST /_plugins/_ml/models/_register

{
    "name": "Cohere Chat Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your Cohere Chat Model",
    "connector_id": "<CONNECTOR_ID>"
}
```

This will create a registration task, the response should look like:

```json
{
  "task_id": "9bXpRY0BRil1qhQaUK-u",
  "status": "CREATED",
  "model_id": "9rXpRY0BRil1qhQaUK_8"
}
```

You can then check whether the registration task has completed with a GET request:

```json
GET /_plugins/_ml/tasks/<TASK_ID>
```

Once the response looks like the below example, where `state` is `COMPLETED`, your model will be ready for deployment.

```json
{
  "model_id": "9rXpRY0BRil1qhQaUK_8",
  "task_type": "REGISTER_MODEL",
  "function_name": "REMOTE",
  "state": "COMPLETED",
  "worker_node": ["ZYMBMV5RRbutADZtlk0c8w"],
  "create_time": 1706274934888,
  "last_update_time": 1706274935060,
  "is_async": false
}
```

##### 2d. Deploy your Model connector

The last step is to deploy the Model. Use the `model_id` returned by the registration request, and run:

```json
POST /_plugins/_ml/models/<MODEL_ID>/_deploy
```

This will once again spawn a task to deploy your Model, with a response that will look like:

```json
{
  "task_id": "97XrRY0BRil1qhQaQK_c",
  "task_type": "DEPLOY_MODEL",
  "status": "COMPLETED"
}
```

You can run the GET tasks request again to verify the status.

```json
GET /_plugins/_ml/tasks/<TASK_ID>
```

Once this is complete, your Model is deployed and ready!

##### 2e. Testing your Model

You can try this request to test that the Model behaves correctly:

Note: `conversation_id` here is the string value for "1", but you can use any string value, such as generating a uuidv4 string. This value will be org-specific and you will need to manage it on your application side.

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "message": "What is the weather like in London?",
    "conversation_id": "1"
  }
}
```

It should return a response similar to this:

```json
{
  "inference_results": [
    {
      "output": [
        {
            "response_id": "f92fdef6-e43c-465f-a2b8-45772b9ef39d",
            "conversation_id": "1",
            "text": "The weather on Thursday, February 1, 2018, in London will be an overcast high of 13°C and a low of 10°C. Unfortunately, I cannot give you a detailed weather forecast for the next ten days in London, as it varies considerably across different sources. Would you like to know more about the weather on any particular day within the next ten?",
            "generation_id": "76e5c68c-a3ca-40a0-91a9-20315f52b4c4",
            "token_count": {
                "prompt_tokens": 1523,
                "response_tokens": 74,
                "total_tokens": 1597,
                "billed_tokens": 81
            },
            "meta": {
                ...
            },
           
            "documents": [
                ...
            ],
            "search_results": [
                ...
            ],
            "tool_inputs": null,
            "search_queries": [
                ...
            ]
        }
      ]
    }
  ]
}
```

Congratulations! You've successfully created a chat connector.
