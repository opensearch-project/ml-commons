# Model Connector Templates

This directory contains connector templates for various AI model providers and functions.

## Directory Structure

```
model-connectors/
├── <provider>/
│   ├── <model_id>.json
│   └── ...
└── ...
```

### Components

- **provider**: The model provider (e.g., `bedrock/text_embedding`, `openai`, `cohere`, `huggingface`)
- **model_id**: The specific model identifier with `:` replaced by `-` (e.g., `amazon.titan-embed-text-v2` for `amazon.titan-embed-text-v2:0`)

## Current Templates

### Bedrock

#### Text Embedding
- `bedrock/text_embedding/amazon.titan-embed-text-v2-0.json` - Amazon Titan Text Embeddings v2

## Template Format

Each template is a JSON file containing the base connector configuration:

```json
{
  "name": "Provider Model Name",
  "description": "Description of the connector",
  "version": 1,
  "protocol": "aws_sigv4",
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://...",
      "headers": {...},
      "request_body": "...",
      "pre_process_function": "...",
      "post_process_function": "..."
    }
  ]
}
```

**Note**: The `parameters` and `credential` blocks are NOT included in the template. They are provided by the user and injected at runtime.

## Adding New Templates

### 1. Create Directory Structure

```bash
mkdir -p common/src/main/resources/model-connectors/<provider>/
```

### 2. Create Template File

Create a JSON file named `<model_id>.json` (with `:` replaced by `-`):

```bash
# Example for OpenAI text-embedding-3-small
common/src/main/resources/model-connectors/openai/text_embedding/text-embedding-3-small.json
```

### 3. Template Content

Include only the base structure:
- `name`, `description`, `version`
- `protocol`
- `actions` array with action definitions

Do NOT include:
- `parameters` block (user-provided)
- `credential` block (user-provided)

### 4. Placeholders in Actions

Use placeholders in action URLs and request bodies:
- `${parameters.param_name}` - Will be filled from user-provided parameters
- `${credential.cred_name}` - Will be filled from user-provided credentials

## Examples

### Bedrock Titan Embedding

**File**: `bedrock/text_embedding/amazon.titan-embed-text-v2.json`

```json
{
  "name": "Amazon Bedrock Connector: embedding",
  "description": "Connector to bedrock embedding model",
  "version": 1,
  "protocol": "aws_sigv4",
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{ \"inputText\": \"${parameters.inputText}\", \"dimensions\": ${parameters.dimensions}, \"normalize\": ${parameters.normalize}, \"embeddingTypes\": ${parameters.embeddingTypes} }",
      "pre_process_function": "connector.pre_process.bedrock.embedding",
      "post_process_function": "connector.post_process.bedrock.embedding"
    }
  ]
}
```

### Future: OpenAI Embedding (Example)

**File**: `openai/text_embedding/text-embedding-3-small.json`

```json
{
  "name": "OpenAI Embedding Connector",
  "description": "Connector to OpenAI embedding model",
  "version": 1,
  "protocol": "http",
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.openai.com/v1/embeddings",
      "headers": {
        "Authorization": "Bearer ${credential.api_key}",
        "content-type": "application/json"
      },
      "request_body": "{ \"input\": \"${parameters.input}\", \"model\": \"${parameters.model}\" }",
      "pre_process_function": "connector.pre_process.openai.embedding",
      "post_process_function": "connector.post_process.openai.embedding"
    }
  ]
}
```

