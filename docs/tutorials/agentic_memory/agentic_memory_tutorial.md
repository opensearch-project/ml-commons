# Topic

This tutorial introduces how to use agentic memory.

# Steps

## 1. Enable agentic memory feature
```
PUT _cluster/settings
{
  "persistent": {
    "plugins.ml_commons.agentic_memory_enabled": true
  }
}
```

## 2. Prepare model

### 2.1 Embedding model

```
POST _plugins/_ml/models/_register
{
  "name": "Bedrock embedding model",
  "function_name": "remote",
  "description": "Embedding model for memory",
  "connector": {
    "name": "embedding",
    "description": "The connector to bedrock Titan embedding model",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "{{aws_region}}",
      "service_name": "bedrock",
      "model": "amazon.titan-embed-text-v2:0",
      "dimensions": 1024,
      "normalize": true,
      "embeddingTypes": [
        "float"
      ]
    },
    "credential": {
      "access_key": "{{access_key}}",
      "secret_key": "{{secret_key}}",
      "session_token": "{{session_token}}"
    },
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
}
```

### 2.1 LLM
```
POST _plugins/_ml/models/_register
{
  "name": "Bedrock infer model",
  "function_name": "remote",
  "description": "LLM model for memory processing",
  "connector": {
    "name": "Amazon Bedrock Connector: LLM",
    "description": "The connector to bedrock Claude 3.7 sonnet model",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "us-west-2",
      "service_name": "bedrock",
      "max_tokens": 8000,
      "temperature": 1,
      "anthropic_version": "bedrock-2023-05-31",
      "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0"
    },
    "credential": {
      "access_key": "{{access_key}}",
      "secret_key": "{{secret_key}}",
      "session_token": "{{session_token}}"
    },
    "actions": [{
      "action_type": "predict",
      "method": "POST",
      "headers": {"content-type": "application/json"},
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "request_body": "{ \"system\": \"${parameters.system_prompt}\", \"anthropic_version\": \"${parameters.anthropic_version}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature}, \"messages\": ${parameters.messages} }"
    }]
  }
}
```

## 3. Create memory container
```
POST _plugins/_ml/memory_containers/_create
{
  "name": "agentic memory test",
  "description": "Store conversations with semantic search and summarization",
  "configuration": {
    "index_prefix": "test1",
    "embedding_model_type": "TEXT_EMBEDDING",
    "embedding_model_id": "{{embed_model}}",
    "embedding_dimension": 1024,
    "llm_id": "{{llm}}",
    "strategies": [
      {
        "enabled": true,
        "type": "SEMANTIC",
        "namespace": [
          "user_id"
        ],
        "configuration": {
          "test": "value"
        }
      }
    ]
  }
}
```
This will create following indexes:

- `test1-session`: store the conversation session data
- `test1-short-term-memory`: store the short term memory: conversation messages (`conversation` type), or non-conversation message (`data` type)
- `test1-long-term-memory`: store the extracted facts from shor-term memories, only extract facts from  conversation messages (`conversation` type)
- `test1-long-term-memory-history`: stores the long term memory events: add/update/delete memory

TODOs: 
1. Add `"enable_history": false,` to control enable/disable tracking memory event history
2. Add `"enable_session_tracking": true,` to control enable/disable tracking session meta data.

## 3. Create data short-term memory

`data` short-term memory is to provide option to store non-conversational data in short-term memory.

It could be used for such use cases:
1. Remember key information in one Agent running. For example user ask error analysis for last week, we can store the time range for the whole session.
2. Build checkpoint for Agent run. Deep research agent may run for a long time with dozens of steps. If agent fails, for example exceeding some throttle limit, user doesn't need to rerun from the beginning.
3. Could be storage layer for Agent's ScratchPad. For example, user could store some SOP doc and use that to initialize ScratchPad.

