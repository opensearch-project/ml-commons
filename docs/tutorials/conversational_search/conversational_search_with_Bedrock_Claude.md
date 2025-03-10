# Topic

This tutorial explains how to use conversational search with Bedrock Claude 3.5 and Claude 2 model. For more information, see [Conversational search](https://opensearch.org/docs/latest/search-plugins/conversational-search/).

Note: Replace the placeholders that start with `your_` with your own values.
Claude3 model not supported yet.

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

This tutorial will show two options by using Bedrock Converse and Invoke APIs.
## Option 1. Bedrock Converse API
### 1.1 Create connector and model:

Note: replace `"model": "anthropic.claude-3-5-sonnet-20240620-v1:0"` as `"model": "anthropic.claude-v2"` if need to use Claude 2.
```
POST _plugins/_ml/connectors/_create
{
    "name": "Amazon Bedrock claude v3",
    "description": "Test connector for Amazon Bedrock claude v3",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "your_access_key",
        "secret_key": "your_secret_key",
        "session_token": "your_session_token"
    },
    "parameters": {
        "region": "your_aws_region",
        "service_name": "bedrock",
        "model": "anthropic.claude-3-5-sonnet-20240620-v1:0",
        "system_prompt": "you are a helpful assistant.",
        "temperature": 0.0,
        "top_p": 0.9,
        "max_tokens": 1000
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
            "request_body": "{ \"system\": [{\"text\": \"${parameters.system_prompt}\"}], \"messages\": ${parameters.messages} , \"inferenceConfig\": {\"temperature\": ${parameters.temperature}, \"topP\": ${parameters.top_p}, \"maxTokens\": ${parameters.max_tokens}} }"
        }
    ]
}
```

Note the connector ID; you will use it to create the model.

Create model:
```
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Bedrock Claude3.5 model",
    "description": "Bedrock Claude3.5 model",
    "function_name": "remote",
    "connector_id": "your_connector_id"
}
```

Note the model ID; you will use it in the following steps.

Test the model:
```
POST /_plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "text": "hello"
          }
        ]
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
            "metrics": {
              "latencyMs": 955.0
            },
            "output": {
              "message": {
                "content": [
                  {
                    "text": "Hello! How can I assist you today? Feel free to ask me any questions or let me know if you need help with anything."
                  }
                ],
                "role": "assistant"
              }
            },
            "stopReason": "end_turn",
            "usage": {
              "inputTokens": 14.0,
              "outputTokens": 30.0,
              "totalTokens": 44.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
### 1.2 Create search pipeline and run RAG
```
PUT /_search/pipeline/my-conversation-search-pipeline-claude
{
  "response_processors": [
    {
      "retrieval_augmented_generation": {
        "tag": "Demo pipeline",
        "description": "Demo pipeline Using Bedrock Claude",
        "model_id": "your_model_id",
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

Basic RAG search without storing conversation history.
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-claude
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
      "llm_model": "bedrock-converse/anthropic.claude-3-sonnet-20240229-v1:0",
      "llm_question": "What's the population increase of New York City from 2021 to 2023?",
      "context_size": 5
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
      "answer": "The population of the New York City metro area increased by 114,000 people from 2021 to 2023. In 2021, the population was 18,823,000. By 2023, it had grown to 18,937,000. This represents a total increase of about 0.61% over the two-year period, with growth rates of 0.23% from 2021 to 2022 and 0.37% from 2022 to 2023."
    }
  }
}
```

### 1.3 Conversational Search
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
  "memory_id": "sBAqY5UBSzdNxlHvrSJK"
}
```

2. Search by specifying memory id
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-claude
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
      "llm_model": "bedrock-converse/anthropic.claude-3-sonnet-20240229-v1:0",
      "llm_question": "What's the population increase of New York City from 2021 to 2023?",
      "context_size": 5,
      "memory_id": "sBAqY5UBSzdNxlHvrSJK"
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
      "answer": "The population of the New York City metro area increased by 114,000 people from 2021 to 2023. In 2021, the population was 18,823,000. By 2023, it had grown to 18,937,000. This represents a total increase of about 0.61% over the two-year period, with growth rates of 0.23% from 2021 to 2022 and 0.37% from 2022 to 2023.",
      "message_id": "sRAqY5UBSzdNxlHvzCIL"
    }
  }
}
```
3. Continue conversation
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-claude
{
  "query": {
    "match": {
      "text": "What's the population increase of Chicago from 2021 to 2023?"
    }
  },
  "size": 1,
  "_source": [
    "text"
  ],
  "ext": {
    "generative_qa_parameters": {
      "llm_model": "bedrock-converse/anthropic.claude-3-sonnet-20240229-v1:0",
      "llm_question": "can you compare the population increase of Chicago with New York City",
      "context_size": 5,
      "memory_id": "sBAqY5UBSzdNxlHvrSJK"
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
        "_id": "3",
        "_score": 3.6660428,
        "_source": {
          "text": "Chart and table of population level and growth rate for the Chicago metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of Chicago in 2023 is 8,937,000, a 0.4% increase from 2022.\\nThe metro area population of Chicago in 2022 was 8,901,000, a 0.27% increase from 2021.\\nThe metro area population of Chicago in 2021 was 8,877,000, a 0.14% increase from 2020.\\nThe metro area population of Chicago in 2020 was 8,865,000, a 0.03% increase from 2019."
        }
      }
    ]
  },
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "Based on the provided data for Chicago, we can compare its population increase to New York City from 2021 to 2023:\n\nChicago's population increased from 8,877,000 in 2021 to 8,937,000 in 2023, a total increase of 60,000 people or about 0.68%.\n\nNew York City's population increased by 114,000 people or 0.61% in the same period.\n\nWhile New York City had a larger absolute increase, Chicago experienced a slightly higher percentage growth rate during this two-year period.",
      "message_id": "shArY5UBSzdNxlHvQyL-"
    }
  }
}
```

