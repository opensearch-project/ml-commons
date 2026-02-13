# Topic

This tutorial shows how to use the unified agent interface with OpenAI chat completion API. 
We will use OpenAI GPT-5 in this tutorial.

Note: Replace the placeholders with your own values.

# Steps

## 1. Create Memory Container

```
POST _plugins/_ml/memory_containers/_create
{
  "name": "My V2 Agent Memory",
  "description": "Memory container for multi-modal conversations"
}
```
Sample response
```
{
  "memory_container_id": "bjoyVJwBZTQZft5FDjfv",
  "status": "created"
}
```

## 2. Create Agent

Note: must use `openai_api_key` in credential, this is a reserved key.
```
POST _plugins/_ml/agents/_register
{
    "name": "OpenAI Multi-modal Agent",
    "type": "conversational_v2",
    "description": "V2 agent with full multi-modal support",
    "model": {
        "model_id": "gpt-5",
        "model_provider": "openai/v1/chat/completions",
        "credential": {
            "openai_api_key": "{{openai_key}}"
        }
    },
    "tools": [
        {
            "type": "ListIndexTool"
        },
        {
            "type": "IndexMappingTool"
        },
        {
            "type": "SearchIndexTool"
        }
    ],
    "memory": {
        "type": "agentic_memory",
        "memory_container_id": "{{unified_agent_mem_container_openai}}"
    },
    "parameters": {
        "system_prompt": "You are a helpful AI assistant with access to document search capabilities."
    }
}
```
Sample response
```
{
  "agent_id": "uzoyVJwBZTQZft5F9Dl7"
}
```

### Get agent
```
POST _plugins/_ml/agents/{{unified_agent_openai}}
```
Sample response
```
{
  "name": "OpenAI Multi-modal Agent",
  "type": "conversational_v2",
  "description": "V2 agent with full multi-modal support",
  "llm": {
    "model_id": "ujoyVJwBZTQZft5F9Dlt"
  },
  "model": {
    "model_id": "gpt-5",
    "model_provider": "openai/v1/chat/completions"
  },
  "tools": [
    {
      "type": "ListIndexTool",
      "include_output_in_agent_response": false
    },
    {
      "type": "IndexMappingTool",
      "include_output_in_agent_response": false
    },
    {
      "type": "SearchIndexTool",
      "include_output_in_agent_response": false
    }
  ],
  "parameters": {
    "model_provider": "openai/v1/chat/completions",
    "system_prompt": "You are a helpful AI assistant with access to document search capabilities."
  },
  "memory": {
    "type": "agentic_memory",
    "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
  },
  "created_time": 1770939151483,
  "last_updated_time": 1770939151483,
  "is_hidden": false
}
```
### Get model

```
GET _plugins/_ml/models/ujoyVJwBZTQZft5F9Dlt
```
```json
{
  "name": "Auto-generated model for gpt-5",
  "model_group_id": "Us3qUJwBBhekawnmVFJp",
  "algorithm": "REMOTE",
  "model_version": "2",
  "description": "Auto-generated model for agent",
  "model_state": "REGISTERED",
  "created_time": 1770939151462,
  "last_updated_time": 1770939151462,
  "is_hidden": false,
  "connector": {
    "name": "Auto-generated OpenAI connector for Agent",
    "version": "1",
    "description": "Auto-generated connector for OpenAI Chat Completions API",
    "protocol": "http",
    "parameters": {
      "model": "gpt-5"
    },
    "actions": [
      {
        "action_type": "PREDICT",
        "method": "POST",
        "url": "https://api.openai.com/v1/chat/completions",
        "headers": {
          "Authorization": "Bearer ${credential.openai_api_key}",
          "Content-Type": "application/json"
        },
        "request_body": "{\"model\":\"${parameters.model}\",\"messages\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]${parameters.tool_configs:-}}"
      }
    ],
    "client_config": {
      "max_connection": 30,
      "connection_timeout": 30000,
      "read_timeout": 30000,
      "retry_backoff_millis": 200,
      "retry_timeout_seconds": 30,
      "max_retry_times": 3,
      "retry_backoff_policy": "constant",
      "skip_ssl_verification": false
    }
  }
}
```
Test model 
```
POST _plugins/_ml/models/{{unified_agent_llm_openai}}/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant",
    "body": {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "What is your name?"
        }
      ]
    }
  }
}
```
Sample response
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "id": "chatcmpl-D8aUFJ9KYd3mMKAKD8dVb4VisxES8",
            "object": "chat.completion",
            "created": 1770939251,
            "model": "gpt-5-2025-08-07",
            "choices": [
              {
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "I’m ChatGPT. How can I help you today?",
                  "refusal": null,
                  "annotations": []
                },
                "finish_reason": "stop"
              }
            ],
            "usage": {
              "prompt_tokens": 11,
              "completion_tokens": 85,
              "total_tokens": 96,
              "prompt_tokens_details": {
                "cached_tokens": 0,
                "audio_tokens": 0
              },
              "completion_tokens_details": {
                "reasoning_tokens": 64,
                "audio_tokens": 0,
                "accepted_prediction_tokens": 0,
                "rejected_prediction_tokens": 0
              }
            },
            "service_tier": "default",
            "system_fingerprint": null
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## Execute Agent

