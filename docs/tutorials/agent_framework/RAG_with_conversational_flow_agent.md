# Topic

> Agent Framework is an experimental feature released in OpenSearch 2.12 and not recommended for use in a production environment. For updates on the progress of the feature or if you want to leave feedback, see the associated [GitHub issue](https://github.com/opensearch-project/ml-commons/issues/1161).

> This tutorial doesn't explain what retrieval-augmented generation(RAG) is.

This tutorial explains how to use a conversational flow agent to build  a RAG application by using your 
OpenSearch data as knowledge base.

Note: You should replace the placeholders with prefix `your_` with your own value

# Steps

## 0. Preparation

To build a RAG application, you need to have an OpenSearch index as knowledge base. In this tutorial, you
are going to use a [k-NN index](https://opensearch.org/docs/latest/search-plugins/knn/knn-index/) and 
[semantic search](https://opensearch.org/docs/latest/search-plugins/semantic-search/). For 
more information, see this [tutorial](https://opensearch.org/docs/latest/search-plugins/neural-search-tutorial/).
For a quick start, follow the steps below.

### Update cluster settings

If you have a dedicated ML node, you don't need to set `"plugins.ml_commons.only_run_on_ml_node": false`.

To avoid triggering a native memory circuit breaker, set `"plugins.ml_commons.native_memory_threshold"` to 100%:
```
PUT _cluster/settings
{
    "persistent": {
        "plugins.ml_commons.only_run_on_ml_node": false,
        "plugins.ml_commons.native_memory_threshold": 100,
        "plugins.ml_commons.agent_framework_enabled": true
    }
}
```

## 1. Prepare knowledge base

### 1.1 Register text embedding model

For more information, see [Pretrained models](https://opensearch.org/docs/latest/ml-commons-plugin/pretrained-models/)

1. Upload model:
```
POST /_plugins/_ml/models/_register
{
  "name": "huggingface/sentence-transformers/all-MiniLM-L12-v2",
  "version": "1.0.1",
  "model_format": "TORCH_SCRIPT"
}
```
Get the model ID by calling the Get Task API.

Note the text embedding model ID; you will use it in the following steps.
```
GET /_plugins/_ml/tasks/your_task_id
```
2. Deploy model:
```
POST /_plugins/_ml/models/your_text_embedding_model_id/_deploy
```
3. Test model:
```
POST /_plugins/_ml/models/your_text_embedding_model_id/_predict
{
  "text_docs":[ "today is sunny"],
  "return_number": true,
  "target_response": ["sentence_embedding"]
}
```

### 1.2 Create ingest pipeline and k-NN index

1. Create ingest pipeline:

For more information, see [Ingest piplines](https://opensearch.org/docs/latest/ingest-pipelines/)

Create a pipeline with a text embedding processor, which can invoke the model created in Step 1.1 to translate text
fields to embeddings:

```
PUT /_ingest/pipeline/test_population_data_pipeline
{
    "description": "text embedding pipeline",
    "processors": [
        {
            "text_embedding": {
                "model_id": "your_text_embedding_model_id",
                "field_map": {
                    "population_description": "population_description_embedding"
                }
            }
        }
    ]
}
```

2. create k-NN index with the ingest pipeline.

For more information, see [k-NN index](https://opensearch.org/docs/latest/search-plugins/knn/knn-index/).
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
        "dimension": 384
      }
    }
  },
  "settings": {
    "index": {
      "knn.space_type": "cosinesimil",
      "default_pipeline": "test_population_data_pipeline",
      "knn": "true"
    }
  }
}
```

3. Ingest test data:
```
POST _bulk
{"index": {"_index": "test_population_data"}}
{"population_description": "Chart and table of population level and growth rate for the Ogden-Layton metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of Ogden-Layton in 2023 is 750,000, a 1.63% increase from 2022.\nThe metro area population of Ogden-Layton in 2022 was 738,000, a 1.79% increase from 2021.\nThe metro area population of Ogden-Layton in 2021 was 725,000, a 1.97% increase from 2020.\nThe metro area population of Ogden-Layton in 2020 was 711,000, a 2.16% increase from 2019."}
{"index": {"_index": "test_population_data"}}
{"population_description": "Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."}
{"index": {"_index": "test_population_data"}}
{"population_description": "Chart and table of population level and growth rate for the Chicago metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Chicago in 2023 is 8,937,000, a 0.4% increase from 2022.\\nThe metro area population of Chicago in 2022 was 8,901,000, a 0.27% increase from 2021.\\nThe metro area population of Chicago in 2021 was 8,877,000, a 0.14% increase from 2020.\\nThe metro area population of Chicago in 2020 was 8,865,000, a 0.03% increase from 2019."}
{"index": {"_index": "test_population_data"}}
{"population_description": "Chart and table of population level and growth rate for the Miami metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Miami in 2023 is 6,265,000, a 0.8% increase from 2022.\\nThe metro area population of Miami in 2022 was 6,215,000, a 0.78% increase from 2021.\\nThe metro area population of Miami in 2021 was 6,167,000, a 0.74% increase from 2020.\\nThe metro area population of Miami in 2020 was 6,122,000, a 0.71% increase from 2019."}
{"index": {"_index": "test_population_data"}}
{"population_description": "Chart and table of population level and growth rate for the Austin metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Austin in 2023 is 2,228,000, a 2.39% increase from 2022.\\nThe metro area population of Austin in 2022 was 2,176,000, a 2.79% increase from 2021.\\nThe metro area population of Austin in 2021 was 2,117,000, a 3.12% increase from 2020.\\nThe metro area population of Austin in 2020 was 2,053,000, a 3.43% increase from 2019."}
{"index": {"_index": "test_population_data"}}
{"population_description": "Chart and table of population level and growth rate for the Seattle metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Seattle in 2023 is 3,519,000, a 0.86% increase from 2022.\\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021.\\nThe metro area population of Seattle in 2021 was 3,461,000, a 0.82% increase from 2020.\\nThe metro area population of Seattle in 2020 was 3,433,000, a 0.79% increase from 2019."}

```

## 2. Prepare LLM

For more information, see [Remote models](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/index/).

This tutorial uses the [Bedrock Claude model](https://aws.amazon.com/bedrock/claude/). You can also use other LLMs.

1. Create connector:
```
POST /_plugins/_ml/connectors/_create
{
  "name": "BedRock Claude instant-v1 Connector ",
  "description": "The connector to BedRock service for claude model",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "us-east-1",
    "service_name": "bedrock",
    "anthropic_version": "bedrock-2023-05-31",
    "max_tokens_to_sample": 8000,
    "temperature": 0.0001,
    "response_filter": "$.completion"
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
      "url": "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-instant-v1/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{\"prompt\":\"${parameters.prompt}\", \"max_tokens_to_sample\":${parameters.max_tokens_to_sample}, \"temperature\":${parameters.temperature},  \"anthropic_version\":\"${parameters.anthropic_version}\" }"
    }
  ]
}
```

Note the connector ID ; you'll use it to register the model.

2. Register model:

```
POST /_plugins/_ml/models/_register
{
    "name": "Bedrock Claude Instant model",
    "function_name": "remote",
    "description": "Bedrock Claude instant-v1 model",
    "connector_id": "your_LLM_connector_id"
}
```
Copy the LLM model id from the response, will use it in following steps.

3. Deploy model
```
POST /_plugins/_ml/models/your_LLM_model_id/_deploy
```

4. Test predict
```
POST /_plugins/_ml/models/your_LLM_model_id/_predict
{
  "parameters": {
    "prompt": "\n\nHuman: how are you? \n\nAssistant:"
  }
}
```

## 3. Create Agent
Agent framework provides several agent types: `flow`, `conversational_flow` and `conversational`.

You will use `conversational_flow` agent in this tutorial.

The agent consists of:
1. meta info: `name`, `type`, `description`
2. `app_type`: To differentiate different application types
3. `memory`: To store user questions and LLM responses as a conversation so an agent can retrieve conversation history from memory and continue the same conversation.
4. `tools`: Define a list of tools to use. The agent will run these tools sequentially.
```
POST /_plugins/_ml/agents/_register
{
    "name": "population data analysis agent",
    "type": "conversational_flow",
    "description": "This is a demo agent for population data analysis",
    "app_type": "rag",
    "memory": {
        "type": "conversation_index"
    },
    "tools": [
        {
            "type": "VectorDBTool",
            "name": "population_knowledge_base",
            "parameters": {
                "model_id": "your_text_embedding_model_id",
                "index": "test_population_data",
                "embedding_field": "population_description_embedding",
                "source_field": [
                    "population_description"
                ],
                "input": "${parameters.question}"
            }
        },
        {
            "type": "MLModelTool",
            "name": "bedrock_claude_model",
            "description": "A general tool to answer any question",
            "parameters": {
                "model_id": "your_LLM_model_id",
                "prompt": "\n\nHuman:You are a professional data analysist. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say don't know. \n\nContext:\n${parameters.population_knowledge_base.output:-}\n\n${parameters.chat_history:-}\n\nHuman:${parameters.question}\n\nAssistant:"
            }
        }
    ]
}
```

Sample response:
```
{
  "agent_id": "fQ75lI0BHcHmo_czdqcJ"
}
```

Note the agent ID; you will use it in the next step. 

## 4. Execute Agent

### 4.1 Start a new conversation

Run the agent to analyze the Seattle population increase.

When you run this agent, the agent will create a new conversation. 
Later, you can continue this conversation by asking other questions.

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "what's the population increase of Seattle from 2021 to 2023?"
  }
}
```

Sample response:
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "memory_id",
          "result": "gQ75lI0BHcHmo_cz2acL" 
        },
        {
          "name": "parent_message_id",
          "result": "gg75lI0BHcHmo_cz2acZ"
        },
        {
          "name": "bedrock_claude_model",
          "result": """ Based on the context given:
- The metro area population of Seattle in 2021 was 3,461,000
- The current metro area population of Seattle in 2023 is 3,519,000
- So the population increase of Seattle from 2021 to 2023 is 3,519,000 - 3,461,000 = 58,000"""
        }
      ]
    }
  ]
}
```
Explanation of the output:
1. `memory_id` is the identifier for the memory that groups all messages within a single conversation. Note this ID; you will use it in Step 4.2.
2. `parent_message_id` is the identifier for current message (one round of question/answer) between human and AI. One memory can have multiple messages.

To get the details of the memory, call the Get Memory API:
```
GET /_plugins/_ml/memory/gQ75lI0BHcHmo_cz2acL