## Option 2. Bedrock Invoke API
This one doesn't works for Claude 3.x as 3.x model interface is different. 

### 2.1. Create connector and model:

```
POST _plugins/_ml/connectors/_create
{
    "name": "Bedrock Claude2",
    "description": "Connector for Bedrock Claude2",
    "version": 1,
    "protocol": "aws_sigv4",
    "credential": {
        "access_key": "your_access_key",
        "secret_key": "your_secret_key",
        "session_token": "your_session_token"
    },
    "parameters": {
        "region": "your_aws_region",
        "service_name": "bedrock",
        "model": "anthropic.claude-v2"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "request_body": "{\"prompt\":\"\\n\\nHuman: ${parameters.inputs}\\n\\nAssistant:\",\"max_tokens_to_sample\":300,\"temperature\":0.5,\"top_k\":250,\"top_p\":1,\"stop_sequences\":[\"\\\\n\\\\nHuman:\"]}"
        }
    ]
}
```

Note the connector ID; you will use it to create the model.

Create model:
```
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "Bedrock Claude2 model",
    "function_name": "remote",
    "description": "Bedrock Claude2 model",
    "connector_id": "your_connector_id"
}
```

Note the model ID; you will use it in the following steps.

Test the model:
```
POST /_plugins/_ml/models/your_model_id/_predict
{
    "parameters": {
      "inputs": "Who won the world series in 2020?"
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
            "type": "completion",
            "completion": " The Los Angeles Dodgers won the 2020 World Series, defeating the Tampa Bay Rays 4 games to 2. The World Series was played at a neutral site in Arlington, Texas due to the COVID-19 pandemic. It was the Dodgers' first World Series championship since 1988.",
            "stop_reason": "stop_sequence",
            "stop": "\n\nHuman:"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

### 2.2 Create search pipeline and search

Create pipeline
```
PUT /_search/pipeline/my-conversation-search-pipeline-claude2
{
  "response_processors": [
    {
      "retrieval_augmented_generation": {
        "tag": "Demo pipeline",
        "description": "Demo pipeline Using Bedrock Claude2",
        "model_id": "your_model_id",
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

Search
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-claude2
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
      "llm_model": "bedrock/claude",
      "llm_question": "What's the population increase of New York City from 2021 to 2023?",
      "context_size": 5,
      "timeout": 15
    }
  }
}
```
Sample response is similar to option1.

Refer to Step 1.3 for conversational search
