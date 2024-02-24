# Topic

This tutorial explains how to use conversational search with the OCI Genai. For more information, see [Conversational search](https://opensearch.org/docs/latest/search-plugins/conversational-search/).

Note: Replace the placeholders that start with `your_` with your own values.

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

1. Create connector for Oci Genai model:
```
POST /_plugins/_ml/connectors/_create
{
  "name": "OCI Genai Chat Connector",
  "description": "The connector to OCI Genai chat API",
  "version": 2,
  "protocol": "oci_sigv1",
  "parameters": {
    "endpoint": "inference.generativeai.us-chicago-1.oci.oraclecloud.com/",
    "auth_type": "resource_principal"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/20231130/actions/generateText",
      "request_body": "{\"prompts\": [\"${parameters.prompt}\"], \"maxTokens\": 300, \"temperature\": 1, \"frequencyPenalty\": 0, \"presencePenalty\": 0, \"topP\": 0.75, \"topK\": 0, \"compartmentId\": \"ocid1.compartment.oc1..aaaaaaaadhycphouqp6xuxrvjoq7czfv44o2r5aoz53g3bw6temcu432t76a\", \"returnLikelihoods\": \"NONE\", \"isStpromptream\": false, \"stopSequences\": [], \"servingMode\": { \"modelId\": \"cohere.command\", \"servingType\": \"ON_DEMAND\" } }"
    }
  ]
}
```

Note the connector ID; you will use it to create the model.

2. Create model:
```
POST /_plugins/_ml/models/_register
{
    "name": "OCI Genai Chat Model",
    "function_name": "remote",
    "model_group_id": "<MODEL_GROUP_ID>",
    "description": "Your OCI Genai Chat Model",
    "connector_id": "<CONNECTOR_ID>"
}
```

Note the model ID; you will use it in the following steps.

3. Test the model:
```
POST /_plugins/_ml/models/<MODEL_ID_HERE>/_predict
{
  "parameters": {
    "prompt": "What is the weather like in London?"
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
            "inferenceResponse": {
              "generatedTexts": [
                {
                  "text": "The weather in London is known..."
                }
              ]
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

## 2. Conversational search

### 2.1 Create pipeline
```
PUT /_search/pipeline/my-conversation-search-pipeline-ocigenai
{
  "response_processors": [
    {
      "retrieval_augmented_generation": {
        "tag": "Demo pipeline",
        "description": "Demo pipeline Using OCI Genai",
        "model_id": "your_model_id_created_in_step1",
        "context_field_list": [
          "text"
        ]
      }
    }
  ]
}

```

### 2.2 Search
Conversational search has some extra parameters you specify in `generative_qa_parameters`:
```
GET /qa_demo/_search?search_pipeline=my-conversation-search-pipeline-ocigenai
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
      "llm_model": "oci_genai/cohere.command",
      "llm_question": "when did us pass espionage act? answer only in two sentences using provided context.",
      "context_size": 2,
      "interaction_size": 1,
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
          "text": """Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019."""
        }
      }
    ]
  },
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "The population of the New York City metro area increased by about 210,000 from 2021 to 2023. The 2021 population was 18,823,000, and in 2023 it was 18,937,000. The average growth rate is 0.23% yearly."
    }
  }
}
```