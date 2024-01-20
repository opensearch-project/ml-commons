### Cohere connector blueprint version 3.0 example for embedding:

#### this blueprint is created from Cohere doc: https://docs.cohere.com/reference/embed

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "cohere-embed-v3",
  "description": "The connector to public Cohere model service for embed",
  "version": "1",
  "protocol": "http",
  "credential": {
    "cohere_key": "<Your_API_Key>"
  },
  "parameters": {
    "model": "embed-english-v3.0",
    "input_type":"search_document",
    "truncate": "END"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.ai/v1/embed",
      "headers": {
        "Authorization": "Bearer ${credential.cohere_key}",
        "Request-Source": "unspecified:opensearch"
      },
      "request_body": "{ \"texts\": ${parameters.texts}, \"truncate\": \"${parameters.truncate}\", \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }",
      "pre_process_function": "connector.pre_process.cohere.embedding",
      "post_process_function": "connector.post_process.cohere.embedding"
    }
  ]
}
```
#### Sample response
```json
{
  "connector_id": "5tkeI4wBOQCMt0W51p18"
}
```

### Register and deploy an ML model before predicting:
```json
POST /_plugins/_ml/models/_register
{
  "name": "cohere embedding model v3",
  "function_name": "remote",
  "version": "1.0.0",
  "description": "test embedding model",
  "connector_id": "5tkeI4wBOQCMt0W51p18"
}
```

```json
POST /_plugins/_ml/models/7dkfI4wBOQCMt0W5Sp3F/_deploy
```
### Corresponding Predict request example:

```json
POST /_plugins/_ml/models/<ENTER ML MODEL ID HERE>/_predict
{
  "parameters": {
    "texts": ["Say this is a test"]
  }
}
```

#### Sample response
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
            -0.0024547577,
            0.0062217712,
            -0.01675415,
            -0.020736694,
            -0.020263672,
            ... ...
            0.038635254
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
