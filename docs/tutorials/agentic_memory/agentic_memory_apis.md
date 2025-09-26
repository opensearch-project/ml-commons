# 1. Memory Container
## 1.1 Create memory container
```
POST _plugins/_ml/memory_containers/_create
{
  "name": "chatbot conatiner",
  "description": "Store conversations with semantic search and summarization",
  "configuration": {
    "index_prefix": "{{memory_index_prefix}}",
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
    ],
    "index_settings": {
      "session_index" : {
        "index": {
          "number_of_shards": "2",
          "number_of_replicas": "2"
        }
      },
      "short_term_memory_index" : {
        "index": {
          "number_of_shards": "2",
          "number_of_replicas": "2"
        }
      },
      "long_term_memory_index" : {
        "index": {
          "number_of_shards": "2",
          "number_of_replicas": "2"
        }
      },
      "long_term_memory_history_index" : {
        "index": {
          "number_of_shards": "2",
          "number_of_replicas": "2"
        }
      }
    }
  },
  "enable_history": true,
  "enable_session_tracking": true
}
```
## 1.2 Get memory container
```
GET _plugins/_ml/memory_containers/{{mem_container_id}}
```
## 1.3 Update memory container
```
PUT _plugins/_ml/memory_containers/{{mem_container_id}}
{
  "name": "new name updated by user1",
  "description": "new description",
  "backend_roles": [ // user has one of these backend roles could get/update/delete this memory container
    "test1", "test2"
  ]
}
```
## 1.4 Delete memory container
```
DELETE _plugins/_ml/memory_containers/{{mem_container_id}}
```
## 1.5 Search memory container
```

```

# 2. Working Memory
## 2.1 Add memory
```
GET _plugins/_ml/memory_containers/{{mem_container_id}}/memories
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
  "session_id": "XSEuiJkBeh2gPPwzjYVh",
  "working_memory_id": "XyEuiJkBeh2gPPwzjYWM"
}
```
## 2.2 Get working memory
```
GET _plugins/_ml/memory_containers/{{mem_container_id}}/memories/working/{{working_memory_id}}
```
Sample response
```
{
  "memory_container_id": "HudqiJkB1SltqOcZusVU",
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
    "session_id": "S-dqiJkB1SltqOcZ1cYO"
  },
  "infer": true,
  "tags": {
    "topic": "personal info"
  },
  "created_time": 1758930326804,
  "last_updated_time": 1758930326804
}
```
## 2.3 Search working memory
```
```

## 2.4 Delete working memory
```
DELETE _plugins/_ml/memory_containers/{{mem_container_id}}/memories/working/{{working_memory_id}}
```
# 3. Long Term Memory

## 3.1 Search long term memory
```
```

## 3.2 Update long term memory
```

```
## 3.3 Delete long term memory
```

```
# 4. Memory History
## 4.1 Search memory history
```
```
## 4.2 Delete memory history
```
```