```
POST /_plugins/_ml/memory_containers/{{mem_container_id}}/memories
{
  "structured_data": {
    "time_range": {
      "start": "2025-09-11",
      "end": "2025-09-15"
    }
  },
  "namespace": {
    "agent_id": "testAgent1"
  },
  "tags": {
    "topic": "agent_state"
  },
  "infer": false,
  "memory_type": "data"
}
```
Sample response
```
{
  "short_term_memory_id": "Z8xeTpkBvwXRq366l0iA"
}
```
Search `test1-long-term-memory` and `test1-long-term-memory-history`, see no long term memory added.

Search `test1-short-term-memory`, can see such sample response
```
{
    "_index": "test1-short-term-memory",
    "_id": "Z8xeTpkBvwXRq366l0iA",
    "_score": 1.0,
    "_source": {
      "memory_container_id": "C01hR5kBZYZ7d7266n2z",
      "memory_type": "data",
      "structured_data": {
        "time_range": {
          "start": "2025-09-11",
          "end": "2025-09-15"
        }
      },
      "namespace": {
        "agent_id": "testAgent1"
      },
      "infer": false,
      "tags": {
        "topic": "agent_state"
      },
      "created_time": "2025-09-15T17:14:06.07736704Z",
      "last_updated_time": "2025-09-15T17:14:06.07736704Z"
    }
}
```

## 4. Create conversation short-term memory

When add `conversation` memory, and set `"infer": true,`, the workflow will be:

1. Check if `session_id` exists in `namespace`. If no, create a new session and store in session index.
2. Save the conversation messages in shor term memory.
3. Run all strategies configured in container. For each strategy, will follow these steps
   1. Check if the strategy's namespace can match the input `namepsace`. For example strategy's namespace is `["user_id", "session_id"]`, then the input `namespace` must have `user_id` and `session_id`, it's ok for having other namespace keys like `agent_id`, but other keys will be ignored.
   2. If strategy's namespace can't match input `namespace`, skip running this strategy. Otherwise, go next step.
   3. Invoke LLM to extract long term memories.
   4. Search old memories with the same namespace as filter. 
   5. Compare old memory and new long term memories to decide the memory action: add/update/delete.
   6. Execute long term memory event to persist long term memory changes.

```
POST _plugins/_ml/memory_containers/{{mem_container_id}}/memories
{
  "messages": [
    {
      "role": "user",
      "content": "I'm Bob, I really like swimming."
    },
    {
      "role": "assistant",
      "content": "Cool, nice. Hope you enjoy your life."
    }
  ],
  "namespace": {
    "user_id": "bob"
  },
  "tags": {
    "topic": "personal info"
  },
  "infer": true,
  "memory_type": "conversation"
}
```
Sample response
```
{
  "session_id": "CcxjTpkBvwXRq366A1aE",
  "short_term_memory_id": "CsxjTpkBvwXRq366A1aJ"
}
```
Search `test1-short-term-memory`, can see a new doc
```
{
    "_index": "test1-short-term-memory",
    "_id": "CsxjTpkBvwXRq366A1aJ",
    "_score": 1.0,
    "_source": {
      "memory_container_id": "C01hR5kBZYZ7d7266n2z",
      "memory_type": "conversation",
      "messages": [
        {
          "role": "user",
          "content_text": "I'm Bob, I really like swimming."
        },
        {
          "role": "assistant",
          "content_text": "Cool, nice. Hope you enjoy your life."
        }
      ],
      "namespace": {
        "user_id": "bob",
        "session_id": "CcxjTpkBvwXRq366A1aE"
      },
      "infer": true,
      "tags": {
        "topic": "personal info"
      },
      "created_time": "2025-09-15T17:18:55.881276939Z",
      "last_updated_time": "2025-09-15T17:18:55.881276939Z"
    }
}
```
Search `test1-long-term-memory`, can see new docs
```
{
    "_index": "test1-long-term-memory",
    "_id": "DcxjTpkBvwXRq366C1Zz",
    "_score": 1.0,
    "_source": {
      "created_time": 1757956737699,
      "memory": "User's name is Bob",
      "last_updated_time": 1757956737699,
      "namespace_size": 1,
      "namespace": {
        "user_id": "bob"
      },
      "memory_type": "SEMANTIC",
      "tags": {
        "topic": "personal info"
      }
    }
},
{
    "_index": "test1-long-term-memory",
    "_id": "DsxjTpkBvwXRq366C1Zz",
    "_score": 1.0,
    "_source": {
      "created_time": 1757956737699,
      "memory": "Bob really likes swimming",
      "last_updated_time": 1757956737699,
      "namespace_size": 1,
      "namespace": {
        "user_id": "bob"
      },
      "memory_type": "SEMANTIC",
      "tags": {
        "topic": "personal info"
      }
    }
}
```
Search `test1-long-term-memory-history`, can see new docs
```
{
    "_index": "test1-long-term-memory-history",
    "_id": "D8xjTpkBvwXRq366C1bC",
    "_score": 1.0,
    "_source": {
      "created_time": "2025-09-15T17:18:57.702183535Z",
      "memory_id": null,
      "action": "ADD",
      "after": {
        "memory": "User's name is Bob"
      }
    }
},
{
    "_index": "test1-long-term-memory-history",
    "_id": "EMxjTpkBvwXRq366C1bC",
    "_score": 1.0,
    "_source": {
      "created_time": "2025-09-15T17:18:57.702461301Z",
      "memory_id": null,
      "action": "ADD",
      "after": {
        "memory": "Bob really likes swimming"
      }
    }
}
```

