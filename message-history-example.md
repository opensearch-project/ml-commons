# Message History Processing Example

## Overview
The enhanced MLAgentExecutor now supports processing conversation history from message arrays, automatically storing Q&A pairs in memory before processing the current question.

## Example Input
```json
{
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "I like red"
        }
      ]
    },
    {
      "role": "assistant", 
      "content": [
        {
          "type": "text",
          "text": "Thanks for telling me that! I'll remember it."
        }
      ]
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text", 
          "text": "What colour do I like?"
        }
      ]
    }
  ]
}
```

## Processing Flow

### 1. Memory Creation (when memory_id not provided)
- **Conversation Name**: Uses first message text: "I like red"
- **Creates new conversation** with this name

### 2. Message History Processing
- **Processes Q&A pairs**: 
  - Q: "I like red" 
  - A: "Thanks for telling me that! I'll remember it."
- **Stores as completed interaction** in memory

### 3. Current Question Processing
- **Current Question**: "What colour do I like?" (last message)
- **Creates new interaction** for this question
- **Agent processes** with full conversation context available

## Benefits

1. **Full Context Preservation**: Agent has access to complete conversation history
2. **Proper Memory Structure**: Each Q&A pair is stored as a complete interaction
3. **Backward Compatibility**: Existing question-based agents continue to work
4. **Flexible Input**: Supports both new message arrays and legacy question format

## Implementation Details

- **Text Extraction**: Only text content blocks are stored (multi-modal support planned)
- **Pair Processing**: Messages processed in pairs (question → response)
- **Error Handling**: Failed history storage doesn't block current question processing
- **Async Processing**: All memory operations are asynchronous with proper error handling

## Memory Structure Result

After processing the example above, the memory will contain:

1. **Historical Interaction**:
   - Input: "I like red"
   - Response: "Thanks for telling me that! I'll remember it."
   - Status: Complete

2. **Current Interaction**:
   - Input: "What colour do I like?"
   - Response: (pending agent execution)
   - Status: In progress

This allows the agent to understand the context and provide a relevant response like "You mentioned that you like red."