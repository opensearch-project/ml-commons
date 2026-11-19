# Voyage AI Contextualized Embedding Connector Standard Blueprint:

This blueprint demonstrates how to deploy a [Voyage AI contextualized chunk embedding](https://docs.voyageai.com/docs/contextualized-chunk-embeddings) model (`voyage-context-4` or `voyage-context-3`) using the connector without pre and post processing functions. A contextualized embedding model embeds the chunks of a document together, so each chunk vector carries the context of the whole document.

The connector calls `POST /v1/contextualizedembeddings`. The built-in pre and post processing functions do not support this API, because the response nests chunk embeddings under each document. Use the ML inference processor (or read the raw response) to map the output.

The model is always required; the blueprint has no default. Both models return 1024-dimension vectors by default, and accept `output_dimension` 2048, 1024, 512 or 256.

## API constraints

These were checked against the live API:

- Auto-chunking only works with `input_type: "document"`. A `query` or missing `input_type` together with `enable_auto_chunking: true` returns HTTP 400 (`enable_auto_chunking=True requires input_type='document'`).
- With auto-chunking, `inputs` is a flat list of strings and each string is one document. A list of lists returns HTTP 400 (`auto-chunking expects one string per document`).
- The response returns the `text` of every chunk when the API chunked the document. Without auto-chunking there is no `text`.

So the blueprint uses two connectors: a document connector that always sends `enable_auto_chunking: true` and `chunk_size: 32000`, and a query connector that does not chunk.

## 1. Create the connectors

Document connector (auto-chunking):

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Voyage AI Contextualized Embed Model (document)",
  "description": "The connector to Voyage AI's contextualized embeddings API, documents are chunked by Voyage",
  "version": "1",
  "protocol": "http",
  "credential": {
    "voyage_key": "<ENTER_VOYAGE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>" // voyage-context-4 or voyage-context-3
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.voyageai.com/v1/contextualizedembeddings",
      "headers": {
        "Authorization": "Bearer ${credential.voyage_key}",
        "Content-Type": "application/json"
      },
      "request_body": "{ \"inputs\": ${parameters.inputs}, \"model\": \"${parameters.model}\", \"input_type\": \"document\", \"enable_auto_chunking\": true, \"chunk_size\": 32000 }"
    }
  ]
}
```

Query connector (no chunking, one inner list per query):

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Voyage AI Contextualized Embed Model (query)",
  "description": "The connector to Voyage AI's contextualized embeddings API, for search queries",
  "version": "1",
  "protocol": "http",
  "credential": {
    "voyage_key": "<ENTER_VOYAGE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>" // use the same model as the document connector
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.voyageai.com/v1/contextualizedembeddings",
      "headers": {
        "Authorization": "Bearer ${credential.voyage_key}",
        "Content-Type": "application/json"
      },
      "request_body": "{ \"inputs\": ${parameters.inputs}, \"model\": \"${parameters.model}\", \"input_type\": \"query\" }"
    }
  ]
}
```

To request a smaller embedding, add `\"output_dimension\": ${parameters.output_dimension}` to the request body and set it in the parameters.

## 2. Register the models and deploy

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Voyage AI Contextualized Embed Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your Voyage AI contextualized embedding model",
    "connector_id": "<CONNECTOR_ID>"
}
```

## 3. Test model inference

Document model: one string per document.

```json
POST /_plugins/_ml/models/<DOCUMENT_MODEL_ID>/_predict
{
  "parameters": {
    "inputs": ["First document. It has two sentences.", "Second document."]
  }
}
```

The response holds one entry per document under `data`, and one `embedding` per chunk inside it. `text` is the chunk text:

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
              {
                "object": "list",
                "index": 0,
                "data": [
                  {
                    "object": "embedding",
                    "index": 0,
                    "embedding": [0.0123, -0.0456, ...],
                    "text": "First document. It has two sentences."
                  }
                ]
              },
              {
                "object": "list",
                "index": 1,
                "data": [
                  {
                    "object": "embedding",
                    "index": 0,
                    "embedding": [0.0231, 0.0187, ...],
                    "text": "Second document."
                  }
                ]
              }
            ],
            "model": "voyage-context-4",
            "usage": {
              "total_tokens": 14
            },
            "chunker_version": "1.0.0"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

Query model: each query is a list with one string.

```json
POST /_plugins/_ml/models/<QUERY_MODEL_ID>/_predict
{
  "parameters": {
    "inputs": [["What does the first document say?"]]
  }
}
```

The query response has the same nesting, without `text` and `chunker_version`.

## 4. Map the output with the ML inference processor

For one document, the chunk texts and chunk embeddings are at `data[0].data[*].text` and `data[0].data[*].embedding` of the response. This ingest pipeline sends the `docs` field of a document (a list with one string per document) to the document model and stores both. It was run against `voyage-context-4`:

```json
PUT /_ingest/pipeline/voyage-contextualized-pipeline
{
  "processors": [
    {
      "ml_inference": {
        "model_id": "<DOCUMENT_MODEL_ID>",
        "input_map": [
          { "inputs": "docs" }
        ],
        "output_map": [
          {
            "chunk_texts": "data[0].data[*].text",
            "chunk_embeddings": "data[0].data[*].embedding"
          }
        ]
      }
    }
  ]
}
```
