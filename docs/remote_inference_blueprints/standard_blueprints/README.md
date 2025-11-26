# Model Blueprints for Vector Search

OpenSearch provides two approaches for implementing embedding models, depending on your needs:

## Standard Blueprints (Using ML Inference Processor)
Recommended for new implementations for version after OS 2.14.0

These blueprints use the ML inference processor to handle input/output mapping, offering:

- Simpler implementation
- Direct model registration 
- Flexible input/output mapping through the processor

[List of available models]:
- Bedrock:
  - [amazon.titan-embed-text-v1](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/bedrock_connector_titan_embedding_standard_blueprint.md)
  - [amazon.titan-embed-image-v1](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/bedrock_connector_titan_multimodal_embedding_standard_blueprint.md)
  - [cohere.embed-english-v3](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/bedrock_connector_cohere_cohere.embed-english-v3_standard_blueprint.md)
  - [cohere.embed-multilingual-v3](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/bedrock_connector_cohere_cohere.embed-multilingual-v3_standard_blueprint.md)
- Cohere
  - text embedding: [embed-english-v3.0 & embed-english-v2.0](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/cohere_connector_text_embedding_standard_blueprint.md)
  - image embedding: [embed-multimodal-v3.0 & embed-multimodal-v2.0](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/cohere_connector_image_embedding_blueprint.md)
- OpenAI:
  - [text-embedding-ada-002](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/openai_connector_embedding_standard_blueprint.md)
- Yandex Cloud:
  - text embedding: [text-search-doc & text-search-query](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/standard_blueprints/yandexcloud_connector_embedding_standard_blueprint.md) 

## Legacy Blueprints (With Pre/Post Processing)
For existing implementations or specific customization needs

These blueprints include pre- and post-processing functions, suitable when you need:

- Custom preprocessing logic
- Specific output formatting requirements
- Compatibility with existing implementations

[List of available models]:

- Bedrock:
  - [amazon.titan-embed-text-v1](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_titan_embedding_blueprint.md)
  - [amazon.titan-embed-image-v1](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_titan_multimodal_embedding_blueprint.md)
  - [amazon.nova-2-multimodal-embeddings-v1:0](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_nova_multimodal_model_blueprint.md)
  - [cohere.embed-english-v3](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_cohere_cohere.embed-english-v3_blueprint.md)
  - [cohere.embed-multilingual-v3](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/bedrock_connector_cohere_cohere.embed-multilingual-v3_blueprint.md)
- Cohere
  - text embedding: [embed-english-v3.0 & embed-english-v2.0](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/cohere_connector_embedding_blueprint.md)
  - image embedding: [embed-multimodal-v3.0 & embed-multimodal-v2.0](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/cohere_connector_image_embedding_blueprint.md)

- OpenAI: 
  - [text-embedding-ada-002](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/openai_connector_embedding_blueprint.md)
  
- VertexAI
  - [embedding](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/gcp_vertexai_connector_embedding_blueprint.md)