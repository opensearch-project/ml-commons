# Image Support Test Examples

## Complete Image Flow Test

### 1. Input JSON (What user sends)
```json
{
  "input": [
    {
      "type": "text",
      "text": "Please analyze this image:"
    },
    {
      "type": "image",
      "source": {
        "type": "base64",
        "format": "png",
        "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
      }
    }
  ]
}
```

### 2. Parsed AgentInput Structure
After parsing, the AgentInput will contain:
- `input`: List<ContentBlock> with 2 items:
  1. ContentBlock with type=TEXT, text="Please analyze this image:"
  2. ContentBlock with type=IMAGE, image=ImageContent(type=BASE64, format="png", data="...")

### 3. ModelProvider Processing
BedrockConverseModelProvider.mapContentBlocks() will generate:
```json
{
  "body": "{\"role\":\"user\",\"content\":[{\"text\":\"Please analyze this image:\"},{\"image\":{\"format\":\"png\",\"source\":{\"bytes\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==\"}}}]}"
}
```

### 4. URL-based Image Example
```json
{
  "input": [
    {
      "type": "text", 
      "text": "What do you see in this image?"
    },
    {
      "type": "image",
      "source": {
        "type": "url",
        "format": "jpeg",
        "data": "https://example.com/image.jpg"
      }
    }
  ]
}
```

This will generate:
```json
{
  "body": "{\"role\":\"user\",\"content\":[{\"text\":\"What do you see in this image?\"},{\"image\":{\"format\":\"jpeg\",\"source\":{\"s3Location\":\"https://example.com/image.jpg\"}}}]}"
}
```

### 5. Message with Image Example
```json
{
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Can you help me with this image?"
        },
        {
          "type": "image",
          "source": {
            "type": "base64",
            "format": "png", 
            "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
          }
        }
      ]
    }
  ]
}
```

## Key Implementation Points

### ✅ AgentInput Parsing
- Detects `"type": "image"` in content blocks
- Creates ImageContent with SourceType, format, and data
- Handles both BASE64 and URL source types

### ✅ BedrockConverseModelProvider Processing  
- Maps BASE64 → "bytes" for Bedrock API
- Maps URL → "s3Location" for Bedrock API
- Generates proper JSON structure for Bedrock Converse API

### ✅ Supported Image Formats
- PNG, JPEG, GIF, WEBP (any format string)
- Base64 encoded data
- URL references (S3 or HTTP URLs)

## Testing Commands

```bash
# Test Base64 Image
curl -X POST "localhost:9200/_plugins/_ml/agents/your-agent-id/_execute" \
  -H "Content-Type: application/json" \
  -d '{
    "input": [
      {"type": "text", "text": "Analyze this image:"},
      {"type": "image", "source": {"type": "base64", "format": "png", "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="}}
    ]
  }'

# Test URL Image  
curl -X POST "localhost:9200/_plugins/_ml/agents/your-agent-id/_execute" \
  -H "Content-Type: application/json" \
  -d '{
    "input": [
      {"type": "text", "text": "What do you see?"},
      {"type": "image", "source": {"type": "url", "format": "jpeg", "data": "https://example.com/image.jpg"}}
    ]
  }'

# Test Message with Image
curl -X POST "localhost:9200/_plugins/_ml/agents/your-agent-id/_execute" \
  -H "Content-Type: application/json" \
  -d '{
    "input": [
      {
        "role": "user",
        "content": [
          {"type": "text", "text": "Help with this image:"},
          {"type": "image", "source": {"type": "base64", "format": "png", "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="}}
        ]
      }
    ]
  }'
```