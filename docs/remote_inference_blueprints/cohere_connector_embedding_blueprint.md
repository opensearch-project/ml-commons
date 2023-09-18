### Cohere connector blueprint example for embedding:

#### this blueprint is created from Cohere doc: https://docs.cohere.com/reference/embed

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "credential": {
    "cohere_key": "<PLEASE ADD YOUR Cohere API KEY HERE>"
  },
  "parameters": {
    "model": "embed-english-v2.0",
    "truncate": "END"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.cohere.ai/v1/embed",
      "headers": {
        "Authorization": "Bearer ${credential.cohere_key}"
      },
      "request_body": "{ \"texts\": ${parameters.prompt}, \"truncate\": \"${parameters.truncate}\", \"model\": \"${parameters.model}\" }", 
      "pre_process_function": "connector.pre_process.cohere.embedding",
			"post_process_function": "connector.post_process.cohere.embedding"
    }
  ]
}
```
#### Sample response
```json
{
  "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```


### Corresponding Predict request example:

```json
POST /_plugins/_ml/models/<ENTER MODEL ID HERE>/_predict
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
          "name": "response",
          "dataAsMap": {
            "id": "39097276-d926-4ca1-92e6-d54a3c969d42",
            "texts": [
              "Say this is a test"
            ],
            "embeddings": [
              [
                -0.76953125,
                -0.12731934,
                -0.52246094,
                -1.2714844,
                ........
                ........
              ]
            ],
            "meta": {
              "api_version": {
                "version": "1"
              }
            }
          }
        }
      ]
    }
  ]
}
```
