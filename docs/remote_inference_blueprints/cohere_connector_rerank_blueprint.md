### Cohere connector blueprint example for embedding:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Cohere Rerank Connector",
  "description": "The connector to Cohere Rerank Model",
  "version": 1,
  "protocol": "http",
  "parameters": {
    "endpoint": "api.cohere.ai",
    "model": "rerank-english-v2.0",
    "top_n": 3,
    "return_documents": true
  },
  "credential": {
    "api_key": "<PLEASE ADD YOUR Cohere API KEY HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/rerank",
      "headers": {
        "Authorization": "Bearer ${credential.api_key}"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"query\" : \"${parameters.query}\", \"documents\" : ${parameters.documents}, \"top_n\" : ${parameters.top_n}, \"return_documents\" : ${parameters.return_documents} }"
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
    "query": "What is the capital of the United States?",
    "documents": [
      "Carson City is the capital city of the American state of Nevada.",
      "The Commonwealth of the Northern Mariana Islands is a group of islands in the Pacific Ocean. Its capital is Saipan.",
      "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district.",
      "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
    ]
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
            "id": "c536e12e-c7fe-414c-9a00-d1c5c6757b34",
            "results": [
              {
                "document": {
                  "text": "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States. It is a federal district."
                },
                "index": 2,
                "relevance_score": 0.98005307
              },
              {
                "document": {
                  "text": "Capital punishment (the death penalty) has existed in the United States since beforethe United States was a country. As of 2017, capital punishment is legal in 30 of the 50 states."
                },
                "index": 3,
                "relevance_score": 0.27904198
              },
              {
                "document": {
                  "text": "Carson City is the capital city of the American state of Nevada."
                },
                "index": 0,
                "relevance_score": 0.10194652
              }
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