GET /_plugins/_ml/memory/gQ75lI0BHcHmo_cz2acL/messages
```
To get the details of a message, call the Get Message API:
```
GET /_plugins/_ml/memory/message/gg75lI0BHcHmo_cz2acZ
```
For debugging purposes, each message has its own trace data. To get trace data, call the Get Traces API:
```
GET /_plugins/_ml/memory/message/gg75lI0BHcHmo_cz2acZ/traces
```

### 4.2 Continue a conversation by asking new questions

To continue the same conversation, provide its memory ID from step 4.1.

Explanation of the input:
1. `message_history_limit`: Specify how many historical messages you want included in the new round of question/answering with Agent.
2. `prompt`: Used to customize the LLM prompt. For example, this example adds a new instruction `always learn useful information from chat history` 
and a new parameter `next_action`:

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "What's the population of New York City in 2023?",
    "next_action": "then compare with Seattle population of 2023",
    "memory_id": "gQ75lI0BHcHmo_cz2acL",
    "message_history_limit": 5,
    "prompt": "\n\nHuman:You are a professional data analysist. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say don't know. \n\nContext:\n${parameters.population_knowledge_base.output:-}\n\n${parameters.chat_history:-}\n\nHuman:always learn useful information from chat history\nHuman:${parameters.question}, ${parameters.next_action}\n\nAssistant:"
  }
}
```

