# Bedrock InvokeModel Streaming for Agent Execute

Use `_llm_interface: "bedrock/invoke/claude"` to call Claude via Bedrock's InvokeModel API instead of the Converse API. This gives access to Claude-native features not available through Converse, such as message compaction (automatic context summarization), extended thinking, and model-specific sampling parameters.

## When to use this

| Feature | Converse (`bedrock/converse/claude`) | InvokeModel (`bedrock/invoke/claude`) |
|---|---|---|
| Text streaming | Yes | Yes |
| Tool calling | Yes | Yes |
| Message compaction | No | Yes |
| Extended thinking | No | Yes |
| Model-specific parameters | No | Yes |

## Setup

### 1. Register a model with an InvokeModel connector

The connector URL must point to the `/invoke` endpoint (not `/converse`). The `request_body` is the raw Claude Messages API payload -- you control the full request shape.

```
POST /_plugins/_ml/models/_register
{
  "name": "Claude via InvokeModel",
  "function_name": "remote",
  "connector": {
    "name": "Bedrock InvokeModel connector",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "us-east-1",
      "service_name": "bedrock",
      "model": "us.anthropic.claude-sonnet-4-20250514-v1:0"
    },
    "credential": {
      "access_key": "YOUR_ACCESS_KEY",
      "secret_key": "YOUR_SECRET_KEY",
      "session_token": "YOUR_SESSION_TOKEN"
    },
    "actions": [
      {
        "action_type": "predict",
        "method": "POST",
        "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
        "headers": {
          "content-type": "application/json"
        },
        "request_body": "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":4096,\"system\":\"${parameters.system_prompt}\",\"messages\":[${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.prompt}\"}]}${parameters._interactions:-}]${parameters.tool_configs:-}}"
      }
    ]
  }
}
```

### 2. Register an agent with `bedrock/invoke/claude`

```
POST /_plugins/_ml/agents/_register
{
  "name": "My Agent",
  "type": "conversational",
  "llm": {
    "model_id": "YOUR_MODEL_ID",
    "parameters": {
      "system_prompt": "You are a helpful assistant.",
      "prompt": "${parameters.question}"
    }
  },
  "memory": {
    "type": "conversation_index"
  },
  "parameters": {
    "_llm_interface": "bedrock/invoke/claude"
  },
  "tools": [
    {
      "type": "SearchIndexTool",
      "name": "search",
      "description": "Search an index for information.",
      "parameters": {
        "input": "{\"index\": \"my_index\", \"query\": ${parameters.query} }",
        "index": "my_index",
        "query": { "query": { "match_all": {} }, "size": 3 }
      },
      "attributes": {
        "input_schema": {
          "type": "object",
          "properties": {
            "question": { "type": "string", "description": "Search query" }
          },
          "required": ["question"]
        }
      }
    }
  ]
}
```

### 3. Execute with streaming

```
POST /_plugins/_ml/agents/YOUR_AGENT_ID/_execute/stream
{
  "parameters": {
    "question": "What can you find in the index?"
  }
}
```

## Enabling message compaction

Message compaction lets Claude automatically summarize earlier messages when the context grows too long. To enable it, include the `anthropic_beta` flags and `context_management` object in the connector's `request_body`.

### 1. Register a model with compaction enabled

The key differences from the basic connector above are the `anthropic_beta` array and the `context_management` object in the `request_body`.

```
POST /_plugins/_ml/models/_register
{
  "name": "Claude with compaction",
  "function_name": "remote",
  "connector": {
    "name": "Bedrock InvokeModel connector with compaction",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "us-east-1",
      "service_name": "bedrock",
      "model": "us.anthropic.claude-sonnet-4-20250514-v1:0"
    },
    "credential": {
      "access_key": "YOUR_ACCESS_KEY",
      "secret_key": "YOUR_SECRET_KEY",
      "session_token": "YOUR_SESSION_TOKEN"
    },
    "actions": [
      {
        "action_type": "predict",
        "method": "POST",
        "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
        "headers": {
          "content-type": "application/json"
        },
        "request_body": "{\"anthropic_version\":\"bedrock-2023-05-31\",\"anthropic_beta\":[\"interleaved-thinking-2025-05-14\",\"context-management-2025-05-14\"],\"max_tokens\":16000,\"context_management\":{\"enabled\":true},\"system\":\"${parameters.system_prompt}\",\"messages\":[${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.prompt}\"}]}${parameters._interactions:-}]${parameters.tool_configs:-}}"
      }
    ]
  }
}
```

### 2. Register an agent

```
POST /_plugins/_ml/agents/_register
{
  "name": "Agent with compaction",
  "type": "conversational",
  "llm": {
    "model_id": "YOUR_MODEL_ID",
    "parameters": {
      "system_prompt": "You are a helpful assistant.",
      "prompt": "${parameters.question}"
    }
  },
  "memory": {
    "type": "conversation_index"
  },
  "parameters": {
    "_llm_interface": "bedrock/invoke/claude"
  },
  "tools": [
    {
      "type": "SearchIndexTool",
      "name": "search",
      "description": "Search an index for information.",
      "parameters": {
        "input": "{\"index\": \"my_index\", \"query\": ${parameters.query} }",
        "index": "my_index",
        "query": { "query": { "match_all": {} }, "size": 3 }
      },
      "attributes": {
        "input_schema": {
          "type": "object",
          "properties": {
            "question": { "type": "string", "description": "Search query" }
          },
          "required": ["question"]
        }
      }
    }
  ]
}
```

### 3. Execute with streaming

```
POST /_plugins/_ml/agents/YOUR_AGENT_ID/_execute/stream
{
  "parameters": {
    "question": "Summarize everything we have discussed so far."
  }
}
```

When the conversation context exceeds the model's limit, Claude will emit compaction events containing a summary of the earlier messages. These are streamed to the client like regular text content.

To have the stream pause after compaction (so the caller can resume with the compacted context), set `"pause_after_compaction": true` inside `context_management` in the connector's `request_body`.

## How it works

- The payload from the connector is sent as raw bytes to `invokeModelWithResponseStream` -- no transformation. This means any Claude parameter you put in the `request_body` template is passed through.
- Streaming events are parsed by `ClaudeInvokeModelEventParser`, which maps Claude's SSE events (`message_start`, `content_block_delta`, etc.) to the handler's state machine.
- Tool use responses are emitted in the same Converse format (`$.output.message.content[*].toolUse`), so the existing `BedrockConverseFunctionCalling` works without changes.
