### OpenAI connector blueprint example for batch inference:

Read more details on https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/

Integrate the OpenAI Batch API using the connector below with a new action type "batch_predict".
For more details of the OpenAI Batch API, please refer to https://platform.openai.com/docs/guides/batch/overview.

#### 1. Create your Model connector and Model group

##### 1a. Register Model group
```json
POST /_plugins/_ml/model_groups/_register
{
  "name": "openAI_model_group",
  "description": "Your openAI model group"
}
```
This request response will return the `model_group_id`, note it down.
Sample response:
```json
{
  "model_group_id": "IMobmY8B8aiZvtEZeO_i",
  "status": "CREATED"
}
```

##### 1b. Create Connector
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "OpenAI Embedding model",
  "description": "OpenAI embedding model for testing offline batch",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "model": "text-embedding-ada-002",
    "input_file_id": "file-YbowBByiyVJN89oSZo2Enu9W",
    "endpoint": "/v1/embeddings"
  },
  "credential": {
    "openAI_key": "<your openAI key>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.openai.com/v1/embeddings",
      "headers": {
        "Authorization": "Bearer ${credential.openAI_key}"
      },
      "request_body": "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\" }",
      "pre_process_function": "connector.pre_process.openai.embedding",
      "post_process_function": "connector.post_process.openai.embedding"
    },
    {
      "action_type": "batch_predict",
      "method": "POST",
      "url": "https://api.openai.com/v1/batches",
      "headers": {
        "Authorization": "Bearer ${credential.openAI_key}"
      },
      "request_body": "{ \"input_file_id\": \"${parameters.input_file_id}\", \"endpoint\": \"${parameters.endpoint}\", \"completion_window\": \"24h\" }"
    }
  ]
}
```
To create the file_id in the connector, please prepare your batch file and upload it to the OpenAI service through the file API. Please refer to this [Public doc](https://platform.openai.com/docs/api-reference/files)

#### Sample response
```json
{
  "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```

### 2. Register model to the model group and link the created connector:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "OpenAI model for realtime embedding and offline batch inference",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "OpenAI text embedding model",
    "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```
Sample response:
```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "lyjxwZABNrAVdFa9zrcZ"
}
```
### 3. Test offline batch inference using the connector

```json
POST /_plugins/_ml/models/lyjxwZABNrAVdFa9zrcZ/_batch_predict
{
  "parameters": {
    "model": "text-embedding-ada-002"
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
            "id": "batch_khFSJIzT0eev9PuxVDsIGxv6",
            "object": "batch",
            "endpoint": "/v1/embeddings",
            "errors": null,
            "input_file_id": "file-YbowBByiyVJN89oSZo2Enu9W",
            "completion_window": "24h",
            "status": "validating",
            "output_file_id": null,
            "error_file_id": null,
            "created_at": 1722037257,
            "in_progress_at": null,
            "expires_at": 1722123657,
            "finalizing_at": null,
            "completed_at": null,
            "failed_at": null,
            "expired_at": null,
            "cancelling_at": null,
            "cancelled_at": null,
            "request_counts": {
              "total": 0,
              "completed": 0,
              "failed": 0
            },
            "metadata": null
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
For the definition of each field in the result, please refer to https://platform.openai.com/docs/guides/batch. 
Once the batch is complete, you can download the output by making a request directly against the OpenAI Files API via the "id" field in the output.
