# Voyage AI Multimodal Embedding Connector Standard Blueprint:

This blueprint demonstrates how to deploy a [Voyage AI multimodal embedding](https://docs.voyageai.com/docs/multimodal-embeddings) model using the connector without pre and post processing functions. Use the ML inference processor to handle input/output mapping.

| Model | Dimensions | Context length | Input |
|---|---|---|---|
| voyage-multimodal-3.5 | 1024 (default), 256, 512, 2048 | 32K tokens | text, images, video |
| voyage-multimodal-3 | 1024 | 32K tokens | text, images |

The model is always required; the blueprint has no default.

Limits (see the [API docs](https://docs.voyageai.com/reference/multimodal-embeddings-api)): at most 1000 inputs and 320K tokens per request, 32K tokens per input. An image counts one token per 560 pixels, and can be up to 16 million pixels and 20 MB. Image URLs must return a content-length header. Base64 images are data URLs (`data:<media type>;base64,<data>`) of type png, jpeg, webp or gif. The Voyage API docs say a request should use image URLs or base64 images consistently, not both, so keep them apart. A video can be up to 20 MB and must be an MP4; only `voyage-multimodal-3.5` accepts video (the other model returns HTTP 400 `does not support video inputs`).

## 1. Create a connector

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Voyage AI Multimodal Embed Model",
  "description": "The connector to Voyage AI's multimodal embeddings API",
  "version": "1",
  "protocol": "http",
  "credential": {
    "voyage_key": "<ENTER_VOYAGE_API_KEY_HERE>"
  },
  "parameters": {
    "model": "<ENTER_MODEL_NAME_HERE>", // voyage-multimodal-3.5 or voyage-multimodal-3
    "input_type": "document"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.voyageai.com/v1/multimodalembeddings",
      "headers": {
        "Authorization": "Bearer ${credential.voyage_key}",
        "Content-Type": "application/json"
      },
      "request_body": "{ \"inputs\": ${parameters.inputs}, \"model\": \"${parameters.model}\", \"input_type\": \"${parameters.input_type}\" }"
    }
  ]
}
```

`input_type` is `document` or `query`. Add `\"truncation\": ${parameters.truncation}` to the body to control truncation (the API default is `true`), and `\"output_dimension\": ${parameters.output_dimension}` for `voyage-multimodal-3.5` to change the vector size.

## 2. Register model and deploy

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Voyage AI Multimodal Embed Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your Voyage AI multimodal embedding model",
    "connector_id": "<CONNECTOR_ID>"
}
```

## 3. Test model inference

Text only, image URL, base64 image, video, or text with an image: every input is an object with a `content` list. A content item is `{"type": "text", "text": ...}`, `{"type": "image_url", "image_url": ...}`, `{"type": "image_base64", "image_base64": ...}`, `{"type": "video_url", "video_url": ...}` or `{"type": "video_base64", "video_base64": ...}`.

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "inputs": [
      {
        "content": [
          { "type": "text", "text": "A red square" },
          { "type": "image_url", "image_url": "https://example.com/red-square.png" }
        ]
      }
    ]
  }
}
```

With a base64 image (a data URL):

```json
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "inputs": [
      {
        "content": [
          { "type": "image_base64", "image_base64": "data:image/png;base64,<BASE64_IMAGE>" }
        ]
      }
    ]
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
            "object": "list",
            "data": [
              {
                "object": "embedding",
                "embedding": [0.0123, -0.0456, ...],
                "index": 0
              }
            ],
            "model": "voyage-multimodal-3.5",
            "usage": {
              "text_tokens": 3,
              "image_pixels": 50000,
              "video_pixels": 0,
              "total_tokens": 92
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