### Text input

#### Run agent
```
POST /_plugins/_ml/agents/{{unified_agent_openai}}/_execute
{
  "parameters": {
    "session_id": "openai_session_1001"
  },
  "input": "What is the capital of France?"
}
```

Sample response
```
{
  "stop_reason": "stop",
  "message": {
    "role": "assistant",
    "content": [
      {
        "text": "Paris."
      }
    ]
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 507,
      "outputTokens": 11,
      "totalTokens": 518
    }
  }
}
```
Continue a session
```
POST /_plugins/_ml/agents/{{unified_agent_openai}}/_execute
{
  "parameters": {
    "session_id": "openai_session_1001"
  },
  "input": "Translate last answer to Chinese"
}
```

Run multiple tools
```
POST _plugins/_ml/agents/{{unified_agent_openai}}/_execute
{
  "parameters": {
    "session_id": "openai_session_1001_3"
  },
  "input": "How many indices in my opensearch cluster? Get 2 documents from opensearch_dashboards_sample_data_flights index. If possible, let's run multile tools in parrallel to reduce latency"
}
```
Sample response
```
{
  "stop_reason": "stop",
  "message": {
    "role": "assistant",
    "content": [
      {
        "text": "- Total indices in the cluster: 23\n\n- Two documents from opensearch_dashboards_sample_data_flights (match_all, size=2):\n  - _id: qszpUJwBBhekawnm440r\n    _source: {\"FlightNum\":\"9HY9SWR\",\"Origin\":\"Frankfurt am Main Airport\",\"OriginLocation\":{\"lon\":\"8.570556\",\"lat\":\"50.033333\"},\"DestLocation\":{\"lon\":\"151.177002\",\"lat\":\"-33.94609833\"},\"FlightDelay\":false,\"DistanceMiles\":10247.856675613455,\"FlightTimeMin\":1030.7704158599038,\"OriginWeather\":\"Sunny\",\"dayOfWeek\":0,\"AvgTicketPrice\":841.2656419677076,\"Carrier\":\"OpenSearch Dashboards Airlines\",\"FlightDelayMin\":0,\"OriginRegion\":\"DE-HE\",\"FlightDelayType\":\"No Delay\",\"DestAirportID\":\"SYD\",\"timestamp\":\"2026-02-02T00:00:00\",\"Dest\":\"Sydney Kingsford Smith International Airport\",\"FlightTimeHour\":17.179506930998397,\"Cancelled\":false,\"DistanceKilometers\":16492.32665375846,\"OriginCityName\":\"Frankfurt am Main\",\"DestWeather\":\"Rain\",\"OriginCountry\":\"DE\",\"DestCountry\":\"AU\",\"DestRegion\":\"SE-BD\",\"OriginAirportID\":\"FRA\",\"DestCityName\":\"Sydney\"}\n  - _id: q8zpUJwBBhekawnm440r\n    _source: {\"FlightNum\":\"X98CCZO\",\"Origin\":\"Cape Town International Airport\",\"OriginLocation\":{\"lon\":\"18.60169983\",\"lat\":\"-33.96480179\"},\"DestLocation\":{\"lon\":\"12.3519\",\"lat\":\"45.505299\"},\"FlightDelay\":false,\"DistanceMiles\":5482.606664853586,\"FlightTimeMin\":464.3894810759016,\"OriginWeather\":\"Clear\",\"dayOfWeek\":0,\"AvgTicketPrice\":882.9826615595518,\"Carrier\":\"Logstash Airways\",\"FlightDelayMin\":0,\"OriginRegion\":\"SE-BD\",\"FlightDelayType\":\"No Delay\",\"DestAirportID\":\"VE05\",\"timestamp\":\"2026-02-02T18:27:00\",\"Dest\":\"Venice Marco Polo Airport\",\"FlightTimeHour\":7.73982468459836,\"Cancelled\":false,\"DistanceKilometers\":8823.40014044213,\"OriginCityName\":\"Cape Town\",\"DestWeather\":\"Sunny\",\"OriginCountry\":\"ZA\",\"DestCountry\":\"IT\",\"DestRegion\":\"IT-34\",\"OriginAirportID\":\"CPT\",\"DestCityName\":\"Venice\"}\n\nNote: I ran the index listing and the document fetch in parallel to reduce latency."
      }
    ]
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 4731,
      "outputTokens": 2343,
      "totalTokens": 7074
    }
  }
}
```
#### Get memory

