# New Simplified Agent Input Format Examples

The user explicitly provides the `type` field to indicate what kind of content each item is.

## 1. Plain Text (No type needed - automatically detected)
```json
{
  "input": "hi does this work"
}
```

## 2. Multi-Modal Content (User provides `type` for each item)
```json
{
  "input": [
    {
      "type": "text",
      "text": "Analyze this image and video:"
    },
    {
      "type": "image", 
      "source": {
        "type": "base64",
        "format": "png",
        "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
      }
    },
    {
      "type": "video",
      "source": {
        "type": "url", 
        "format": "mp4",
        "data": "https://example.com/video.mp4"
      }
    },
    {
      "type": "document",
      "source": {
        "type": "url",
        "format": "pdf", 
        "data": "https://example.com/document.pdf"
      }
    }
  ]
}
```

## 3. Messages with Multi-Modal Content (User provides `role` and `type`)
```json
{
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Hello, can you help me with this image?"
        },
        {
          "type": "image",
          "source": {
            "type": "base64",
            "format": "jpeg", 
            "data": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k="
          }
        }
      ]
    },
    {
      "role": "assistant",
      "content": [
        {
          "type": "text",
          "text": "I can see the image you've shared. What would you like me to help you with regarding this image?"
        }
      ]
    },
    {
      "role": "user", 
      "content": [
        {
          "type": "text",
          "text": "What colors do you see in this image?"
        }
      ]
    }
  ]
}
```

## Key Points:

1. **Plain Text**: User just provides a string - no `type` needed
2. **Multi-Modal**: User explicitly provides `type` field for each content item (`text`, `image`, `video`, `document`)
3. **Messages**: User provides `role` field for messages, and each message's `content` array contains multi-modal items with user-provided `type` fields

The implementation correctly respects the user-provided `type` fields and automatically detects the overall format based on the structure.

## Complete API Request Examples

### Text Input
```bash
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": "What is machine learning?"
}
```

### Multi-modal Input
```bash
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": [
    {
      "type": "text",
      "text": "Describe this image:"
    },
    {
      "type": "image",
      "source": {
        "type": "url",
        "format": "png",
        "data": "https://example.com/image.png"
      }
    }
  ]
}
```

### Conversation Input
```bash
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Hello!"
        }
      ]
    },
    {
      "role": "assistant",
      "content": [
        {
          "type": "text",
          "text": "Hi there! How can I help you today?"
        }
      ]
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Can you explain quantum computing?"
        }
      ]
    }
  ]
}
```