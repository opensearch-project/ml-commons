# Agentic RAG with Bedrock OpenAI OSS Tutorial

## Overview

This tutorial demonstrates how to build an intelligent Retrieval Augmented Generation (RAG) system using OpenSearch's agent framework with Bedrock's OpenAI GPT OSS model. The system combines the power of large language models with OpenSearch's search capabilities to create conversational agents that can intelligently query and retrieve information from your data indices.

### What You'll Build

- **LLM Integration**: Set up Bedrock OpenAI GPT OSS 120b model with tool usage capabilities
- **Embedding Model**: Configure Bedrock Titan Embedding Model V2 for semantic search
- **Flow Agent**: Create a specialized agent that translates natural language questions into OpenSearch Query DSL
- **Chat Agent**: Build a conversational RAG agent that can search across multiple indices and provide contextual responses

### Prerequisites

- OpenSearch cluster with ML plugin enabled
- AWS Bedrock access with appropriate permissions
- Basic understanding of OpenSearch Query DSL
- Sample data indices (the tutorial uses OpenSearch Dashboards sample flight data)

---

# 1. Create Model

## 1.1 LLM

### 1.1.1 Create LLM

- `reasoning_effort`: "low", "medium", "high"

```
POST _plugins/_ml/models/_register
{
  "name": "Bedrock OpenAI GPT OSS 120b",
  "function_name": "remote",
  "description": "test model",
  "connector": {
    "name": "Bedrock OpenAI GPT OSS connector",
    "description": "test connector",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "us-west-2",
      "service_name": "bedrock",
      "model": "openai.gpt-oss-120b-1:0",
      "return_data_as_map": true,
      "reasoning_effort": "high",
      "output_processors": [
        {
          "type": "conditional",
          "path": "$.output.message.content[*].toolUse",
          "routes": [
            {
              "exists": [
                {
                  "type": "regex_replace",
                  "pattern": "\"stopReason\"\\s*:\\s*\"end_turn\"",
                  "replacement": "\"stopReason\": \"tool_use\""
                }
              ]
            },
            {
              "not_exists": [
                {
                  "type": "regex_replace",
                  "pattern": "<reasoning>.*?</reasoning>",
                  "replacement": ""
                }
              ]
            }
          ]
        },
        {
          "type": "remove_jsonpath",
          "path": "$.output.message.content[0]"
        }
      ]
    },
    "credential": {
      "access_key": "xxx",
      "secret_key": "xxx"
    },
    "actions": [
      {
        "action_type": "predict",
        "method": "POST",
        "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
        "headers": {
          "content-type": "application/json"
        },
        "request_body": "{ \"additionalModelRequestFields\": {\"reasoning_effort\": \"${parameters.reasoning_effort}\"}, \"system\": [{\"text\": \"${parameters.system_prompt}\"}], \"messages\": [${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.prompt}\"}]}${parameters._interactions:-}]${parameters.tool_configs:-} }"
      }
    ],
    "client_config": {
      "max_retry_times": 5,
      "retry_backoff_policy": "exponential_equal_jitter",
      "retry_backoff_millis": 5000
    }
  },
  "interface": {}
}
```

Sample output
```
{
  "task_id": "aPArmJgBCqG4iVqlioAh",
  "status": "CREATED",
  "model_id": "afArmJgBCqG4iVqlioA9"
}
```

### 1.1.2 Test Tool Usage