Sample response:
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "memory_id",
          "result": "gQ75lI0BHcHmo_cz2acL"
        },
        {
          "name": "parent_message_id",
          "result": "wQ4JlY0BHcHmo_cz8Kc-"
        },
        {
          "name": "bedrock_claude_model",
          "result": """ Based on the context given:
- The current metro area population of New York City in 2023 is 18,937,000
- The current metro area population of Seattle in 2023 is 3,519,000
- So the population of New York City in 2023 (18,937,000) is much higher than the population of Seattle in 2023 (3,519,000)"""
        }
      ]
    }
  ]
}
```

You can also customize which tool to use in  the Predict API.
For example, if you want to translate the previous answer into Chinese, you don't need to retrieve data from knowledge base.
Use `selected_tools` to just run `the bedrock_claude_model`.

Note: The agent will run the tools sequentially in the new order defined in `selected_tools`. 

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "Translate last answer into Chinese?",
    "selected_tools": ["bedrock_claude_model"]
  }
}
```

## 5. Advanced Topics
### 5.1 Configure multiple knowledge bases
You can configure multiple knowledge bases in an agent. For example, if you have product description and comments data,
you can configure the agent as follows: 
```
{
    "name": "My product agent",
    "type": "conversational_flow",
    "description": "This is an agent with product description and comments knowledge bases.",
    "memory": {
        "type": "conversation_index"
    },
    "app_type": "rag",
    "tools": [
        {
            "type": "VectorDBTool",
            "name": "product_description_vectordb",
            "parameters": {
                "model_id": "your_embedding_model_id",
                "index": "product_description_data",
                "embedding_field": "product_description_embedding",
                "source_field": [
                    "product_description"
                ],
                "input": "${parameters.question}"
            }
        },
        {
            "type": "VectorDBTool",
            "name": "product_comments_vectordb",
            "parameters": {
                "model_id": "your_embedding_model_id",
                "index": "product_comments_data",
                "embedding_field": "product_comment_embedding",
                "source_field": [
                    "product_comment"
                ],
                "input": "${parameters.question}"
            }
        },
        {
            "type": "MLModelTool",
            "description": "A general tool to answer any question",
            "parameters": {
                "model_id": "{{llm_model_id}}",
                "prompt": "\n\nHuman:You are a professional product recommendation engine. You will always recommend product based on the given context. If you don't have enough context, you will ask Human to provide more information. If you don't see any related product to recommend, just say we don't have such product. \n\n Context:\n${parameters.product_description_vectordb.output}\n\n${parameters.product_comments_vectordb.output}\n\nHuman:${parameters.question}\n\nAssistant:"
            }
        }
    ]
}
```
When you run the agent, it will query product description and comments data and then send query results and the question to the LLM.

