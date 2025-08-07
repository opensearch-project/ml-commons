# Topic

This tutorial works for 3.1 and above. To read more details, see the OpenSearch document [Agents and tools](https://opensearch.org/docs/latest/ml-commons-plugin/agents-tools/index/).

Read this AWS [retrieval-augmented-generation](https://aws.amazon.com/what-is/retrieval-augmented-generation/) doc to learn more details about RAG.

One of the known limitations of large language models (LLMs) is that their knowledge base only contains information up to the time when the LLMs were trained. 
LLMs have no knowledge of recent events or your internal data. 
You can augment the LLM knowledge base by using retrieval-augmented generation (RAG).

This tutorial explains how to build Agentic RAG, supplementing the LLM knowledge with information contained in OpenSearch indexes.

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 1. Preparation Knowledge Base

### 1.1 Create Embedding Model

We will use Bedrock Titan Embedding V2 in this tutorial.
```
POST /_plugins/_ml/models/_register
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
      "access_key": "your_aws_access_key",
      "secret_key": "your_aws_secret_key",
      "session_token": "your_aws_session_token"
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

Sample output
```
{
  "task_id": "-OVdX5gB_sp0V9yyvtqh",
  "status": "CREATED",
  "model_id": "6uVbX5gB_sp0V9yyy9ro"
}
```

Test model by running predict API
```
POST _plugins/_ml/models/6uVbX5gB_sp0V9yyy9ro/_predict
{
  "parameters": {
    "inputText": "What is the meaning of life?"
  }
}
```

Sample predict output
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1024
          ],
          "data": [
            -0.04134807735681534,
            ....
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

### 1.2 Create Population Knowledge Base

Create ingest pipeline
```
PUT _ingest/pipeline/test_population_data_pipeline
{
  "processors": [
    {
      "text_embedding": {
        "model_id": "your_embedding_model_id_from_step_1.1",
        "field_map": {
          "population_description": "population_description_embedding"
        }
      }
    }
  ]
}
```
Create K-NN index 
```
PUT test_population_data
{
  "mappings": {
    "properties": {
      "population_description": {
        "type": "text"
      },
      "population_description_embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": {
          "name": "hnsw",
          "engine": "lucene"
        }
      }
    }
  },
  "settings": {
    "index": {
      "default_pipeline": "test_population_data_pipeline",
      "knn": true
    }
  }
}
```
Ingest test data 
```
POST _bulk
{"index": {"_index": "test_population_data"}}
{"population_description": "The current metro area population of Ogden-Layton in 2025 is 771,000, a 1.31% increase from 2024.\nThe metro area population of Ogden-Layton in 2024 was 761,000, a 1.47% increase from 2023.\nThe metro area population of Ogden-Layton in 2023 was 750,000, a 1.63% increase from 2022.\nThe metro area population of Ogden-Layton in 2022 was 738,000, a 1.79% increase from 2021."}
{"index": {"_index": "test_population_data"}}
{"population_description": "The current metro area population of New York City in 2025 is 19,154,000, a 0.63% increase from 2024.\nThe metro area population of New York City in 2024 was 19,034,000, a 0.51% increase from 2023.\nThe metro area population of New York City in 2023 was 18,937,000, a 0.37% increase from 2022.\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021."}
{"index": {"_index": "test_population_data"}}
{"population_description": "The current metro area population of Austin in 2025 is 2,313,000, a 1.72% increase from 2024.\nThe metro area population of Austin in 2024 was 2,274,000, a 2.06% increase from 2023.\nThe metro area population of Austin in 2023 was 2,228,000, a 2.39% increase from 2022.\nThe metro area population of Austin in 2022 was 2,176,000, a 2.79% increase from 2021."}
{"index": {"_index": "test_population_data"}}
{"population_description": "The current metro area population of Seattle in 2025 is 3,581,000, a 0.9% increase from 2024.\nThe metro area population of Seattle in 2024 was 3,549,000, a 0.85% increase from 2023.\nThe metro area population of Seattle in 2023 was 3,519,000, a 0.86% increase from 2022.\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021."}

```
### 1.3 Create Tech News Knowledge Base

Create ingest pipeline
```
PUT _ingest/pipeline/test_tech_news_pipeline
{
  "processors": [
    {
      "text_embedding": {
        "model_id": "your_embedding_model_id_from_step_1.1",
        "field_map": {
          "passage": "passage_embedding"
        }
      }
    }
  ]
}
```
Create K-NN index
```
PUT test_tech_news
{
  "mappings": {
    "properties": {
      "passage": {
        "type": "text"
      },
      "passage_embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": {
          "name": "hnsw",
          "engine": "lucene"
        }
      }
    }
  },
  "settings": {
    "index": {
      "default_pipeline": "test_tech_news_pipeline",
      "knn": true
    }
  }
}
```

Ingest test data
```
POST _bulk
{"index":{"_index":"test_tech_news"}}
{"passage":"OpenSearch 3.1 release highlights: \nMakes GPU acceleration for vector index builds generally available. Introduces memory-optimized search for Faiss indexes using Lucene HNSW, semantic field type for streamlined semantic search, and Search Relevance Workbench for search quality optimization. Makes star-tree indexes generally available with support for comprehensive query types. Enhances observability with ML Commons metrics integration, custom index support for OpenTelemetry data, and new PPL commands for JSON manipulation. Improves agent management with Update Agent API and persistent MCP tools. Includes security enhancements with immutable user objects and new resource sharing framework. For a full list of release highlights, see the Release Notes."}
{"index":{"_index":"test_tech_news"}}
{"passage":"OpenSearch 3.0 release highlights:\nUpgrades to Lucene 10 for improved indexing and vector search. Adds experimental gRPC support and pull-based ingestion from Kafka and Kinesis. Introduces GPU acceleration for vector operations and semantic sentence highlighting. Improves range query performance and hybrid search with z-score normalization. Adds plan-execute-reflect agents and native MCP protocol support for agentic workflows. Enhances security with a new Java agent replacing the Security Manager. Includes PPL query improvements with lookup, join, and subsearch commands. For a full list of release highlights, see the Release Notes."}
{"index":{"_index":"test_tech_news"}}
{"passage":"Announcing Amazon S3 Vectors (Preview)—First cloud object storage with native support for storing and querying vectors:\nAmazon S3 Vectors delivers purpose-built, cost-optimized vector storage for AI agents, AI inference, and semantic search of your content stored in Amazon S3. By reducing the cost of uploading, storing, and querying vectors by up to 90%, S3 Vectors makes it cost-effective to create and use large vector datasets to improve the memory and context of AI agents as well as semantic search results of your S3 data. Designed to provide the same elasticity, scale, and durability as Amazon S3, S3 Vectors lets you store and search data with sub-second query performance. It's ideal for applications that need to build and maintain vector indexes so you can organize and search through massive amounts of information. S3 Vectors provides a simple and flexible API for operations such as finding similar scenes in petabyte-scale video archives, identifying collections of related business documents, or detecting rare patterns in diagnostic collections including millions of medical images.\nS3 Vectors is natively integrated with Amazon Bedrock Knowledge Bases so that you can reduce the cost of using large vector datasets for retrieval-augmented generation (RAG). You can also use S3 Vectors with Amazon OpenSearch Service to lower storage costs for infrequent queried vectors, and then quickly move them to OpenSearch as demands increase or to enhance search capabilities.\nS3 Vectors introduces a new bucket type optimized for durable, low-cost vector storage. It includes a dedicated set of APIs for you to store, access, and query vectors without provisioning any infrastructure. Within a vector bucket, you can organize vector data within vector indexes and elastically scale up to 10,000 indexes per bucket. When creating a Knowledge Base in Amazon Bedrock or Amazon SageMaker Unified Studio, you can select an S3 vector index as your vector store or use the Quick Create workflow to set one up. Within OpenSearch, you can adopt a tiered strategy to store large vector datasets in S3 for near real-time access while effortlessly activating the vector data with the highest performance requirements in OpenSearch."}

