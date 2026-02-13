# Topic

This tutorial shows how to use the unified agent interface with Bedrock converse API. 
We will use Claude 4.6 in this tutorial.

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
  "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7",
  "status": "created"
}
```

## 2. Create Agent
```
POST _plugins/_ml/agents/_register
{
  "name": "Claude Sonnet Multi-modal Agent",
  "type": "conversational_v2",
  "description": "V2 agent with full multi-modal support",
  "model": {
    "model_id": "global.anthropic.claude-opus-4-6-v1",
    "model_provider": "bedrock/converse",
    "credential": {
      "access_key": "{{access_key}}",
      "secret_key": "{{secret_key}}",
      "session_token": "{{session_token}}"
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
    "memory_container_id": "{{unified_agent_mem_container}}"
  },
  "parameters": {
    "system_prompt": "You are a helpful AI assistant with access to document search capabilities."
  }
}
```
Sample response
```
{
  "agent_id": "qjc2U5wBZTQZft5Fnqq1"
}
```

### Get agent
```
POST _plugins/_ml/agents/{{unified_agent}}
```
Sample response
```
{
  "name": "Claude Sonnet Multi-modal Agent",
  "type": "conversational_v2",
  "description": "V2 agent with full multi-modal support",
  "llm": {
    "model_id": "qTc2U5wBZTQZft5Fnqqd"
  },
  "model": {
    "model_id": "global.anthropic.claude-opus-4-6-v1",
    "model_provider": "bedrock/converse"
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
    "model_provider": "bedrock/converse",
    "system_prompt": "You are a helpful AI assistant with access to document search capabilities."
  },
  "memory": {
    "type": "agentic_memory",
    "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
  },
  "created_time": 1770922614452,
  "last_updated_time": 1770922614452,
  "is_hidden": false
}
```
### Get model

```
GET _plugins/_ml/models/qTc2U5wBZTQZft5Fnqqd
```
```json
{
  "name": "Auto-generated model for global.anthropic.claude-opus-4-6-v1",
  "model_group_id": "pzc2U5wBZTQZft5FnqpR",
  "algorithm": "REMOTE",
  "model_version": "1",
  "description": "Auto-generated model for agent",
  "model_state": "REGISTERED",
  "created_time": 1770922614406,
  "last_updated_time": 1770922614406,
  "is_hidden": false,
  "connector": {
    "name": "Auto-generated Bedrock Converse connector for Agent",
    "version": "1",
    "description": "Auto-generated connector for Bedrock Converse API",
    "protocol": "aws_sigv4",
    "parameters": {
      "service_name": "bedrock",
      "model": "global.anthropic.claude-opus-4-6-v1",
      "region": "us-east-1"
    },
    "actions": [
      {
        "action_type": "PREDICT",
        "method": "POST",
        "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
        "headers": {
          "content-type": "application/json"
        },
        "request_body": "{\"system\": [{\"text\": \"${parameters.system_prompt}\"}], \"messages\": [${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]${parameters.tool_configs:-} }"
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
POST _plugins/_ml/models/qTc2U5wBZTQZft5Fnqqd/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant",
    "body": {
      "role": "user",
      "content": [
        {
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
            "metrics": {
              "latencyMs": 1638
            },
            "output": {
              "message": {
                "content": [
                  {
                    "text": "My name is Claude. I'm an AI assistant made by Anthropic. How can I help you today?"
                  }
                ],
                "role": "assistant"
              }
            },
            "stopReason": "end_turn",
            "usage": {
              "cacheReadInputTokenCount": 0,
              "cacheReadInputTokens": 0,
              "cacheWriteInputTokenCount": 0,
              "cacheWriteInputTokens": 0,
              "inputTokens": 18,
              "outputTokens": 26,
              "serverToolUsage": {},
              "totalTokens": 44
            }
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
POST /_plugins/_ml/agents/{{unified_agent}}/_execute
{
  "parameters": {
    "session_id": "session_10001"
  },
  "input": "What is the capital of France?"
}
```

Sample response
```
{
  "stop_reason": "end_turn",
  "message": {
    "content": [
      {
        "text": "The capital of France is **Paris**. It is the largest city in France and serves as the country's political, economic, and cultural center. Paris is renowned for its iconic landmarks such as the Eiffel Tower, the Louvre Museum, and Notre-Dame Cathedral."
      }
    ],
    "role": "assistant"
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 1307,
      "outputTokens": 61,
      "totalTokens": 1368
    }
  }
}
```
Continue a session
```
POST /_plugins/_ml/agents/{{unified_agent}}/_execute
{
  "parameters": {
    "session_id": "session_10001"
  },
  "input": "Translate last answer to Chinese"
}
```

Run multiple tools
```
POST _plugins/_ml/agents/{{unified_agent}}/_execute
{
  "parameters": {
    "session_id": "session_10004_9"
  },
  "input": "How many indices in my opensearch cluster? Get 2 documents from opensearch_dashboards_sample_data_flights index. If possible, let's run multile tools in parrallel to reduce latency"
}
```
#### Get memory

```
GET _plugins/_ml/memory_containers/{{unified_agent_mem_container}}/memories/working/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "namespace.session_id": "session_10004_9"
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
        "_id": "Sjh5U5wBZTQZft5FIYz4",
        "_version": 1,
        "_seq_no": 72,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770926973432,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T20:09:33.431220070Z",
            "created_at": "2026-02-12T20:09:33.431220070Z",
            "message_id": 3,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "Here are the results from both queries:\n\n---\n\n### 📊 Total Indices in Your Cluster: **23 indices**\n\nHere's a quick breakdown:\n- **Green health**: 20 indices\n- **Yellow health**: 3 indices (`jaeger-service-*`, `jaeger-span-*`, `ss4o_logs-*`, `.kibana`)\n\n---\n\n### ✈️ 2 Documents from `opensearch_dashboards_sample_data_flights`\n\n**Document 1:**\n| Field | Value |\n|-------|-------|\n| **FlightNum** | 9HY9SWR |\n| **Origin** | Frankfurt am Main Airport (Germany) |\n| **Destination** | Sydney Kingsford Smith International Airport (Australia) |\n| **Carrier** | OpenSearch Dashboards Airlines |\n| **Avg Ticket Price** | $841.27 |\n| **Distance** | 16,492 km (10,248 miles) |\n| **Flight Time** | ~17.2 hours |\n| **Delayed** | No |\n| **Cancelled** | No |\n| **Weather (Origin → Dest)** | Sunny → Rain |\n\n**Document 2:**\n| Field | Value |\n|-------|-------|\n| **FlightNum** | X98CCZO |\n| **Origin** | Cape Town International Airport (South Africa) |\n| **Destination** | Venice Marco Polo Airport (Italy) |\n| **Carrier** | Logstash Airways |\n| **Avg Ticket Price** | $882.98 |\n| **Distance** | 8,823 km (5,483 miles) |\n| **Flight Time** | ~7.7 hours |\n| **Delayed** | No |\n| **Cancelled** | No |\n| **Weather (Origin → Dest)** | Clear → Sunny |\n\nBoth calls were executed in parallel to minimize latency! 🚀"
                }
              ]
            }
          },
          "last_updated_time": 1770926973432,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_10004_9"
          },
          "message_id": 3,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          3
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "GDh5U5wBZTQZft5FCIws",
        "_version": 1,
        "_seq_no": 71,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770926966827,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T20:09:26.826941972Z",
            "created_at": "2026-02-12T20:09:26.826941972Z",
            "message_id": 2,
            "message": {
              "role": "user",
              "content": [
                {
                  "toolResult": {
                    "toolUseId": "tooluse_tpb5GiNJVFBIe6HaVsEdFi",
                    "content": [
                      {
                        "text": "row,health,status,index,uuid,pri(number of primary shards),rep(number of replica shards),docs.count(number of available documents),docs.deleted(number of deleted documents),store.size(store size of primary and replica shards),pri.store.size(store size of primary shards)\n1,green,open,security-auditlog-2026.02.12,fmTbKqvfTd2pdkbal43jhA,1,0,103,0,474.4kb,474.4kb\n2,green,open,.plugins-ml-model-group,6dOF1ckzS9GvKKHolnOjCQ,1,0,3,0,19.2kb,19.2kb\n3,green,open,.plugins-ml-am-default-memory-sessions,-cL_1JWCR2C2l8ukal-FoQ,1,0,18,0,43.7kb,43.7kb\n4,yellow,open,jaeger-service-2026-02-12,hAie7F18Tyiuimavd3HWaQ,1,1,84,4,21.5kb,21.5kb\n5,green,open,.ql-datasources,-msUsnG0Q7qIKUkeble2mA,1,0,0,0,208b,208b\n6,green,open,.plugins-ml-am-memory-container,6NYvE7MuSr-CPwmq73ygBw,1,0,6,0,28.4kb,28.4kb\n7,green,open,.kibana_92668751_admin_1,LNt0chyUQj-Ka8RVPnRYBg,1,0,37,0,125.1kb,125.1kb\n8,green,open,opensearch_dashboards_sample_data_otel_spans,YxkxAAVzT7aGbolwLdyw6w,1,0,13061,0,6.1mb,6.1mb\n9,green,open,.plugins-ml-agent,JMWCxJpTQJqV7biGvhSlDw,1,0,3,0,45kb,45kb\n10,yellow,open,ss4o_logs-otel-2026.02.12,rjHy3SZ2S6GImMnGE43P4w,1,1,8193,0,15.7mb,15.7mb\n11,green,open,.plugins-ml-task,l5GlUKbWQsSmjpi7m32NNQ,1,0,11,0,40.7kb,40.7kb\n12,green,open,top_queries-2026.02.12-95553,bAsuVPnWSQSBstaCtQupHA,1,0,42,0,56kb,56kb\n13,yellow,open,jaeger-span-2026-02-12,tRtwIvirS_27gyi-4JkGfg,1,1,20585,0,28.5mb,28.5mb\n14,green,open,.opendistro_security,PI3tcT8rRxiW6Nz9yN2S3Q,1,0,9,0,81.5kb,81.5kb\n15,green,open,.plugins-ml-config,JJ3QVcxEQ5KQCsTOpOnePw,1,0,1,0,4kb,4kb\n16,green,open,.plugins-ml-am-default-memory-working,Xy9Q2J-PSv6D1t3LckNCKg,1,0,70,0,252.7kb,252.7kb\n17,green,open,opensearch_dashboards_sample_data_otel_service_map,cnVvxHmyR--aFSvckE59EA,1,0,49,0,17.6kb,17.6kb\n18,yellow,open,.kibana,i9QuobHjR-C9fBXyhR_beA,1,1,3,0,9.6kb,9.6kb\n19,green,open,.plugins-ml-model,5YxlSmipRxinygGY-2sjyg,1,0,3,0,121.2kb,121.2kb\n20,green,open,opensearch_dashboards_sample_data_otel_logs,QUQCxMOYRG-J0HKyAry5-A,1,0,16286,0,5.6mb,5.6mb\n21,green,open,ss4o_metrics-otel-opensearch_dashboards-sample,gal8GAaFSG6XqRhsjpI-bg,1,0,39923,0,4.3mb,4.3mb\n22,green,open,opensearch_dashboards_sample_data_flights,9a52k5WnQgyoXlgNAqKcFQ,1,0,13059,0,5.7mb,5.7mb\n23,green,open,opensearch_dashboards_sample_data_ecommerce,Cpgx3vHgRB64AI66KDOgCg,1,0,4675,0,4mb,4mb\n"
                      }
                    ],
                    "status": "success"
                  }
                },
                {
                  "toolResult": {
                    "toolUseId": "tooluse_7odNizAEaGb0OB5kCuzAFT",
                    "content": [
                      {
                        "text": "{\"_index\":\"opensearch_dashboards_sample_data_flights\",\"_source\":{\"FlightNum\":\"9HY9SWR\",\"Origin\":\"Frankfurt am Main Airport\",\"OriginLocation\":{\"lon\":\"8.570556\",\"lat\":\"50.033333\"},\"DestLocation\":{\"lon\":\"151.177002\",\"lat\":\"-33.94609833\"},\"FlightDelay\":false,\"DistanceMiles\":10247.856675613455,\"FlightTimeMin\":1030.7704158599038,\"OriginWeather\":\"Sunny\",\"dayOfWeek\":0,\"AvgTicketPrice\":841.2656419677076,\"Carrier\":\"OpenSearch Dashboards Airlines\",\"FlightDelayMin\":0,\"OriginRegion\":\"DE-HE\",\"FlightDelayType\":\"No Delay\",\"DestAirportID\":\"SYD\",\"timestamp\":\"2026-02-02T00:00:00\",\"Dest\":\"Sydney Kingsford Smith International Airport\",\"FlightTimeHour\":17.179506930998397,\"Cancelled\":false,\"DistanceKilometers\":16492.32665375846,\"OriginCityName\":\"Frankfurt am Main\",\"DestWeather\":\"Rain\",\"OriginCountry\":\"DE\",\"DestCountry\":\"AU\",\"DestRegion\":\"SE-BD\",\"OriginAirportID\":\"FRA\",\"DestCityName\":\"Sydney\"},\"_id\":\"qszpUJwBBhekawnm440r\",\"_score\":1.0}\n{\"_index\":\"opensearch_dashboards_sample_data_flights\",\"_source\":{\"FlightNum\":\"X98CCZO\",\"Origin\":\"Cape Town International Airport\",\"OriginLocation\":{\"lon\":\"18.60169983\",\"lat\":\"-33.96480179\"},\"DestLocation\":{\"lon\":\"12.3519\",\"lat\":\"45.505299\"},\"FlightDelay\":false,\"DistanceMiles\":5482.606664853586,\"FlightTimeMin\":464.3894810759016,\"OriginWeather\":\"Clear\",\"dayOfWeek\":0,\"AvgTicketPrice\":882.9826615595518,\"Carrier\":\"Logstash Airways\",\"FlightDelayMin\":0,\"OriginRegion\":\"SE-BD\",\"FlightDelayType\":\"No Delay\",\"DestAirportID\":\"VE05\",\"timestamp\":\"2026-02-02T18:27:00\",\"Dest\":\"Venice Marco Polo Airport\",\"FlightTimeHour\":7.73982468459836,\"Cancelled\":false,\"DistanceKilometers\":8823.40014044213,\"OriginCityName\":\"Cape Town\",\"DestWeather\":\"Sunny\",\"OriginCountry\":\"ZA\",\"DestCountry\":\"IT\",\"DestRegion\":\"IT-34\",\"OriginAirportID\":\"CPT\",\"DestCityName\":\"Venice\"},\"_id\":\"q8zpUJwBBhekawnm440r\",\"_score\":1.0}\n"
                      }
                    ],
                    "status": "success"
                  }
                }
              ]
            }
          },
          "last_updated_time": 1770926966827,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_10004_9"
          },
          "message_id": 2,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          2
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "Fzh5U5wBZTQZft5FCIwg",
        "_version": 1,
        "_seq_no": 70,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770926966816,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T20:09:26.815624549Z",
            "created_at": "2026-02-12T20:09:26.815624549Z",
            "message_id": 1,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "\n\nI'll help you with that! Let me run both queries in parallel to save time."
                },
                {
                  "toolUse": {
                    "input": {
                      "indices": []
                    },
                    "name": "ListIndexTool",
                    "type": "tool_use",
                    "toolUseId": "tooluse_tpb5GiNJVFBIe6HaVsEdFi"
                  }
                },
                {
                  "toolUse": {
                    "input": {
                      "query": {
                        "size": 2,
                        "query": {
                          "match_all": {}
                        }
                      },
                      "index": "opensearch_dashboards_sample_data_flights"
                    },
                    "name": "SearchIndexTool",
                    "type": "tool_use",
                    "toolUseId": "tooluse_7odNizAEaGb0OB5kCuzAFT"
                  }
                }
              ]
            }
          },
          "last_updated_time": 1770926966816,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_10004_9"
          },
          "message_id": 1,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          1
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "Fjh4U5wBZTQZft5F-4xD",
        "_version": 1,
        "_seq_no": 69,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770926963523,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T20:09:23.522664746Z",
            "created_at": "2026-02-12T20:09:23.522664746Z",
            "message_id": 0,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "How many indices in my opensearch cluster? Get 2 documents from opensearch_dashboards_sample_data_flights index. If possible, let's run multile tools in parrallel to reduce latency"
                }
              ]
            }
          },
          "last_updated_time": 1770926963523,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_10004_9"
          },
          "message_id": 0,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
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
POST _plugins/_ml/agents/{{unified_agent}}/_execute
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
  "stop_reason": "end_turn",
  "message": {
    "content": [
      {
        "text": "The image shows the text **\"Hello World!!\"** displayed in a light/white colored monospaced font on a dark/black background. This appears to be a screenshot of a terminal, console, or code output — a classic programming example often used as the first program when learning a new programming language."
      }
    ],
    "role": "assistant"
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 1333,
      "outputTokens": 66,
      "totalTokens": 1399
    }
  }
}
```
#### Get memory
```
GET _plugins/_ml/memory_containers/{{unified_agent_mem_container}}/memories/working/_search
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
        "_id": "uDorVJwBZTQZft5FBCis",
        "_version": 1,
        "_seq_no": 93,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770938631340,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:23:51.339388429Z",
            "created_at": "2026-02-12T23:23:51.339388429Z",
            "message_id": 1,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "The image contains the text **\"Hello World!!\"** displayed in white text on a dark/black background. It appears to be a simple text output, commonly associated with a terminal/console window or a basic programming example — the classic \"Hello World\" program that is often the first program written when learning a new programming language."
                }
              ]
            }
          },
          "last_updated_time": 1770938631340,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_20001"
          },
          "message_id": 1,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          1
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "kjoqVJwBZTQZft5F9SjB",
        "_version": 1,
        "_seq_no": 92,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770938627520,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:23:47.520344623Z",
            "created_at": "2026-02-12T23:23:47.520344623Z",
            "message_id": 0,
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
          "last_updated_time": 1770938627520,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_20001"
          },
          "message_id": 0,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          0
        ]
      }
    ]
  }
}
```
### Messages input
#### Run agent
```
POST _plugins/_ml/agents/{{unified_agent}}/_execute
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
  "stop_reason": "end_turn",
  "message": {
    "content": [
      {
        "text": "You said you like the color **blue**! 💙"
      }
    ],
    "role": "assistant"
  },
  "metrics": {
    "accumulated_usage": {
      "inputTokens": 1316,
      "outputTokens": 16,
      "totalTokens": 1332
    }
  }
}
```
#### Get memory
```
GET _plugins/_ml/memory_containers/{{unified_agent_mem_container}}/memories/working/_search
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
        "_id": "xDobVJwBZTQZft5FgwLx",
        "_version": 1,
        "_seq_no": 90,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770937615345,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:06:55.344498283Z",
            "created_at": "2026-02-12T23:06:55.344498283Z",
            "message_id": 3,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "You said you like the color **blue**! 💙"
                }
              ]
            }
          },
          "last_updated_time": 1770937615345,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 3,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          3
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "wTobVJwBZTQZft5FfQJ1",
        "_version": 1,
        "_seq_no": 89,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770937613685,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:06:53.684828030Z",
            "created_at": "2026-02-12T23:06:53.684828030Z",
            "message_id": 2,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "What color did I say I like?"
                }
              ]
            }
          },
          "last_updated_time": 1770937613685,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 2,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          2
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "wDobVJwBZTQZft5FfQJ1",
        "_version": 1,
        "_seq_no": 88,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770937613685,
          "metadata": {
            "role": "assistant",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:06:53.684614936Z",
            "created_at": "2026-02-12T23:06:53.684614936Z",
            "message_id": 1,
            "message": {
              "role": "assistant",
              "content": [
                {
                  "text": "That's great! Blue is a beautiful color."
                }
              ]
            }
          },
          "last_updated_time": 1770937613685,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 1,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          1
        ]
      },
      {
        "_index": ".plugins-ml-am-default-memory-working",
        "_id": "vzobVJwBZTQZft5FfQJ0",
        "_version": 1,
        "_seq_no": 87,
        "_primary_term": 3,
        "_score": null,
        "_source": {
          "payload_type": "conversational",
          "created_time": 1770937613684,
          "metadata": {
            "role": "user",
            "format": "strands",
            "type": "message"
          },
          "structured_data_blob": {
            "updated_at": "2026-02-12T23:06:53.684283816Z",
            "created_at": "2026-02-12T23:06:53.684283816Z",
            "message_id": 0,
            "message": {
              "role": "user",
              "content": [
                {
                  "text": "I like the color blue"
                }
              ]
            }
          },
          "last_updated_time": 1770937613684,
          "infer": false,
          "namespace_size": 1,
          "owner_id": "admin",
          "namespace": {
            "session_id": "session_30001"
          },
          "message_id": 0,
          "memory_container_id": "Vzc2U5wBZTQZft5Ffoc7"
        },
        "sort": [
          0
        ]
      }
    ]
  }
}
```