```
POST _plugins/_ml/models/afArmJgBCqG4iVqlioA9/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant.",
    "prompt": "What's the weather in Seattle and Beijing?",
    "tool_configs": ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}",
    "_tools": "{\"toolSpec\":{\"name\":\"getWeather\",\"description\":\"Get the current weather for a location\",\"inputSchema\":{\"json\":{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\",\"description\":\"City name, e.g. Seattle, WA\"}},\"required\":[\"location\"]}}}}",
    "no_escape_params": "tool_configs,_tools"
  }
}
```
Sample output
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "metrics": {
              "latencyMs": 10011.0
            },
            "output": {
              "message": {
                "content": [
                  {
                    "text": ""
                  },
                  {
                    "toolUse": {
                      "input": {
                        "location": "Seattle"
                      },
                      "name": "getWeather",
                      "toolUseId": "tooluse_t-ICDhbRQUyB3HQsFriRcw"
                    }
                  }
                ],
                "role": "assistant"
              }
            },
            "stopReason": "tool_use",
            "usage": {
              "inputTokens": 28.0,
              "outputTokens": 36.0,
              "totalTokens": 64.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

Test example 2
```
POST /_plugins/_ml/models/afArmJgBCqG4iVqlioA9/_predict
{
  "parameters": {
    "system_prompt": "You are an expert RAG (Retrieval Augmented Generation) assistant with access to OpenSearch indices. Your primary purpose is to help users find and understand information by retrieving relevant content from the OpenSearch indices.\n\nSEARCH GUIDELINES:\n1. When a user asks for information:\n   - First determine which index to search (use ListIndexTool if unsure)\n   - For complex questions, break them down into smaller sub-questions\n   - Be specific and targeted in your search queries\n\n\nRESPONSE GUIDELINES:\n1. Always cite which index the information came from\n2. Synthesize search results into coherent, well-structured responses\n3. Use formatting (headers, bullet points) to organize information when appropriate\n4. If search results are insufficient, acknowledge limitations and suggest alternatives\n5. Maintain context from the conversation history when formulating responses\n\nPrioritize accuracy over speculation. Be professional, helpful, and concise in your interactions.",
    "prompt": "How many flights from China to USA",
    "tool_configs": ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}",
    "_tools": "{\"toolSpec\":{\"name\":\"getWeather\",\"description\":\"Get the current weather for a location\",\"inputSchema\":{\"json\":{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\",\"description\":\"City name, e.g. Seattle, WA\"}},\"required\":[\"location\"]}}}}",
    "no_escape_params": "tool_configs,_tools, _interactions",
    "_interactions": ", {\"content\":[{\"text\":\"\\u003creasoning\\u003eThe user asks: \\\"How many flights from China to USA\\\". They want a number. Likely they need data from an index that tracks flight data. We need to search relevant index. Not sure which index exists. Let\\u0027s list indices.\\u003c/reasoning\\u003e\"},{\"toolUse\":{\"input\":{\"indices\":[]},\"name\":\"ListIndexTool\",\"toolUseId\":\"tooluse_1dXChRccQnap_Yf5Icf2vw\"}}],\"role\":\"assistant\"},{\"role\":\"user\",\"content\":[{\"toolResult\":{\"toolUseId\":\"tooluse_1dXChRccQnap_Yf5Icf2vw\",\"content\":[{\"text\":\"row,health,status,index,uuid,pri(number of primary shards),rep(number of replica shards),docs.count(number of available documents),docs.deleted(number of deleted documents),store.size(store size of primary and replica shards),pri.store.size(store size of primary shards)\\n1,green,open,.plugins-ml-model-group,HU6XuHpjRp6mPe4GYn0qqQ,1,0,4,0,11.8kb,11.8kb\\n2,green,open,otel-v1-apm-service-map-sample,KYlj9cclQXCFWKTsNGV3Og,1,0,49,0,17.7kb,17.7kb\\n3,green,open,.plugins-ml-memory-meta,CCnEW7ixS_KrJdmEl330lw,1,0,546,29,209.2kb,209.2kb\\n4,green,open,.ql-datasources,z4aXBn1oQoSXNiCMvwapkw,1,0,0,0,208b,208b\\n5,green,open,security-auditlog-2025.08.11,IwYKN08KTJqGnIJ5tVURzA,1,0,30,0,270.3kb,270.3kb\\n6,yellow,open,test_tech_news,2eA8yCDIRcaKoUhDwnD4uQ,1,1,3,0,23.1kb,23.1kb\\n7,yellow,open,test_mem_container,VS57K2JvQmyXlJxDwJ9nhA,1,1,0,0,208b,208b\\n8,green,open,.plugins-ml-task,GTX8717YRtmw0nkl1vnXMg,1,0,691,28,107.6kb,107.6kb\\n9,green,open,ss4o_metrics-otel-sample,q5W0oUlZRuaEVtfL1dknKA,1,0,39923,0,4.3mb,4.3mb\\n10,green,open,.opendistro_security,3AmSFRp1R42BSFMPfXXVqw,1,0,9,0,83.7kb,83.7kb\\n11,yellow,open,test_population_data,fkgI4WqQSv2qHSaXRJv_-A,1,1,4,0,22.5kb,22.5kb\\n12,green,open,.plugins-ml-model,Ksvz5MEwR42RmrOZAg6RTQ,1,0,18,31,406.8kb,406.8kb\\n13,green,open,.plugins-ml-memory-container,TYUOuxfMT5i4DdOMfYe45w,1,0,2,0,9.9kb,9.9kb\\n14,green,open,.kibana_-969161597_admin123_1,dlCY0iSQR9aTtRJsJx9cIg,1,0,1,0,5.3kb,5.3kb\\n15,green,open,opensearch_dashboards_sample_data_ecommerce,HdLqBN2QQCC4nUhE-5mOkA,1,0,4675,0,4.1mb,4.1mb\\n16,green,open,security-auditlog-2025.08.04,Ac34ImxkQSeonGeW8RSYcw,1,0,985,0,1mb,1mb\\n17,green,open,.plugins-ml-memory-message,osbPSzqeQd2sCPF2yqYMeg,1,0,2489,11,4mb,4mb\\n18,green,open,security-auditlog-2025.08.05,hBi-5geLRqml4mDixXMPYQ,1,0,854,0,1.1mb,1.1mb\\n19,green,open,security-auditlog-2025.08.07,bY2GJ1Z8RAG7qJb8dcHktw,1,0,5,0,75.8kb,75.8kb\\n20,green,open,otel-v1-apm-span-sample,mewMjWmHSdOlGNzgz-OnXg,1,0,13061,0,6.2mb,6.2mb\\n21,green,open,security-auditlog-2025.08.01,lvwEW0_VQnq88o7XjJkoPw,1,0,57,0,216.9kb,216.9kb\\n22,green,open,.kibana_92668751_admin_1,w45HvTqcTQ-eVTWK77lx7A,1,0,242,0,125.2kb,125.2kb\\n23,green,open,.plugins-ml-agent,Q-kWqw9_RdW3SfhUcKBcfQ,1,0,156,0,423.1kb,423.1kb\\n24,green,open,security-auditlog-2025.08.03,o_iRgiWZRryXrhQ-y36JQg,1,0,847,0,575.9kb,575.9kb\\n25,green,open,.kibana_1,tn9JwuiSSe6gR9sInHFxDg,1,0,4,0,16.7kb,16.7kb\\n26,green,open,security-auditlog-2025.08.09,uugjBoB_SR67h4455K5KPw,1,0,58,0,161.9kb,161.9kb\\n27,green,open,ss4o_logs-otel-sample,wIaGQx8wT1ijhxDjjbux6A,1,0,16286,0,5.6mb,5.6mb\\n28,green,open,.plugins-ml-config,uh1sYBiTRYy_lK61Rx0fkQ,1,0,1,0,4kb,4kb\\n29,green,open,opensearch_dashboards_sample_data_logs,jDDgZ_PfTkKHfaDqBl72lQ,1,0,14074,0,8.3mb,8.3mb\\n30,green,open,opensearch_dashboards_sample_data_flights,7dcplp7MRc2C9WTPyLRRIg,1,0,13059,0,5.8mb,5.8mb\\n\"}]}}]}"
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
              "latencyMs": 37441.0
            },
            "output": {
              "message": {
                "content": [
                  {
                    "text": ""
                  },
                  {
                    "toolUse": {
                      "input": {
                        "index": "opensearch_dashboards_sample_data_flights",
                        "query": {
                          "bool": {
                            "must": [
                              {
                                "match_phrase": {
                                  "Origin": "China"
                                }
                              },
                              {
                                "match_phrase": {
                                  "Destination": "United States"
                                }
                              }
                            ]
                          }
                        },
                        "size": 0.0,
                        "track_total_hits": true
                      },
                      "name": "SearchTool",
                      "toolUseId": "tooluse_S19DlVesT3SAZ96-TmEWkA"
                    }
                  }
                ],
                "role": "assistant"
              }
            },
            "stopReason": "tool_use",
            "usage": {
              "inputTokens": 1030.0,
              "outputTokens": 147.0,
              "totalTokens": 1177.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### 1.1.3 Test final resposne
```
POST _plugins/_ml/models/afArmJgBCqG4iVqlioA9/_predict
{
  "parameters": {
    "system_prompt": "You are a helpful assistant.",
    "prompt": "What's the capital of USA?"
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
              "latencyMs": 797.0
            },
            "output": {
              "message": {
                "content": [
                  {
                    "text": "The capital of the United States of America is **Washington, D.C.**."
                  }
                ],
                "role": "assistant"
              }
            },
            "stopReason": "end_turn",
            "usage": {
              "inputTokens": 24.0,
              "outputTokens": 46.0,
              "totalTokens": 70.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### 1.1.1 Test Tool Usage

## 1.2 Embedding Model

### 1.2.1 Create Embedding Model
```
{
  "name": "Bedrock embedding model",
  "function_name": "remote",
  "description": "Bedrock Titan Embedding Model V2",
  "connector": {
    "name": "Amazon Bedrock Connector: embedding",
    "description": "The connector to bedrock Titan embedding model",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "your_aws_region",
      "service_name": "bedrock",
      "model": "amazon.titan-embed-text-v2:0",
      "dimensions": 1024,
      "normalize": true,
      "embeddingTypes": [
        "float"
      ]
    },
    "credential": {
      "access_key": "xxx",
      "secret_key": "xxx"
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
Sample response
```
{
  "task_id": "WfAemJgBCqG4iVqlaID3",
  "status": "CREATED",
  "model_id": "WvAemJgBCqG4iVqlaYAS"
}
```

### 1.2.2 Test Embedding Model
```
POST _plugins/_ml/models/WvAemJgBCqG4iVqlaYAS/_predict?algorithm=text_embedding
{
  "text_docs": [
    "hello", 
    "how are you"
  ]
}
```
or
```
POST _plugins/_ml/models/WvAemJgBCqG4iVqlaYAS/_predict
{
  "parameters": {
    "inputText": "how are you"
  }
}
```

# 2. Agent
## 2.1 Flow Agent 

### 2.1.1 Create Flow Agent
```
POST /_plugins/_ml/agents/_register
{
  "name": "Query DSL Translator Agent",
  "type": "flow",
  "description": "This is a demo agent for translating NLQ to OpenSearch DSL",
  "tools": [
    {
      "type": "IndexMappingTool",
      "include_output_in_agent_response": false,
      "parameters": {
        "input": "{\"index\": \"${parameters.index_name}\"}"
      }
    },
    {
      "type": "SearchIndexTool",
      "parameters": {
        "input": "{\"index\": \"${parameters.index_name}\", \"query\": ${parameters.query_dsl} }",
        "query_dsl": "{\"size\":2,\"query\":{\"match_all\":{}}}"
      },
      "include_output_in_agent_response": false
    },
    {
      "type": "MLModelTool",
      "name": "generate_os_query_dsl",
      "description": "A tool to generate OpenSearch query DSL with natrual language question.",
      "parameters": {
        "model_id": "afArmJgBCqG4iVqlioA9",
        "embedding_model_id": "WvAemJgBCqG4iVqlaYAS",
        "system_prompt": "You are an OpenSearch query generator that converts natural language questions into precise OpenSearch Query DSL JSON. Your ONLY response should be the valid JSON DSL query without any explanations or additional text.\n\nFollow these rules:\n1. Analyze the index mapping and sample document first, use the exact field name, DON'T use non-existing field name in generated query DSL\n2. Analyze the question to identify search criteria, filters, sorting, and result limits\n3. Extract specific parameters (fields, values, operators, size) mentioned in the question\n4. Apply the most appropriate query type (match, match_all, term, range, bool, etc.)\n5. Return ONLY the JSON query DSL with proper formatting.\n6. Please use standard two-letter ISO 3166-1 alpha-2 country codes (such as CN for China, US for United States, GB for United Kingdom) when build opensearch query.\n\nNEURAL SEARCH GUIDANCE:\n1. OpenSearch KNN index can be identified index settings with `\"knn\": \"true\",`; or in index mapping with any field with `\"type\": \"knn_vector\"`\n2. If search KNN index, prefer to use OpenSearch neural search query which is semantic based search, and has better accuracy.\n3. OpenSearch neural search needs embedding model id, please always use this model id \"${parameters.embedding_model_id}\"\n4. In KNN indices, embedding fields follow the pattern: {text_field_name}_embedding. For example, the raw text input is \"description\", then the generated embedding for this field will be saved into KNN field \"description_embedding\". \n5. Always exclude embedding fields from search results as they contain vector arrays that clutter responses\n6. Embedding fields can be identified in index mapping with \"type\": \"knn_vector\"\n7. OpenSearch neural search query will use embedding field (knn_vector type) and embedding model id. \n\nNEURAL SEARCH QUERY CONSTRUCTION:\nWhen constructing neural search queries, follow this pattern:\n{\n  \"_source\": {\n    \"excludes\": [\n      \"{field_name}_embedding\"\n    ]\n  },\n  \"query\": {\n    \"neural\": {\n      \"{field_name}_embedding\": {\n        \"query_text\": \"your query here\",\n        \"model_id\": \"${parameters.embedding_model_id}\"\n      }\n    }\n  }\n}\n\nRESPONSE GUIDELINES:\n1. Don't return the reasoning process, just return the generated OpenSearch query.\n2. Don't wrap the generated OpenSearch query with ```json and ```\n\nExamples:\n\nQuestion: retrieve 5 documents from index test_data\n{\"query\":{\"match_all\":{}},\"size\":5}\n\nQuestion: find documents where the field title contains machine learning\n{\"query\":{\"match\":{\"title\":\"machine learning\"}}}\n\nQuestion: search for documents with the phrase artificial intelligence in the content field and return top 10 results\n{\"query\":{\"match_phrase\":{\"content\":\"artificial intelligence\"}},\"size\":10}\n\nQuestion: get documents where price is greater than 100 and category is electronics\n{\"query\":{\"bool\":{\"must\":[{\"range\":{\"price\":{\"gt\":100}}},{\"term\":{\"category\":\"electronics\"}}]}}}\n\nQuestion: find the average rating of products in the electronics category\n{\"query\":{\"term\":{\"category\":\"electronics\"}},\"aggs\":{\"avg_rating\":{\"avg\":{\"field\":\"rating\"}}},\"size\":0}\n\nQuestion: return documents sorted by date in descending order, limit to 15 results\n{\"query\":{\"match_all\":{}},\"sort\":[{\"date\":{\"order\":\"desc\"}}],\"size\":15}\n\nQuestion: which book has the introduction of AWS AgentCore\n{\"_source\":{\"excludes\":[\"book_content_embedding\"]},\"query\":{\"neural\":{\"book_content_embedding\":{\"query_text\":\"which book has the introduction of AWS AgentCore\"}}}}\n\nQuestion: how many books published in 2024\n{\"query\": {\"term\": {\"publication_year\": 2024}},\"size\": 0,\"track_total_hits\": true}\n",
        "prompt": "The index mappoing of ${parameters.index_name}:\n${parameters.IndexMappingTool.output:-}\n\nThe sample documents of ${parameters.index_name}:\n${parameters.SearchIndexTool.output:-}\n\nPlease generate the OpenSearch query dsl for the question:\n${parameters.question}",
        "response_filter": "$.output.message.content[1].text",
        "output_processors": [
          {
            "type": "regex_replace",
            "pattern": "<reasoning>.*?</reasoning>",
            "replacement": ""
          }
        ],
        "return_data_as_map": true
      },
      "include_output_in_agent_response": true
    },
    {
      "type": "SearchIndexTool",
      "name": "search_index_with_llm_generated_dsl",
      "include_output_in_agent_response": false,
      "parameters": {
        "input": "{\"index\": \"${parameters.index_name}\", \"query\": ${parameters.query_dsl} }",
        "query_dsl": "${parameters.generate_os_query_dsl.output}",
        "return_raw_response": true,
        "return_data_as_map": true
      },
      "attributes": {
        "required_parameters": [
          "index_name",
          "query_dsl",
          "generate_os_query_dsl.output"
        ]
      }
    }
  ]
}
```
Sample response
```
{
  "agent_id": "bPAsmJgBCqG4iVqlqYAR"
}
```
### 2.1.2 Test Flow Agent
```
POST _plugins/_ml/agents/bPAsmJgBCqG4iVqlqYAR/_execute
{
  "parameters": {
    "question": "How many total flights from Beijing?",
    "index_name": "opensearch_dashboards_sample_data_flights"
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
          "name": "generate_os_query_dsl.output",
          "dataAsMap": {
            "query": {
              "term": {
                "OriginCityName": "Beijing"
              }
            },
            "size": 0.0,
            "track_total_hits": true
          }
        },
        {
          "name": "search_index_with_llm_generated_dsl",
          "dataAsMap": {
            "_shards": {
              "total": 1,
              "failed": 0,
              "successful": 1,
              "skipped": 0
            },
            "hits": {
              "hits": [],
              "total": {
                "value": 131,
                "relation": "eq"
              },
              "max_score": null
            },
            "took": 1,
            "timed_out": false
          }
        }
      ]
    }
  ]
}
```

## 2.2 Chat Agent

### 2.2.1 Create Chat Agent
```
POST _plugins/_ml/agents/_register
{
  "name": "RAG Agent",
  "type": "conversational",
  "description": "this is a test agent",
  "app_type": "rag",
  "llm": {
    "model_id": "afArmJgBCqG4iVqlioA9",
    "parameters": {
      "max_iteration": 10,
      "system_prompt": "You are an expert RAG (Retrieval Augmented Generation) assistant with access to OpenSearch indices. Your primary purpose is to help users find and understand information by retrieving relevant content from the OpenSearch indices.\n\nSEARCH GUIDELINES:\n1. When a user asks for information:\n   - First determine which index to search (use ListIndexTool if unsure)\n   - For complex questions, break them down into smaller sub-questions\n   - Be specific and targeted in your search queries\n\n\nRESPONSE GUIDELINES:\n1. Always cite which index the information came from\n2. Synthesize search results into coherent, well-structured responses\n3. Use formatting (headers, bullet points) to organize information when appropriate\n4. If search results are insufficient, acknowledge limitations and suggest alternatives\n5. Maintain context from the conversation history when formulating responses\n\nPrioritize accuracy over speculation. Be professional, helpful, and concise in your interactions.",
      "prompt": "${parameters.question}"
    }
  },
  "memory": {
    "type": "conversation_index"
  },
  "parameters": {
    "_llm_interface": "bedrock/converse/claude"
  },
  "tools": [
    {
      "type": "ListIndexTool"
    },
    {
      "type": "AgentTool",
      "name": "search_opensearch_index_with_nlq",
      "include_output_in_agent_response": false,
      "description": "This tool accepts one OpenSearch index and one natrual language question and generate OpenSearch query DSL. Then query the index with generated query DSL. If the question if complex, suggest split it into smaller questions then query one by one.",
      "parameters": {
        "agent_id": "bPAsmJgBCqG4iVqlqYAR",
        "output_filter": "$.mlModelOutputs[0].mlModelTensors[2].dataAsMap"
      },
      "attributes": {
        "required_parameters": [
          "index_name",
          "question"
        ],
        "input_schema": {
          "type": "object",
          "properties": {
            "question": {
              "type": "string",
              "description": "Natural language question"
            },
            "index_name": {
              "type": "string",
              "description": "Name of the index to query"
            }
          },
          "required": [
            "question"
          ],
          "additionalProperties": false
        },
        "strict": false
      }
    }
  ]
}
```
Sample response
```
{
  "agent_id": "bvAtmJgBCqG4iVql54Ck"
}
```

### 2.2.2 Test Chat Agent
```
POST /_plugins/_ml/agents/bvAtmJgBCqG4iVql54Ck/_execute
{
  "parameters": {
    "question": "How many flights from Seattle to Canada",
    "max_iteration": 30,
    "verbose": true
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
          "name": "memory_id",
          "result": "hMc4mJgBLapFVETfRl5I"
        },
        {
          "name": "parent_interaction_id",
          "result": "hcc4mJgBLapFVETfRl5n"
        },
        {
          "name": "response",
          "result": "{\"metrics\":{\"latencyMs\":2210},\"output\":{\"message\":{\"content\":[{\"text\":\"<reasoning>We need to answer: number of flights from Seattle to Canada. Likely need to search an index containing flight data. Not sure what's available. Let's list indices.</reasoning>\"},{\"toolUse\":{\"input\":{\"indices\":[]},\"name\":\"ListIndexTool\",\"toolUseId\":\"tooluse_cjlogX--SPy4d_jLUF-1kg\"}}],\"role\":\"assistant\"}},\"stopReason\":\"tool_use\",\"usage\":{\"inputTokens\":265,\"outputTokens\":52,\"totalTokens\":317}}"
        },
        {
          "name": "response",
          "result": "row,health,status,index,uuid,pri(number of primary shards),rep(number of replica shards),docs.count(number of available documents),docs.deleted(number of deleted documents),store.size(store size of primary and replica shards),pri.store.size(store size of primary shards)\n1,green,open,.plugins-ml-model-group,HU6XuHpjRp6mPe4GYn0qqQ,1,0,4,0,6.7kb,6.7kb\n2,green,open,otel-v1-apm-service-map-sample,KYlj9cclQXCFWKTsNGV3Og,1,0,49,0,17.7kb,17.7kb\n3,green,open,.plugins-ml-memory-meta,CCnEW7ixS_KrJdmEl330lw,1,0,568,18,89kb,89kb\n4,green,open,.ql-datasources,z4aXBn1oQoSXNiCMvwapkw,1,0,0,0,208b,208b\n5,green,open,security-auditlog-2025.08.11,IwYKN08KTJqGnIJ5tVURzA,1,0,113,0,198.7kb,198.7kb\n6,yellow,open,test_tech_news,2eA8yCDIRcaKoUhDwnD4uQ,1,1,3,0,23.1kb,23.1kb\n7,yellow,open,test_mem_container,VS57K2JvQmyXlJxDwJ9nhA,1,1,0,0,208b,208b\n8,green,open,.plugins-ml-task,GTX8717YRtmw0nkl1vnXMg,1,0,818,35,215.1kb,215.1kb\n9,green,open,ss4o_metrics-otel-sample,q5W0oUlZRuaEVtfL1dknKA,1,0,39923,0,4.3mb,4.3mb\n10,green,open,.opendistro_security,3AmSFRp1R42BSFMPfXXVqw,1,0,9,0,83.7kb,83.7kb\n11,yellow,open,test_population_data,fkgI4WqQSv2qHSaXRJv_-A,1,1,4,0,22.5kb,22.5kb\n12,green,open,.plugins-ml-model,Ksvz5MEwR42RmrOZAg6RTQ,1,0,25,36,597.5kb,597.5kb\n13,green,open,.plugins-ml-memory-container,TYUOuxfMT5i4DdOMfYe45w,1,0,2,0,9.9kb,9.9kb\n14,green,open,.kibana_-969161597_admin123_1,dlCY0iSQR9aTtRJsJx9cIg,1,0,1,0,5.3kb,5.3kb\n15,green,open,opensearch_dashboards_sample_data_ecommerce,HdLqBN2QQCC4nUhE-5mOkA,1,0,4675,0,4.1mb,4.1mb\n16,green,open,security-auditlog-2025.08.04,Ac34ImxkQSeonGeW8RSYcw,1,0,985,0,1mb,1mb\n17,green,open,.plugins-ml-memory-message,osbPSzqeQd2sCPF2yqYMeg,1,0,2648,8,4mb,4mb\n18,green,open,security-auditlog-2025.08.05,hBi-5geLRqml4mDixXMPYQ,1,0,854,0,1.1mb,1.1mb\n19,green,open,security-auditlog-2025.08.07,bY2GJ1Z8RAG7qJb8dcHktw,1,0,5,0,75.8kb,75.8kb\n20,green,open,otel-v1-apm-span-sample,mewMjWmHSdOlGNzgz-OnXg,1,0,13061,0,6.2mb,6.2mb\n21,green,open,security-auditlog-2025.08.01,lvwEW0_VQnq88o7XjJkoPw,1,0,57,0,216.9kb,216.9kb\n22,green,open,.kibana_92668751_admin_1,w45HvTqcTQ-eVTWK77lx7A,1,0,242,0,125.2kb,125.2kb\n23,green,open,.plugins-ml-agent,Q-kWqw9_RdW3SfhUcKBcfQ,1,0,165,0,435.7kb,435.7kb\n24,green,open,security-auditlog-2025.08.03,o_iRgiWZRryXrhQ-y36JQg,1,0,847,0,575.9kb,575.9kb\n25,green,open,.kibana_1,tn9JwuiSSe6gR9sInHFxDg,1,0,4,0,16.7kb,16.7kb\n26,green,open,security-auditlog-2025.08.09,uugjBoB_SR67h4455K5KPw,1,0,58,0,161.9kb,161.9kb\n27,green,open,ss4o_logs-otel-sample,wIaGQx8wT1ijhxDjjbux6A,1,0,16286,0,5.6mb,5.6mb\n28,green,open,.plugins-ml-config,uh1sYBiTRYy_lK61Rx0fkQ,1,0,1,0,4kb,4kb\n29,green,open,opensearch_dashboards_sample_data_logs,jDDgZ_PfTkKHfaDqBl72lQ,1,0,14074,0,8.3mb,8.3mb\n30,green,open,opensearch_dashboards_sample_data_flights,7dcplp7MRc2C9WTPyLRRIg,1,0,13059,0,5.8mb,5.8mb\n"
        },
        {
          "name": "response",
          "result": "{\"metrics\":{\"latencyMs\":1548},\"output\":{\"message\":{\"content\":[{\"text\":\"<reasoning>We need to answer: \\\"How many flights from Seattle to Canada\\\". Likely the index opensearch_dashboards_sample_data_flights contains flight data. Let's query that index.</reasoning>\"},{\"toolUse\":{\"input\":{\"question\":\"How many flights from Seattle to Canada\",\"index_name\":\"opensearch_dashboards_sample_data_flights\"},\"name\":\"search_opensearch_index_with_nlq\",\"toolUseId\":\"tooluse_XjafWd4RTUO72qqkwhOC_A\"}}],\"role\":\"assistant\"}},\"stopReason\":\"tool_use\",\"usage\":{\"inputTokens\":1016,\"outputTokens\":78,\"totalTokens\":1094}}"
        },
        {
          "name": "response",
          "result": "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"OriginCityName\":\"Seattle\"}},{\"term\":{\"DestCountry\":\"Canada\"}}]}},\"size\":0.0,\"track_total_hits\":true}"
        },
        {
          "name": "response",
          "result": "{\"metrics\":{\"latencyMs\":1351},\"output\":{\"message\":{\"content\":[{\"text\":\"<reasoning>The search returns 0 hits. The dataset may not contain such flights, or maybe field names differ. Let's open index mapping or sample data. Let's search for flights from Seattle.</reasoning>\"},{\"toolUse\":{\"input\":{\"question\":\"Seattle flights\",\"index_name\":\"opensearch_dashboards_sample_data_flights\"},\"name\":\"search_opensearch_index_with_nlq\",\"toolUseId\":\"tooluse_g7FFHR26RoqBhJ02kf1KcQ\"}}],\"role\":\"assistant\"}},\"stopReason\":\"tool_use\",\"usage\":{\"inputTokens\":1176,\"outputTokens\":72,\"totalTokens\":1248}}"
        },
        {
          "name": "response",
          "result": "{\"query\":{\"match\":{\"DestCityName\":\"Seattle\"}}}"
        },
        {
          "name": "response",
          "result": "{\"metrics\":{\"latencyMs\":1229},\"output\":{\"message\":{\"content\":[{\"text\":\"<reasoning>We need count flights from Seattle to Canada. Flights where OriginCityName == Seattle and DestCountry == Canada. Index opensearch_dashboards_sample_data_flights.\\n\\nWe must query.</reasoning>\"},{\"toolUse\":{\"input\":{\"question\":\"Seattle flights to Canada\",\"index_name\":\"opensearch_dashboards_sample_data_flights\"},\"name\":\"search_opensearch_index_with_nlq\",\"toolUseId\":\"tooluse_XAI2YKD9Sx616oXtqZljzQ\"}}],\"role\":\"assistant\"}},\"stopReason\":\"tool_use\",\"usage\":{\"inputTokens\":3583,\"outputTokens\":75,\"totalTokens\":3658}}"
        },
        {
          "name": "response",
          "result": "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"OriginCityName\":\"Seattle\"}},{\"term\":{\"DestCountry\":\"Canada\"}}]}}}"
        },
        {
          "name": "response",
          "result": "{\"metrics\":{\"latencyMs\":1114},\"output\":{\"message\":{\"content\":[{\"text\":\"<reasoning>The user asks: \\\"How many flights from Seattle to Canada\\\". We need to query the flights dataset. The index opensearch_dashboards_sample_data_flights appears to have fields: OriginCityName, DestCountry.\\n\\nWe attempted a query with OriginCityName: Seattle and DestCountry: Canada, got zero results.\\n\\nMaybe the field names are different; maybe use OriginCityName = Seattle? Check a sample doc where OriginCityName is Seattle. Do we have any Seattle as origin? Let's search for OriginCityName \\\"Seattle\\\".</reasoning>\"},{\"toolUse\":{\"input\":{\"question\":\"OriginCityName Seattle\",\"index_name\":\"opensearch_dashboards_sample_data_flights\"},\"name\":\"search_opensearch_index_with_nlq\",\"toolUseId\":\"tooluse_5tEq-NJfRcGCXjnQ6o7w8Q\"}}],\"role\":\"assistant\"}},\"stopReason\":\"tool_use\",\"usage\":{\"inputTokens\":3736,\"outputTokens\":154,\"totalTokens\":3890}}"
        },
        {
          "name": "response",
          "result": "{\"query\":{\"term\":{\"OriginCityName\":\"Seattle\"}}}"
        },
        {
          "name": "response",
          "result": "{\"metrics\":{\"latencyMs\":1433},\"output\":{\"message\":{\"content\":[{\"text\":\"<reasoning>The user asks: \\\"How many flights from Seattle to Canada\\\". We need to count flights where OriginCityName is Seattle and DestCountry is Canada (or DestRegion Canada). The dataset appears to be sample flight data. The earlier query with both constraints returned 0 hits. Possibly there are no flights from Seattle to Canada in this sample dataset. However we should verify maybe DestCountry field sometimes uses two-letter code? The sample shows DestCountry values like \\\"US\\\", \\\"AT\\\", \\\"IT\\\". For Canada likely \\\"CA\\\". Let's search DestCountry:CA with Origin Seattle.</reasoning>\"},{\"toolUse\":{\"input\":{\"question\":\"OriginCityName Seattle AND DestCountry CA\",\"index_name\":\"opensearch_dashboards_sample_data_flights\"},\"name\":\"search_opensearch_index_with_nlq\",\"toolUseId\":\"tooluse_0Sv4fGB4T_Kp1_2-k83JGA\"}}],\"role\":\"assistant\"}},\"stopReason\":\"tool_use\",\"usage\":{\"inputTokens\":6248,\"outputTokens\":174,\"totalTokens\":6422}}"
        },
        {
          "name": "response",
          "result": "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"OriginCityName\":\"Seattle\"}},{\"term\":{\"DestCountry\":\"CA\"}}]}}}"
        },
        {
          "name": "response",
          "result": "**Answer**\n\nThere are **5 flights** in the dataset that originate from Seattle and have a destination in Canada (DestCountry = “CA”).\n\n**Source**\n\n- Data retrieved from the **`opensearch_dashboards_sample_data_flights`** index. The query filtered for `OriginCityName = \"Seattle\"` and `DestCountry = \"CA\"` and returned five matching documents."
        }
      ]
    }
  ]
}
```