## 4. Update memory

```
POST /_plugins/_ml/memory_containers/{{mem_container_id}}/memories
{
  "messages": [
    {
      "role": "user",
      "content": "I don't like swimming now."
    }
  ],
  "namespace": {
    "user_id": "bob"
  },
  "tags": {
    "topic": "personal info"
  },
  "infer": true,
  "memory_type": "conversation"
}
```
Can see a new record added to shor-term memory index. 

Since the `namespace` is on user level, it will search old memories for `bob` and update.

Can see new events in `test1-long-term-memory-history`

```
{
    "_index": "test1-long-term-memory-history",
    "_id": "eMxnTpkBvwXRq366hmAU",
    "_score": 1.0,
    "_source": {
      "created_time": "2025-09-15T17:23:51.302920078Z",
      "memory_id": "DsxjTpkBvwXRq366C1Zz",
      "action": "DELETE",
      "after": {
        "memory": "Bob really likes swimming"
      }
    }
},
{
    "_index": "test1-long-term-memory-history",
    "_id": "ecxnTpkBvwXRq366hmAU",
    "_score": 1.0,
    "_source": {
      "created_time": "2025-09-15T17:23:51.303097838Z",
      "memory_id": null,
      "action": "ADD",
      "after": {
        "memory": "User doesn't like swimming currently"
      }
    }
}
```

## 5. Trace data 

### 5.1 Add trace data