```

## 2. Create LLM
We will use Bedrock Claude 3.7 model in this tutorial.
```
POST _plugins/_ml/models/_register
{
  "name": "Bedrock Claude 3.7 model",
  "function_name": "remote",
  "description": "test model",
  "connector": {
    "name": "Bedrock Claude3.7 connector",
    "description": "test connector",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "your_aws_region",
      "service_name": "bedrock",
      "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0"
    },
    "credential": {
      "access_key": "your_aws_access_key",
      "secret_key": "your_aws_secret_key",
      "session_token": "your_aws_session_token"
    },
    "actions": [
      {
        "action_type": "predict",
        "method": "POST",
        "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
        "headers": {
          "content-type": "application/json"
        },
        "request_body": "{ \"system\": [{\"text\": \"${parameters.system_prompt}\"}], \"messages\": [${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.prompt}\"}]}${parameters._interactions:-}]${parameters.tool_configs:-} }"
      }
    ]
  }
}
```

Sample output
```
{
  "task_id": "sOV8X5gB_sp0V9yyjdvB",
  "status": "CREATED",
  "model_id": "seV8X5gB_sp0V9yyjdvf"
}
```

Test model with predict API
```
POST _plugins/_ml/models/seV8X5gB_sp0V9yyjdvf/_predict
{
    "parameters": {
        "system_prompt": "You are a helpful assistant.",
        "prompt": "hello"
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
              "latencyMs": 1587
            },
            "output": {
              "message": {
                "content": [
                  {
                    "text": "Hello! How can I assist you today? Whether you have a question, need information, or just want to chat, I'm here to help. What's on your mind?"
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
              "inputTokens": 14,
              "outputTokens": 39,
              "totalTokens": 53
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 3. Create Agent
Create an agent of the `conversational` type.

In this tutorial, the agent includes two tools that provide latest population data and tech news.

```
POST _plugins/_ml/agents/_register
{
  "name": "RAG Agent",
  "type": "conversational",
  "description": "this is a test agent",
  "app_type": "rag",
  "llm": {
    "model_id": "your_llm_id_from_step_2",
    "parameters": {
      "max_iteration": 10,
      "system_prompt": "You are a helpful assistant. You are able to assist with a wide range of tasks, from answering simple questions to providing in-depth explanations and discussions on a wide range of topics.\nIf the question is complex, you will split it into several smaller questions, and solve them one by one. For example, the original question is:\nhow many orders in last three month? Which month has highest?\nYou will spit into several smaller questions:\n1.Calculate total orders of last three month.\n2.Calculate monthly total order of last three month and calculate which month's order is highest.",
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
      "type": "SearchIndexTool",
      "name": "retrieve_population_data",
      "description": "This tool provides recent tech news.",
      "parameters": {
        "input": "{\"index\": \"${parameters.index}\", \"query\": ${parameters.query} }",
        "index": "test_population_data",
        "query": {
          "query": {
            "neural": {
              "population_description_embedding": {
                "query_text": "${parameters.question}",
                "model_id": "your_embedding_model_id_from_step_1.1"
              }
            }
          },
          "size": 2,
          "_source": "population_description"
        }
      },
      "attributes": {
        "input_schema": {
          "type": "object",
          "properties": {
            "question": {
              "type": "string",
              "description": "Natural language question"
            }
          },
          "required": [ "question" ],
          "additionalProperties": false
        },
        "strict": false
      }
    },
    {
      "type": "SearchIndexTool",
      "name": "retrieve_tech_news",
      "description": "This tool provides recent tech news.",
      "parameters": {
        "input": "{\"index\": \"${parameters.index}\", \"query\": ${parameters.query} }",
        "index": "test_tech_news",
        "query": {
          "query": {
            "neural": {
              "passage_embedding": {
                "query_text": "${parameters.question}",
                "model_id": "your_embedding_model_id_from_step_1.1"
              }
            }
          },
          "size": 2,
          "_source": "passage"
        }
      },
      "attributes": {
        "input_schema": {
          "type": "object",
          "properties": {
            "question": {
              "type": "string",
              "description": "Natural language question"
            }
          },
          "required": [ "question" ],
          "additionalProperties": false
        },
        "strict": false
      }
    }
  ]
}
```

Sample output
```
{
  "agent_id": "tbPvX5gBc62yu84LsNzh"
}
```

## 4. Test Agent

The `conversational` agent supports a `verbose` option. You can set `verbose` to `true` to obtain detailed steps.

Alternatively, you can use the Get Trace Data API:
```
GET _plugins/_ml/memory/message/your_message_id/traces
```

### 4.1 Simple Query Example
- Example 1: Ask a question related to tech news:
```
POST _plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "What's the population of Seattle 2025",
    "verbose": true
  }
}
```
In the response, note that the agent runs the `retrieve_population_data` tool to retrieve data.
The LLM uses the retrieved data to answer the question correctly.
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "memory_id",
          "result": "hbMPYJgBc62yu84LGN3I"
        },
        {
          "name": "parent_interaction_id",
          "result": "hrMPYJgBc62yu84LGN3Z"
        },
        {
          "name": "response",
          "result": """{"metrics":{"latencyMs":3290.0},"output":{"message":{"content":[{"text":"I need to provide information about Seattle\u0027s population for 2025. Since we\u0027re looking at a future date, I\u0027ll need to retrieve the most recent population data and any projections that might be available."},{"toolUse":{"input":{"question":"What is the projected population of Seattle for 2025?"},"name":"retrieve_population_data","toolUseId":"tooluse_W-EGMztFRzakU4Y_Yz_GpA"}}],"role":"assistant"}},"stopReason":"tool_use","usage":{"cacheReadInputTokenCount":0.0,"cacheReadInputTokens":0.0,"cacheWriteInputTokenCount":0.0,"cacheWriteInputTokens":0.0,"inputTokens":597.0,"outputTokens":109.0,"totalTokens":706.0}}"""
        },
        {
          "name": "response",
          "result": """{"_index":"test_population_data","_source":{"population_description":"The current metro area population of Seattle in 2025 is 3,581,000, a 0.9% increase from 2024.\nThe metro area population of Seattle in 2024 was 3,549,000, a 0.85% increase from 2023.\nThe metro area population of Seattle in 2023 was 3,519,000, a 0.86% increase from 2022.\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021."},"_id":"XLMMYJgBc62yu84LOd1K","_score":0.70810187}
{"_index":"test_population_data","_source":{"population_description":"The current metro area population of New York City in 2025 is 19,154,000, a 0.63% increase from 2024.\nThe metro area population of New York City in 2024 was 19,034,000, a 0.51% increase from 2023.\nThe metro area population of New York City in 2023 was 18,937,000, a 0.37% increase from 2022.\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021."},"_id":"WrMMYJgBc62yu84LOd1K","_score":0.4465592}
"""
        },
        {
          "name": "response",
          "result": """Based on the data I retrieved, the projected population of Seattle in 2025 is 3,581,000. This represents a 0.9% increase from 2024.

For context, here's how Seattle's population has grown in recent years:
- 2022: 3,489,000
- 2023: 3,519,000 (0.86% increase from 2022)
- 2024: 3,549,000 (0.85% increase from 2023)
- 2025: 3,581,000 (0.9% increase from 2024)

This data refers to the Seattle metropolitan area population rather than just the city limits of Seattle itself."""
        }
      ]
    }
  ]
}
```

### 4.2 Multi-Topic Question
```
POST _plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "Any new features released in OpenSearch 3.1? What's the population of Seattle in 2025",
    "verbose": true
  }
}
```
In the response, note that the agent runs `retrieve_tech_news` first , then run `retrieve_population_data` tool to retrieve latest data.  
Then LLM can answer the question correctly.
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "memory_id",
          "result": "kLMQYJgBc62yu84LcN1d"
        },
        {
          "name": "parent_interaction_id",
          "result": "kbMQYJgBc62yu84LcN1v"
        },
        {
          "name": "response",
          "result": """{"metrics":{"latencyMs":3540.0},"output":{"message":{"content":[{"text":"I\u0027ll help you answer both of your questions by breaking them down and using the appropriate tools.\n\nFirst, let\u0027s check for new features in OpenSearch 3.1:"},{"toolUse":{"input":{"question":"What are the new features released in OpenSearch 3.1?"},"name":"retrieve_tech_news","toolUseId":"tooluse_lDYhyUJxQiqd3cy9Grx3Zw"}}],"role":"assistant"}},"stopReason":"tool_use","usage":{"cacheReadInputTokenCount":0.0,"cacheReadInputTokens":0.0,"cacheWriteInputTokenCount":0.0,"cacheWriteInputTokens":0.0,"inputTokens":610.0,"outputTokens":105.0,"totalTokens":715.0}}"""
        },
        {
          "name": "response",
          "result": """{"_index":"test_tech_news","_source":{"passage":"OpenSearch 3.1 release highlights: \nMakes GPU acceleration for vector index builds generally available. Introduces memory-optimized search for Faiss indexes using Lucene HNSW, semantic field type for streamlined semantic search, and Search Relevance Workbench for search quality optimization. Makes star-tree indexes generally available with support for comprehensive query types. Enhances observability with ML Commons metrics integration, custom index support for OpenTelemetry data, and new PPL commands for JSON manipulation. Improves agent management with Update Agent API and persistent MCP tools. Includes security enhancements with immutable user objects and new resource sharing framework. For a full list of release highlights, see the Release Notes."},"_id":"b7MMYJgBc62yu84L_93y","_score":0.7947274}
{"_index":"test_tech_news","_source":{"passage":"OpenSearch 3.0 release highlights:\nUpgrades to Lucene 10 for improved indexing and vector search. Adds experimental gRPC support and pull-based ingestion from Kafka and Kinesis. Introduces GPU acceleration for vector operations and semantic sentence highlighting. Improves range query performance and hybrid search with z-score normalization. Adds plan-execute-reflect agents and native MCP protocol support for agentic workflows. Enhances security with a new Java agent replacing the Security Manager. Includes PPL query improvements with lookup, join, and subsearch commands. For a full list of release highlights, see the Release Notes."},"_id":"cLMMYJgBc62yu84L_93y","_score":0.6915282}
"""
        },
        {
          "name": "response",
          "result": """{"metrics":{"latencyMs":2723.0},"output":{"message":{"content":[{"text":"Now, let\u0027s get information about Seattle\u0027s population for 2025:"},{"toolUse":{"input":{"question":"What\u0027s the population of Seattle in 2025?"},"name":"retrieve_population_data","toolUseId":"tooluse_E-9ER5MfTO6KNM39AYOrnw"}}],"role":"assistant"}},"stopReason":"tool_use","usage":{"cacheReadInputTokenCount":0.0,"cacheReadInputTokens":0.0,"cacheWriteInputTokenCount":0.0,"cacheWriteInputTokens":0.0,"inputTokens":1118.0,"outputTokens":82.0,"totalTokens":1200.0}}"""
        },
        {
          "name": "response",
          "result": """{"_index":"test_population_data","_source":{"population_description":"The current metro area population of Seattle in 2025 is 3,581,000, a 0.9% increase from 2024.\nThe metro area population of Seattle in 2024 was 3,549,000, a 0.85% increase from 2023.\nThe metro area population of Seattle in 2023 was 3,519,000, a 0.86% increase from 2022.\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021."},"_id":"XLMMYJgBc62yu84LOd1K","_score":0.7388486}
{"_index":"test_population_data","_source":{"population_description":"The current metro area population of New York City in 2025 is 19,154,000, a 0.63% increase from 2024.\nThe metro area population of New York City in 2024 was 19,034,000, a 0.51% increase from 2023.\nThe metro area population of New York City in 2023 was 18,937,000, a 0.37% increase from 2022.\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021."},"_id":"WrMMYJgBc62yu84LOd1K","_score":0.45074782}
"""
        },
        {
          "name": "response",
          "result": """Here are the answers to your questions:

### New Features in OpenSearch 3.1

OpenSearch 3.1 includes several significant new features and improvements:

1. **GPU Acceleration**: Made generally available for vector index builds
2. **Memory-Optimized Search**: Introduced for Faiss indexes using Lucene HNSW
3. **Semantic Field Type**: Added for streamlined semantic search
4. **Search Relevance Workbench**: New tool for search quality optimization
5. **Star-Tree Indexes**: Now generally available with support for comprehensive query types
6. **Observability Enhancements**:
   - ML Commons metrics integration
   - Custom index support for OpenTelemetry data
   - New PPL commands for JSON manipulation
7. **Agent Management Improvements**:
   - Update Agent API
   - Persistent MCP tools
8. **Security Enhancements**:
   - Immutable user objects
   - New resource sharing framework

### Population of Seattle in 2025

According to the data, the metro area population of Seattle in 2025 is 3,581,000, which represents a 0.9% increase from 2024."""
        }
      ]
    }
  ]
}
```


### 4.3 Continue a conversation

You can continue the conversation by providing `memory_id`.
```
POST _plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "What's the population of Ogden-Layton 2025, compare with Seattle",
    "memory_id": "l7VUxI0B8vrNLhb9sRuQ",
    "verbose": true
  }
}
```
