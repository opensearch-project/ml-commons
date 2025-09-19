# Topic

This tutorial explains how to use conversational search with a model hosted locally in Ollama or any other local/self-hosted LLM as long as it is OpenAI compatible (Ollama, llama.cpp, vLLM, etc). For more information, see [Conversational search](https://opensearch.org/docs/latest/search-plugins/conversational-search/).

Note: Replace the placeholders that start with `your_` with your own values.

The other way to build RAG/conversational search is using Agent Framework, see [RAG_with_conversational_flow_agent](../agent_framework/RAG_with_conversational_flow_agent.md)

# Steps

## 0. Preparation

Ingest test data:
```
POST _bulk
{"index": {"_index": "qa_demo", "_id": "1"}}
{"text": "Chart and table of population level and growth rate for the Ogden-Layton metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of Ogden-Layton in 2023 is 750,000, a 1.63% increase from 2022.\nThe metro area population of Ogden-Layton in 2022 was 738,000, a 1.79% increase from 2021.\nThe metro area population of Ogden-Layton in 2021 was 725,000, a 1.97% increase from 2020.\nThe metro area population of Ogden-Layton in 2020 was 711,000, a 2.16% increase from 2019."}
{"index": {"_index": "qa_demo", "_id": "2"}}
{"text": "Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."}
{"index": {"_index": "qa_demo", "_id": "3"}}
{"text": "Chart and table of population level and growth rate for the Chicago metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Chicago in 2023 is 8,937,000, a 0.4% increase from 2022.\\nThe metro area population of Chicago in 2022 was 8,901,000, a 0.27% increase from 2021.\\nThe metro area population of Chicago in 2021 was 8,877,000, a 0.14% increase from 2020.\\nThe metro area population of Chicago in 2020 was 8,865,000, a 0.03% increase from 2019."}
{"index": {"_index": "qa_demo", "_id": "4"}}
{"text": "Chart and table of population level and growth rate for the Miami metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Miami in 2023 is 6,265,000, a 0.8% increase from 2022.\\nThe metro area population of Miami in 2022 was 6,215,000, a 0.78% increase from 2021.\\nThe metro area population of Miami in 2021 was 6,167,000, a 0.74% increase from 2020.\\nThe metro area population of Miami in 2020 was 6,122,000, a 0.71% increase from 2019."}
{"index": {"_index": "qa_demo", "_id": "5"}}
{"text": "Chart and table of population level and growth rate for the Austin metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Austin in 2023 is 2,228,000, a 2.39% increase from 2022.\\nThe metro area population of Austin in 2022 was 2,176,000, a 2.79% increase from 2021.\\nThe metro area population of Austin in 2021 was 2,117,000, a 3.12% increase from 2020.\\nThe metro area population of Austin in 2020 was 2,053,000, a 3.43% increase from 2019."}
{"index": {"_index": "qa_demo", "_id": "6"}}
{"text": "Chart and table of population level and growth rate for the Seattle metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Seattle in 2023 is 3,519,000, a 0.86% increase from 2022.\\nThe metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021.\\nThe metro area population of Seattle in 2021 was 3,461,000, a 0.82% increase from 2020.\\nThe metro area population of Seattle in 2020 was 3,433,000, a 0.79% increase from 2019."}

```

## 1. Create connector and model

1. Create connector for an Ollama model:

Follow [this blueprint](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/remote_inference_blueprints/ollama_connector_chat_blueprint.md) in case you haven't created a connector before.

```
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "http",
  "parameters": {
    "endpoint": "127.0.0.1:11434",
    "model": "qwen3:4b"
  },
  "credential": {
    "openAI_key": "<YOUR API KEY HERE IF NEEDED>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Authorization": "Bearer ${credential.openAI_key}"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
    }
  ]
}
```

Note the connector ID; you will use it to create the model.

2. Create model:

In this example we are using `?deploy=true` in the POST request to automatically deploy the model.

```
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "Local LLM Model",
  "function_name": "remote",
  "description": "Ollama model",
  "connector_id": "92B0V5kBw_FJbXhqNWdu"
}
```

Note the model ID; you will use it in the following steps.

3. Test the model:

```
POST /_plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Who won the world series in 2020?"
      }
    ]
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
          "name": "response",
          "dataAsMap": {
            "choices": [
              {
                "finish_reason": "stop",
                "index": 0.0,
                "message": {
                  "role": "assistant",
                  "content": "The Los Angeles Dodgers won the World Series in 2020. They defeated the Tampa Bay Rays in six games to secure their first championship since 1988."
                }
              }
            ],
            "created": 1757369906,
            "model": "qwen3:4b",
            "system_fingerprint": "b6259-cebb30fb",
            "object": "chat.completion",
            "usage": {
              "completion_tokens": 264,
              "prompt_tokens": 563,
              "total_tokens": 827
            },
            "id": "chatcmpl-iHioFpaxa8K2SXgAHd4FhQnbewLQ9PjB",
            "timings": {
              "prompt_n": 563,
              "prompt_ms": 293.518,
              "prompt_per_token_ms": 0.5213463587921847,
              "prompt_per_second": 1918.1106439809487,
              "predicted_n": 264,
              "predicted_ms": 5084.336,
              "predicted_per_token_ms": 19.258848484848485,
              "predicted_per_second": 51.92418439693993
            }
          }
        }
      ],
      "status_code": 200
    }
```

## 2. Conversational search

### 2.1 Create pipeline

```
PUT /_search/pipeline/my-conversation-search-pipeline-openai
{
  "response_processors": [
    {
      "retrieval_augmented_generation": {
        "tag": "Demo pipeline",
        "description": "Demo pipeline Using Cohere",
        "model_id": "your_model_id_created_in_step1",
        "context_field_list": [
          "text"
        ],
        "system_prompt": "You are a helpful assistant",
        "user_instructions": "Generate a concise and informative answer in less than 100 words for the given question"
      }
    }
  ]
}

```

### 2.2 Basic RAG Search

Basic RAG search without storing conversation history.

```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-openai
{
  "query": {
    "match": {
      "text": "What's the population increase of New York City from 2021 to 2023?"
    }
  },
  "size": 1,
  "_source": [
    "text"
  ],
  "ext": {
    "generative_qa_parameters": {
      "llm_model": "gpt-4o",
      "llm_question": "What's the population increase of New York City from 2021 to 2023?",
      "context_size": 5,
      "timeout": 15
    }
  }
}

```
Sample response:
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
      "value": 6,
      "relation": "eq"
    },
    "max_score": 9.042081,
    "hits": [
      {
        "_index": "qa_demo",
        "_id": "2",
        "_score": 9.042081,
        "_source": {
          "text": "Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."
        }
      }
    ]
  },
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "The population of the New York City metro area increased by 114,000 from 2021 to 2023, rising from 18,823,000 in 2021 to 18,937,000 in 2023."
    }
  }
}
```

### 2.3 Conversational Search
You can store conversation history to memory and continue the conversation later. 

1. Create memory
```
POST /_plugins/_ml/memory/
{
  "name": "Conversation about NYC population"
}
```
Sample response
```
{
  "memory_id": "rBAbY5UBSzdNxlHvIyI3"
}
```

2. Search by specifying memory id
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-openai
{
  "query": {
    "match": {
      "text": "What's the population increase of New York City from 2021 to 2023?"
    }
  },
  "size": 1,
  "_source": [
    "text"
  ],
  "ext": {
    "generative_qa_parameters": {
      "llm_model": "gpt-4o",
      "llm_question": "What's the population increase of New York City from 2021 to 2023?",
      "context_size": 5,
      "timeout": 15,
      "memory_id": "rBAbY5UBSzdNxlHvIyI3"
    }
  }
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
      "value": 6,
      "relation": "eq"
    },
    "max_score": 9.042081,
    "hits": [
      {
        "_index": "qa_demo",
        "_id": "2",
        "_score": 9.042081,
        "_source": {
          "text": "Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."
        }
      }
    ]
  },
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "The population of the New York City metro area increased from 18,823,000 in 2021 to 18,937,000 in 2023. This represents an increase of 114,000 people over the two-year period.",
      "message_id": "rRAcY5UBSzdNxlHvyiI1"
    }
  }
}
```
3. Continue conversation
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-openai
{
  "query": {
    "match": {
      "text": "What's the population increase of Miami from 2021 to 2023?"
    }
  },
  "size": 1,
  "_source": [
    "text"
  ],
  "ext": {
    "generative_qa_parameters": {
      "llm_model": "gpt-4o",
      "llm_question": "compare population increase of New York City and Miami",
      "context_size": 5,
      "timeout": 15,
      "memory_id": "rBAbY5UBSzdNxlHvIyI3"
    }
  }
}
```
Response
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
      "value": 6,
      "relation": "eq"
    },
    "max_score": 3.6660428,
    "hits": [
      {
        "_index": "qa_demo",
        "_id": "4",
        "_score": 3.6660428,
        "_source": {
          "text": "Chart and table of population level and growth rate for the Miami metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Miami in 2023 is 6,265,000, a 0.8% increase from 2022.\\nThe metro area population of Miami in 2022 was 6,215,000, a 0.78% increase from 2021.\\nThe metro area population of Miami in 2021 was 6,167,000, a 0.74% increase from 2020.\\nThe metro area population of Miami in 2020 was 6,122,000, a 0.71% increase from 2019."
        }
      }
    ]
  },
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "From 2021 to 2023, the New York City metro area increased by 114,000 people, while the Miami metro area grew by 98,000 people. This means New York City saw a slightly larger population increase compared to Miami over the same period.",
      "message_id": "rhAdY5UBSzdNxlHv5SKa"
    }
  }
}
```