### OCIGenai Chat Connector Blueprint:

This blueprint will show you how to connect a OCI Genai chat model to your Opensearch cluster.

See [OCI GenAI Inference API docs](https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/) for more details.

#### 1. Create your Model connector and Model group

##### 1a. Register Model group

```json
POST /_plugins/_ml/model_groups/_register

{
  "name": "ocigenai_model_group",
  "description": "Your OCI Genai model group"
}
```

This request response will return the `model_group_id`, note it down.

##### 1b. Create Connector

Refer to the [OCI GenAI Inference API docs](https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/) for more parameters. This API request will create a connector pointing to OCI Genai API

Later, when calling the newly created Predict action, you will pass `prompt` and any extra parameters you've included to the request.

For authentication and authorization, you need to specify several parameters (Visit [OCI SDK Authentication Methods](https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/Content/API/Concepts/sdk_authentication_methods.htm) for information)
- auth_type: The type of authentication for OCI services, we support resource principal, instance principal, and user principal. 
- tenant_id: the customer tenancy ID (required for user principal authentication)
- user_id: the user ID (required for user principal authentication)
- fingerprint: the user fingerprint (required for user principal authentication)
- pemfile_path: the user private key path (required for user principal authentication)
- region: the OCI region (required for user principal authentication)

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "OCI Genai Chat Connector",
  "description": "The connector to OCI Genai chat API",
  "version": 2,
  "protocol": "oci_sigv1",
  "parameters": {
    "endpoint": "inference.generativeai.us-chicago-1.oci.oraclecloud.com/",
    "auth_type": "resource_principal"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/20231130/actions/generateText",
      "request_body": "{\"prompts\": [\"${parameters.prompt}\"], \"maxTokens\": 300, \"temperature\": 1, \"frequencyPenalty\": 0, \"presencePenalty\": 0, \"topP\": 0.75, \"topK\": 0, \"compartmentId\": \"ocid1.compartment.oc1..aaaaaaaadhycphouqp6xuxrvjoq7czfv44o2r5aoz53g3bw6temcu432t76a\", \"returnLikelihoods\": \"NONE\", \"isStpromptream\": false, \"stopSequences\": [], \"servingMode\": { \"modelId\": \"cohere.command\", \"servingType\": \"ON_DEMAND\" } }"
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
    "name": "OCI Genai Chat Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your OCI Genai Chat Model",
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

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "prompt": "What is the weather like in London?"
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
          "name": "response",
          "dataAsMap": {
            "inferenceResponse": {
              "generatedTexts": [
                {
                  "text": "The weather in London is known..."
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

Congratulations! You've successfully created a chat connector.