```
GET _plugins/_ml/memory_containers/{{unified_agent_mem_container_openai}}/memories/working/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "namespace.session_id": "openai_session_1001"
          }
        }
      ]
    }
  },
  "sort": [
    {
      "message_id": {
        "order": "desc"
      }
    }
  ]
}
```
Sample response
```
{
  "took": 1,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 2,
      "relation": "eq"
    },
    "max_score": null,
    "hits": [
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "Mzo1VJwBZTQZft5FmkCH",
        "_version": 1,
        "_seq_no": 96,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939325063,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:35:25.062356809Z",
            "created_at": "2026-02-12T23:35:25.062356809Z",
            "message_id": 1,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "Paris."
                }
              ]
            }
          },
          "last_updated_time": 1770939325063,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "openai_session_1001"
          },
          "message_id": 1,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          1
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "LTo1VJwBZTQZft5FhEC6",
        "_version": 1,
        "_seq_no": 95,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939319481,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:35:19.481391871Z",
            "created_at": "2026-02-12T23:35:19.481391871Z",
            "message_id": 0,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "What is the capital of France?"
                }
              ]
            }
          },
          "last_updated_time": 1770939319481,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "openai_session_1001"
          },
          "message_id": 0,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          0
        ]
      }
    ]
  }
}
```

