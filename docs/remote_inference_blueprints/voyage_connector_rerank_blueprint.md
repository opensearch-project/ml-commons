### Voyage AI Rerank Connector Blueprint:

This blueprint shows how to connect a [Voyage AI reranker](https://docs.voyageai.com/docs/reranker) to your OpenSearch cluster. You need a Voyage AI API key.

Models: `rerank-3`, `rerank-3-lite`, `rerank-2.5`, `rerank-2.5-lite`. The model is always required; the blueprint has no default.

See [Voyage AI's /v1/rerank API docs](https://docs.voyageai.com/reference/reranker-api) for the request and response fields. `documents` takes at most 1000 items.

There are two ways to use the connector.

#### A. With the `rerank` search processor (`text_similarity`)

This variant uses the built-in `connector.pre_process.voyage.rerank` and `connector.post_process.voyage.rerank` functions. The post-process function returns one score per document in the input order, which is what the `rerank` processor needs. So the request does not send `top_k`; Voyage returns the score of every document.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Voyage AI Rerank Model",
  "description": "The connector to Voyage AI's rerank API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "voyage_key": "<ENTER_VOYAGE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>" // rerank-3, rerank-3-lite, rerank-2.5 or rerank-2.5-lite
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.voyageai.com/v1/rerank",
      "headers": {
        "Authorization": "Bearer ${credential.voyage_key}",
        "Content-Type": "application/json"
      },
      "request_body": "{ \"query\": \"${parameters.query}\", \"documents\": ${parameters.documents}, \"model\": \"${parameters.model}\" }",
      "pre_process_function": "connector.pre_process.voyage.rerank",
      "post_process_function": "connector.post_process.voyage.rerank"
    }
  ]
}
```

Register and deploy the model with `POST /_plugins/_ml/models/_register?deploy=true` and the `connector_id`, then test it with the `text_similarity` input:

```json
POST /_plugins/_ml/_predict/text_similarity/<MODEL_ID>
{
  "query_text": "What is the capital of France?",
  "text_docs": ["Cats sleep a lot.", "Berlin is in Germany.", "Paris is the capital of France."]
}
```

The response has one `similarity` tensor per document, in the order of `text_docs`:

```json
{
  "inference_results": [
    {
      "output": [
        { "name": "similarity", "data_type": "FLOAT32", "shape": [1], "data": [0.2919] },
        { "name": "similarity", "data_type": "FLOAT32", "shape": [1], "data": [0.3595] },
        { "name": "similarity", "data_type": "FLOAT32", "shape": [1], "data": [0.9212] }
      ],
      "status_code": 200
    }
  ]
}
```

To rerank search results, create a search pipeline with the `rerank` processor and the `ml_opensearch` type that points to this model, as described in [Reranking search results](https://opensearch.org/docs/latest/search-plugins/search-relevance/reranking-search-results/).

#### B. With the raw response (ML inference processor)

This variant uses no pre or post processing functions. It sends `top_k` and `return_documents`, so only the best documents come back with their text. Use it with the `ml_inference` processor, or call it directly. The `rerank` search processor cannot use it.

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Voyage AI Rerank Model (raw)",
  "description": "The connector to Voyage AI's rerank API, raw response",
  "version": "1",
  "protocol": "http",
  "credential": {
    "voyage_key": "<ENTER_VOYAGE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>",
    "top_k": 3,
    "return_documents": true
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.voyageai.com/v1/rerank",
      "headers": {
        "Authorization": "Bearer ${credential.voyage_key}",
        "Content-Type": "application/json"
      },
      "request_body": "{ \"query\": \"${parameters.query}\", \"documents\": ${parameters.documents}, \"model\": \"${parameters.model}\", \"top_k\": ${parameters.top_k}, \"return_documents\": ${parameters.return_documents} }"
    }
  ]
}
```

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
    "query": "What is the capital of France?",
    "documents": ["Cats sleep a lot.", "Berlin is in Germany.", "Paris is the capital of France."],
    "top_k": 2
  }
}
```

The response lists the documents from the highest to the lowest `relevance_score`; `index` is the position in the input:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "object": "list",
            "data": [
              { "relevance_score": 0.9212, "index": 2, "document": "Paris is the capital of France." },
              { "relevance_score": 0.3600, "index": 1, "document": "Berlin is in Germany." }
            ],
            "model": "rerank-3",
            "usage": { "total_tokens": 41 }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