`structured_data` is `flat_object` type, so it's flexible for agent to decide how to store trace data. For example store all trace data in one memory or store separately.
- option1: store all tool invocations in one short term memory
```
POST _plugins/_ml/memory_containers/{{mem_container_id}}/memories
{
  "structured_data": {
    "tool_invocations": [
      {
        "tool_name": "ListIndexTool",
        "tool_input": {
          "filter": "*,-.plugins*"
        },
        "tool_output": "green  open security-auditlog-2025.09.17   Kcc5QhHKQMuqGoepKta1_w 1 0   86 0 220.9kb 220.9kb\nyellow open test1-long-term-memory         HQbJLJPNR-yQPgpwIJ3Obg 1 1    0 0    208b    208b\nyellow open ss4o_logs-otel-2025.09.17      Rmlzz0FeRTyQM8osgleoYQ 1 1 4027 0   3.7mb   3.7mb\ngreen  open test1-short-term-memory        9NU9SW_0QQ63NpINuzfScQ 1 0    5 0  52.5kb  52.5kb\ngreen  open top_queries-2025.09.17-00414   HUxssrhFQFauoxg5YQ7KOg 1 0   19 0  55.7kb  55.7kb\ngreen  open test1-long-term-memory-history b7zqHp-eQleiPn-VMR93HQ 1 0    0 0    208b    208b\ngreen  open .opendistro_security           Rk5gENmgTPWfJ6fXgDfmyw 1 0    9 0  80.8kb  80.8kb\nyellow open jaeger-span-2025-09-17         UdAPt-ZcTuaapekonCHEWA 1 1 8150 0   7.5mb   7.5mb\ngreen  open test1-session                  IiL9VbeKQYqWNlImJuNKhA 1 0    2 0   5.2kb   5.2kb"
      },
      {
        "tool_name": "SearchIndexTool",
        "tool_input": {
          "index": "test_index",
          "query": {
            "_source": {
              "exclude": [
                "memory_embedding"
              ]
            }
          }
        },
        "tool_output": "sample output"
      }
    ]
  },
  "namespace": {
    "user_id": "bob",
    "agent_id": "testAgent1",
    "session_id": "123"
  },
  "tags": {
    "topic": "personal info",
    "parent_memory_id": "o4-WWJkBFT7urc7Ed9hM",
    "data_type": "trace"
  },
  "infer": false,
  "memory_type": "conversation"
}
```
Response
```
{
  "session_id": "123",
  "short_term_memory_id": "HL64WJkBE_hFTtoVFWp2"
}
```
- option2: store each tool invocation in one short term memory
```
POST _plugins/_ml/memory_containers/{{mem_container_id}}/memories
{
  "structured_data": {
    "tool_invocation": {
        "tool_name": "ListIndexTool",
        "tool_input": {
          "filter": "*,-.plugins*"
        },
        "tool_output": "green  open security-auditlog-2025.09.17   Kcc5QhHKQMuqGoepKta1_w 1 0   86 0 220.9kb 220.9kb\nyellow open test1-long-term-memory         HQbJLJPNR-yQPgpwIJ3Obg 1 1    0 0    208b    208b\nyellow open ss4o_logs-otel-2025.09.17      Rmlzz0FeRTyQM8osgleoYQ 1 1 4027 0   3.7mb   3.7mb\ngreen  open test1-short-term-memory        9NU9SW_0QQ63NpINuzfScQ 1 0    5 0  52.5kb  52.5kb\ngreen  open top_queries-2025.09.17-00414   HUxssrhFQFauoxg5YQ7KOg 1 0   19 0  55.7kb  55.7kb\ngreen  open test1-long-term-memory-history b7zqHp-eQleiPn-VMR93HQ 1 0    0 0    208b    208b\ngreen  open .opendistro_security           Rk5gENmgTPWfJ6fXgDfmyw 1 0    9 0  80.8kb  80.8kb\nyellow open jaeger-span-2025-09-17         UdAPt-ZcTuaapekonCHEWA 1 1 8150 0   7.5mb   7.5mb\ngreen  open test1-session                  IiL9VbeKQYqWNlImJuNKhA 1 0    2 0   5.2kb   5.2kb"
    }
  },
  "namespace": {
    "user_id": "bob",
    "agent_id": "testAgent1",
    "session_id": "123"
  },
  "tags": {
    "topic": "personal info",
    "parent_memory_id": "o4-WWJkBFT7urc7Ed9hM",
    "data_type": "trace"
  },
  "infer": false,
  "memory_type": "conversation"
}
```
### 5.2 Search trace data

- Search all trace data for a conversation
```
GET /{{memory_index_prefix}}-short-term-memory/_search
{
  "query": {
    "term": {
      "namespace.session_id": "123"
    }
  }
}
```

- Search trace data for a specific conversation message
```
GET /{{memory_index_prefix}}-short-term-memory/_search
{
  "query": {
    "term": {
      "tags.parent_memory_id": "o4-WWJkBFT7urc7Ed9hM"
    }
  }
}
```

- Search conversation message only, without trace data
```
GET /{{memory_index_prefix}}-short-term-memory/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "namespace.session_id": "123"
          }
        }
      ],
      "must_not": [
        {
          "exists": {
            "field": "tags.parent_memory_id"
          }
        }
      ]
    }
  }
}
```