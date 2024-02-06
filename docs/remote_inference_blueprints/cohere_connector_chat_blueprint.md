### Cohere Chat Connector Blueprint:

This blueprint will show you how to connect a Cohere chat model to your Opensearch cluster. You will require a Cohere API key to create a connector.

It is suggested to use the default `command` model for using Chat. Cohere's Chat endpoint also features Retrievel Augmented Generation (or RAG) parameters, by adding `connectors` or `documents` in your request body, in addition to allowing you to pass a `conversation_id` to provide context to Cohere's model. 

See [Cohere's /chat API docs](https://docs.cohere.com/reference/chat) for more details.

#### 1. Create your Model connector and Model group

##### 1a. Register Model group

```json
POST /_plugins/_ml/model_groups/_register

{
  "name": "cohere_model_group",
  "description": "Your Cohere model group"
}
```

This request response will return the `model_group_id`, note it down.

##### 1b. Create Connector

Refer to the [/chat API docs](https://docs.cohere.com/reference/chat) for more parameters. This API request will create a connector pointing to Cohere's /chat API using the default `command` model.

To enable Retrieval Augmented Generation (RAG) features, you can add any of these optional parameters:
- `connectors`: Specifies extra Cohere connectors to query prior to generation. Important note that these [connectors](https://docs.cohere.com/docs/connectors) differ from Opensearch connectors. These are connectors that are configurable and deployable within the Cohere ecosystem. You can enable Web search to start by passing in `[{"id": "web-search"}]` to this parameter. 
- `documents`: List of [documents](https://docs.cohere.com/docs/retrieval-augmented-generation-rag#document-mode) used to augment generation.
- `chat_history`: List of previous messages between the user and model. 

For more details on these, please refer to the [Cohere docs](https://docs.cohere.com/reference/chat).

Later, when calling the newly created Predict action, you will pass `message` and any extra parameters you've included to the request.

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
    "model": "command"
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
      "request_body": "{ \"message\": ${parameters.message}, \"model\": \"${parameters.model}\" }"
    }
  ]
}
```

This request response will return the `connector_id`, note it down.

##### 1c. Register your model with the connector

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

##### 1d. Deploy model

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

##### 1e. Test model

You can try this request to test that the Model behaves correctly:

Note: `conversation_id` here is the string value for "1", but you can use any string value, such as generating a uuidv4 string. This value will be org-specific and you will need to manage it on your application side.

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "message": "What is the weather like in London?",
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