To query a specific knowledge base, specify it in `selected_tools`.

For example, you can retrieve only `product_comments_vectordb` if the question only relates to product comments:

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "What feature people like the most for Amazon Echo Dot",
    "selected_tools": ["product_comments_vectordb", "MLModelTool"]
  }
}
```

### 5.2 Support any search query

Use `SearchIndexTool` to run any OpenSearch query on any index.

#### 5.2.1 Register agent
```
POST /_plugins/_ml/agents/_register
{
    "name": "Demo agent",
    "type": "conversational_flow",
    "description": "This is a test agent support running any search query",
    "memory": {
        "type": "conversation_index"
    },
    "app_type": "rag",
    "tools": [
        {
            "type": "SearchIndexTool",
            "parameters": {
                "input": "{\"index\": \"${parameters.index}\", \"query\": ${parameters.query} }"
            }
        },
        {
            "type": "MLModelTool",
            "description": "A general tool to answer any question",
            "parameters": {
                "model_id": "your_llm_model_id",
                "prompt": "\n\nHuman:You are a professional data analysist. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say don't know. \n\n Context:\n${parameters.SearchIndexTool.output:-}\n\nHuman:${parameters.question}\n\nAssistant:"
            }
        }
    ]
}
```
#### 5.2.2 Execute agent with BM25 query

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
    "parameters": {
        "question": "what's the population increase of Seattle from 2021 to 2023??",
        "index": "test_population_data",
        "query": {
            "query": {
                "match": {
                    "population_description": "${parameters.question}"
                }
            },
            "size": 2,
            "_source": "population_description"
        }
    }
}
```