### Content blocks input
#### Run agent
```
POST _plugins/_ml/agents/{{unified_agent_openai}}/_execute
{
  "parameters": {
    "session_id": "session_20001"
  },
  "input": [
    {
      "type": "text",
      "text": "What's in this image?"
    },
    {
      "type": "image",
      "source": {
        "type": "base64",
        "format": "png",
        "data": "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"
      }
    }
  ]
}
```
Sample response
```
{
  "stop_reason": "stop",
  "message": {
    "role": "assistant",
    "content": [
      {
        "text": "It’s a small screenshot showing the text “Hello World!!” in a monospaced font on a dark background."
      }
    ]
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 716,
      "outputTokens": 161,
      "totalTokens": 877
    }
  }
}
```
#### Get memory
```
GET _plugins/_ml/memory_containers/{{unified_agent_mem_container_openai}}/memories/working/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "namespace.session_id": "session_20001"
          }
        }
      ]
    }
  },
  "sort": [
    {
      "message_id": {
        "order": "desc"
      }
    }
  ]
}
```
Sample response
```
{
  "took": 1,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 2,
      "relation": "eq"
    },
    "max_score": null,
    "hits": [
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "dDo-VJwBZTQZft5F3lfo",
        "_version": 1,
        "_seq_no": 125,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939932392,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:45:32.391722775Z",
            "created_at": "2026-02-12T23:45:32.391722775Z",
            "message_id": 3,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "It’s a small screenshot showing the text “Hello World!!” in a monospaced font on a dark background."
                }
              ]
            }
          },
          "last_updated_time": 1770939932392,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_20001"
          },
          "message_id": 3,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          3
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "bjo-VJwBZTQZft5F0lfJ",
        "_version": 1,
        "_seq_no": 124,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939929289,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:45:29.288505962Z",
            "created_at": "2026-02-12T23:45:29.288505962Z",
            "message_id": 2,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "What's in this image?"
                },
                {
                  "image": {
                    "format": "png",
                    "source": {
                      "bytes": {
                        "data": "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",
                        "__bytes_encoded__": true
                      }
                    }
                  }
                }
              ]
            }
          },
          "last_updated_time": 1770939929289,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_20001"
          },
          "message_id": 2,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          2
        ]
      }
    ]
  }
}
```
### Messages input
#### Run agent
```
POST _plugins/_ml/agents/{{unified_agent_openai}}/_execute
{
  "parameters": {
    "session_id": "session_30001"
  },
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "I like the color blue"
        }
      ]
    },
    {
      "role": "assistant",
      "content": [
        {
          "type": "text",
          "text": "That's great! Blue is a beautiful color."
        }
      ]
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "What color did I say I like?"
        }
      ]
    }
  ]
}
```
Sample response
```
{
  "stop_reason": "stop",
  "message": {
    "role": "assistant",
    "content": [
      {
        "text": "You said you like blue."
      }
    ]
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 532,
      "outputTokens": 143,
      "totalTokens": 675
    }
  }
}
```
#### Get memory
```
GET _plugins/_ml/memory_containers/{{unified_agent_mem_container_openai}}/memories/working/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "namespace.session_id": "session_30001"
          }
        }
      ]
    }
  },
  "sort": [
    {
      "message_id": {
        "order": "desc"
      }
    }
  ]
}
```
Sample response
```
{
  "took": 1,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": null,
    "hits": [
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "-zo_VJwBZTQZft5Ft1gI",
        "_version": 1,
        "_seq_no": 131,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939987720,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:46:27.719431768Z",
            "created_at": "2026-02-12T23:46:27.719431768Z",
            "message_id": 7,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "You said you like blue."
                }
              ]
            }
          },
          "last_updated_time": 1770939987720,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 7,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          7
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "9jo_VJwBZTQZft5FrFiL",
        "_version": 1,
        "_seq_no": 130,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939985035,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:46:25.034788499Z",
            "created_at": "2026-02-12T23:46:25.034788499Z",
            "message_id": 6,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "What color did I say I like?"
                }
              ]
            }
          },
          "last_updated_time": 1770939985035,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 6,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          6
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "9To_VJwBZTQZft5FrFiL",
        "_version": 1,
        "_seq_no": 129,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939985035,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:46:25.034609150Z",
            "created_at": "2026-02-12T23:46:25.034609150Z",
            "message_id": 5,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "That's great! Blue is a beautiful color."
                }
              ]
            }
          },
          "last_updated_time": 1770939985035,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 5,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          5
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "9Do_VJwBZTQZft5FrFiK",
        "_version": 1,
        "_seq_no": 128,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770939985034,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:46:25.034313627Z",
            "created_at": "2026-02-12T23:46:25.034313627Z",
            "message_id": 4,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "I like the color blue"
                }
              ]
            }
          },
          "last_updated_time": 1770939985034,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 4,
          "memory_container_id": "bjoyVJwBZTQZft5FDjfv"
        },
        "sort": [
          4
        ]
      }
    ]
  }
}
```