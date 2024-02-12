# Topic

> Agent Framework is an experimental feature released in OpenSearch 2.12 and not recommended for use in a production environment. For updates on the progress of the feature or if you want to leave feedback, see the associated [GitHub issue](https://github.com/opensearch-project/ml-commons/issues/1161).

> This tutorial doesn't explain what's retrieval-augmented generation(RAG).

This tutorial explains how to use conversational flow agent to build RAG application by leveraging your 
OpenSearch data as knowledge base.

Note: You should replace the placeholders with prefix `your_` with your own value

# Steps

## 0. Preparation

To build RAG application, we need to have some OpenSearch index as knowledge base. In this tutorial, we
are going to use [k-NN index](https://opensearch.org/docs/latest/search-plugins/knn/knn-index/) and 
[semantic search](https://opensearch.org/docs/latest/search-plugins/semantic-search/). You can read more 
details on their document and this [tutorial](https://opensearch.org/docs/latest/search-plugins/neural-search-tutorial/).
It's totally fine to just follow below steps to quick start.

### update cluster setting

If you have dedicated ML node, you don't need to set `"plugins.ml_commons.only_run_on_ml_node": false`.

We set `"plugins.ml_commons.native_memory_threshold"` as 100% to avoid triggering native memory circuit breaker.
```
PUT _cluster/settings
{
    "persistent": {
        "plugins.ml_commons.only_run_on_ml_node": false,
        "plugins.ml_commons.native_memory_threshold": 100,
        "plugins.ml_commons.agent_framework_enabled": true,
        "plugins.ml_commons.memory_feature_enabled": true
    }
}
```

## 1. Prepare knowledge base

### 1.1 register text embedding model

Find more details on [pretrained model](https://opensearch.org/docs/latest/ml-commons-plugin/pretrained-models/)

1. Upload model:
```
POST /_plugins/_ml/models/_register
{
  "name": "huggingface/sentence-transformers/all-MiniLM-L12-v2",
  "version": "1.0.1",
  "model_format": "TORCH_SCRIPT"
}
```
Find model id by calling get task API.

Copy the text embedding model id, will use it in following steps.
```
GET /_plugins/_ml/tasks/your_task_id
```
2. Deploy model
```
POST /_plugins/_ml/models/your_text_embedding_model_id/_deploy
```
3. Test predict
```
POST /_plugins/_ml/models/your_text_embedding_model_id/_predict
{
  "text_docs":[ "today is sunny"],
  "return_number": true,
  "target_response": ["sentence_embedding"]
}
```

### 1.2 create ingest pipeline and k-NN index

1. Create ingest pipeline 

Create pipeline with text embedding processor which can invoke model created in step1.1 to translate text
field to embedding.

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

3. Ingest test data
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

Find more details on [Remote model](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/index/)

We use [Bedrock Claude model](https://aws.amazon.com/bedrock/claude/) in this tutorial. You can also use other LLM.

1. Create connector
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

Copy the connector id from the response. 

2. register model

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

We will use `conversational_flow` agent in this tutorial.

The agent consists of:
1. meta info: `name`, `type`, `description`
2. `app_type`: this is to differentiate different application type
3. `memory`: this is to store agent execution result, so user can retrieve memory later and continue one conversation.
4. `tools`: define a list of tools to use. Agent will run tools sequentially.
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

Sample response
```
{
  "agent_id": "fQ75lI0BHcHmo_czdqcJ"
}
```

Copy the agent id, will use it in next step. 

## 4. Execute Agent

### 4.1 Start a new conversation

Run the agent to analyze Seattle population increase.

When run this agent, it will create a new conversation. 
Later you can continue the conversation by asking other questions.

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "what's the population increase of Seattle from 2021 to 2023?"
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
1. `memory_id` means the conversation id, copy it as we will use in Step4.2
2. `parent_message_id` means the current interaction (one round of question/answer), one conversation can have multiple interactions

Check more details of conversation by calling get memory API.
```
GET /_plugins/_ml/memory/gQ75lI0BHcHmo_cz2acL

GET /_plugins/_ml/memory/gQ75lI0BHcHmo_cz2acL/messages
```
Check more details of interaction by calling get message API.
```
GET /_plugins/_ml/memory/message/gg75lI0BHcHmo_cz2acZ
```
For debugging purpose, each interaction/message has its own trace data, you can find trace data by calling
```
GET /_plugins/_ml/memory/message/gg75lI0BHcHmo_cz2acZ/traces
```

### 4.2 Continue a conversation by asking new question

Continue last conversation by providing memory id from step4.1

Explanation of the input:
1. `message_history_limit`: specify how many historical messages included in this interaction.
2. `prompt`: use can customize prompt. For example, this example adds a new instruction `always learn useful information from chat history` 
and a new parameter `next_action`.

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

Sample response
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

You can also customize which tool to use in predict API.
For example, if you want to translate last answer into Chinese, you don't need to retrieve data from knowledge base.
Then you can use `selected_tools` to specify just run `bedrock_claude_model`.

Note: Agent will run tools sequentially with the new order defined in `selected_tools`. 

```
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "Translate last answer into Chinese?",
    "selected_tools": ["bedrock_claude_model"]
  }
}
```