#### 5.2.3 Execute agent with neural search query

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
    "parameters": {
        "question": "what's the population increase of Seattle from 2021 to 2023??",
        "index": "test_population_data",
        "query": {
            "query": {
                "neural": {
                    "population_description_embedding": {
                        "query_text": "${parameters.question}",
                        "model_id": "your_embedding_model_id",
                        "k": 10
                    }
                }
            },
            "size": 2,
            "_source": ["population_description"]
        }
    }
}
```

#### 5.2.4 Execute agent with hybrid search query
For more information, see [Hybrid Search](https://opensearch.org/docs/latest/search-plugins/hybrid-search),

Configure search pipeline:
```
PUT /_search/pipeline/nlp-search-pipeline
{
    "description": "Post processor for hybrid search",
    "phase_results_processors": [
      {
        "normalization-processor": {
          "normalization": {
            "technique": "min_max"
          },
          "combination": {
            "technique": "arithmetic_mean",
            "parameters": {
              "weights": [
                0.3,
                0.7
              ]
            }
          }
        }
      }
    ]
  }
```

Run agent with hybrid search:
```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
    "parameters": {
        "question": "what's the population increase of Seattle from 2021 to 2023??",
        "index": "test_population_data",
        "query": {
            "_source": {
                "exclude": [
                    "population_description_embedding"
                ]
            },
            "size": 2,
            "query": {
                "hybrid": {
                    "queries": [
                        {
                            "match": {
                                "population_description": {
                                    "query": "${parameters.question}"
                                }
                            }
                        },
                        {
                            "neural": {
                                "population_description_embedding": {
                                    "query_text": "${parameters.question}",
                                    "model_id": "your_embedding_model_id",
                                    "k": 10
                                }
                            }
                        }
                    ]
                }
            }
        }
    }
}
```

### 5.3 Natural language query (NLQ)

The `PPLTool` can translate natural language to [PPL](https://opensearch.org/docs/latest/search-plugins/sql/ppl/index/)
and execute the generated PPL query.

#### 5.3.1 Register agent with PPLTool

PPLTool parameters:
- `model_type` (Enum): `CLAUDE`, `OPENAI`, or `FINETUNE`.
- `execute` (Boolean): If `true`, executes the generated PPL query.
- `input` (String): You must provide the `index` and `question`.

In this tutorial, you'll use Bedrock Claude, so set `model_type` to `CLAUDE`:
```
POST /_plugins/_ml/agents/_register
{
    "name": "Demo agent for NLQ",
    "type": "conversational_flow",
    "description": "This is a test flow agent for NLQ",
    "memory": {
        "type": "conversation_index"
    },
    "app_type": "rag",
    "tools": [
        {
            "type": "PPLTool",
            "parameters": {
                "model_id": "your_ppl_model_id",
                "model_type": "CLAUDE",
                "execute": true,
                "input": "{\"index\": \"${parameters.index}\", \"question\": ${parameters.question} }"
            }
        },
        {
            "type": "MLModelTool",
            "description": "A general tool to answer any question",
            "parameters": {
                "model_id": "your_llm_model_id",
                "prompt": "\n\nHuman:You are a professional data analysist. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say don't know. \n\n Context:\n${parameters.PPLTool.output:-}\n\nHuman:${parameters.question}\n\nAssistant:"
            }
        }
    ]
}
```
#### 5.3.2 Execute agent with NLQ

1. Go to OpenSearch Dashboards home page, select "Add sample data", then add "Sample eCommerce orders".
2. Run agent:
```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
    "parameters": {
        "question": "How many orders do I have in last week",
        "index": "opensearch_dashboards_sample_data_ecommerce"
    }
}
```
Sample response:
```
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "memory_id",
                    "result": "sqIioI0BJhBwrVXYeYOM"
                },
                {
                    "name": "parent_message_id",
                    "result": "s6IioI0BJhBwrVXYeYOW"
                },
                {
                    "name": "MLModelTool",
                    "result": " Based on the given context, the number of orders in the last week is 3992. The data shows a query that counts the number of orders where the order date is greater than 1 week ago. The query result shows the count as 3992."
                }
            ]
        }
    ]
}
```
For more details, get trace data:
```
GET _plugins/_ml/memory/message/s6IioI0BJhBwrVXYeYOW/traces
```
