---
layout: default
title: Agentic search with a llmGenerated query type
parent: Agentic search
nav_order: 30
---

# Topic

This tutorial works for 3.2 and above. To read more details, see the OpenSearch document [Agents and tools](https://opensearch.org/docs/latest/ml-commons-plugin/agents-tools/index/).

This is an experimental feature and is not recommended for use in a production environment. For updates on the progress of the feature or if you want to leave feedback, join the discussion on the [OpenSearch forum](https://forum.opensearch.org/).
{: .warning}

Agentic Search is a new query type proposed in OpenSearch that triggers an agent-driven workflow for query understanding, planning and execution. Instead of hand-crafting DSL, you supply a natural language question and an agent id; the agent executes a Query Planning Tool with index/mapping introspection, and guide LLMs to produce OpenSearch DSL and run it through agentic query clause and return the search hits based on it.

This tutorial demonstrates how to use the `QueryPlanningTool` with the `llmGenerated` type to translate natural language questions into OpenSearch Query DSL queries.

**Note**: Replace the placeholders that start with `your_` with your own values.

# Steps
1. Enable agentic search using the `agentic_search_enabled` setting
2. Set up a base LLM model using Amazon Bedrock Claude
3. Create and configure an agent with the `QueryPlanningTool`
4. Create a sample index and populate it with test data
5. Create a search pipeline with the `agentic_query_translator` processor
6. Execute natural language queries using the agentic search query

The agentic search feature provides several benefits:
- Allows users to search using natural language instead of complex DSL queries
- Automatically translates questions into appropriate OpenSearch queries
- Supports various query types including term queries, match queries, and aggregations
- Provides flexibility through customizable agents and search pipelines

## Step 1: Enable the agentic search feature flag

To use the `QueryPlanningTool`, you must first enable the `agentic_search_enabled` setting:

```json
PUT _cluster/settings
{
  "persistent" : {
    "plugins.ml_commons.agentic_search_enabled" : true,
    "plugins.neural_search.agentic_search_enabled": true
  }
}
```
{% include copy-curl.html %}

**Sample Response**
```json
{
  "acknowledged": true,
  "persistent": {
    "plugins": {
      "ml_commons": {
        "agentic_search_enabled": "true"
      }
    }
  },
  "transient": {}
}
```

## Step 2: Create an LLM

We will use the Bedrock Claude 3.7 model in this tutorial.

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "agentic_search_base_model",
  "function_name": "remote",
  "description": "Agentic search base model",
  "connector": {
    "name": "Amazon Bedrock Claude 3.7 Sonnet Connector",
    "description": "Connector for the base agent with tools",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "your_aws_region",
      "service_name": "bedrock",
      "model": "anthropic.claude-3-sonnet-20240229-v1:0",
      "system_prompt": "Please help answer the user question."
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
        "request_body": "{ \"system\": [{\"text\": \"${parameters.system_prompt}\"}], \"messages\": [${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.user_prompt}\"}]}${parameters._interactions:-}]${parameters.tool_configs:-} }"
      }
    ]
  }
}
```
{% include copy-curl.html %}

**Sample Response**
```json
{
  "task_id": "your_task_id",
  "status": "CREATED",
  "model_id": "your_model_id"
}
```

### Test the model

Test the model by running the `_predict` API.

```json
POST _plugins/_ml/models/your_model_id/_predict
{
    "parameters": {
        "user_prompt": "hello"
    }
}
```
{% include copy-curl.html %}

## Step 3: Prepare the data

### 3.1 Create an index

Create an index named `shipment` with mappings for the fields in the sample dataset:

```json
PUT /shipment
{
  "mappings": {
    "properties": {
      "category": { "type": "keyword" },
      "currency": { "type": "keyword" },
      "customer_full_name": {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "customer_gender": { "type": "keyword" },
      "order_date": { "type": "date" },
      "products": {
        "type": "nested",
        "properties": {
          "base_price": { "type": "float" },
          "product_name": { "type": "text" },
          "category": { "type": "keyword" }
        }
      },
      "geoip": {
        "properties": {
          "city_name": { "type": "keyword" }
        }
      }
    }
  }
}
```
{% include copy-curl.html %}

### 3.2 Ingest documents

Ingest documents into the index created in the previous step by sending the following `_bulk` request:

```json
POST _bulk
{ "index": { "_index": "shipment", "_id": "1" } }
{ "category": ["Women's Shoes", "Women's Clothing"], "customer_full_name": "Diane Goodwin", "customer_gender": "FEMALE", "order_date": "2023-11-26T21:44:38+00:00", "products": [{ "base_price": 59.99, "product_name": "Winter boots - brown", "category": "Women's Shoes" }, { "base_price": 11.99, "product_name": "Shorts - dark blue/pink/dark green", "category": "Women's Clothing" }], "geoip": { "city_name": null } }
{ "index": { "_index": "shipment", "_id": "2" } }
{ "category": ["Women's Shoes", "Women's Clothing"], "customer_full_name": "Rabbia Al Baker", "customer_gender": "FEMALE", "order_date": "2023-12-04T03:41:46+00:00", "products": [{ "base_price": 74.99, "product_name": "Boots - black", "category": "Women's Shoes" }, { "base_price": 32.99, "product_name": "Cardigan - black/white", "category": "Women's Clothing" }], "geoip": { "city_name": "Dubai" } }
{ "index": { "_index": "shipment", "_id": "3" } }
{ "category": ["Men's Accessories", "Men's Clothing"], "customer_full_name": "Eddie Gregory", "customer_gender": "MALE", "order_date": "2023-12-05T13:10:34+00:00", "products": [{ "base_price": 17.99, "product_name": "Belt - black", "category": "Men's Accessories" }, { "base_price": 10.99, "product_name": "Gloves - dark grey multicolor/bordeaux", "category": "Men's Accessories" }], "geoip": { "city_name": "Cairo" } }
```
{% include copy-curl.html %}

## Step 4: Create an agent

Create an agent of the `flow` type. This agent uses the `QueryPlanningTool` to convert natural language to DSL.

```json
POST /_plugins/_ml/agents/_register
{
  "name": "Agentic Search DSL Agent",
  "type": "flow",
  "description": "A test agent for query planning.",
  "tools": [
    {
      "type": "QueryPlanningTool",
      "description": "A general tool to answer any question.",
      "parameters": {
        "model_id": "your_model_id",
        "response_filter": "$.output.message.content[0].text"
      }
    }
  ]
}
```
{% include copy-curl.html %}

**Sample Response**
```json
{
  "agent_id": "your_agent_id"
}
```

## Step 5: Test the agent

To see how the agent converts a question into a DSL query, use the `_execute` API.

It's optional to provide `user_prompt`, as there is a default `user_prompt` in the agent.
If you would like to test the agent with a simple question and your custom user prompt, you can use the following example:


### Example: Question with aggregation

```json
POST /_plugins/_ml/agents/your_agent_id/_execute
{
    "parameters": {
        "user_prompt": "You are an OpenSearch Query DSL generation assistant, generate an OpenSearch Query DSL to retrieve the most relevant documents for the user provided natural language question: ${parameters.query_text}, please return the query dsl only, no other texts.",
        "query_text": "How many orders were placed by Diane Goodwin?"
    }
}
```
{% include copy-curl.html %}

**Sample Response**
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "result": "{\"query\":{\"bool\":{\"adjust_pure_negative\":true,\"must\":[{\"match_phrase\":{\"customer_full_name\":{\"query\":\"Diane Goodwin\",\"zero_terms_query\":\"NONE\",\"boost\":1.0,\"slop\":0}}}],\"boost\":1.0}},\"aggregations\":{\"order_count\":{\"cardinality\":{\"field\":\"order_id\"}}}}"
        }
      ]
    }
  ]
}
```

The response contains the generated DSL query. The `QueryPlanningTool` intelligently interprets the natural language question:
- It understands that "How many" implies a need for an aggregation to count the results, not just retrieve them.
- It creates a `value_count` aggregation on the `_id` field to count the number of unique orders.
- It correctly identifies that "placed by Diane Goodwin" requires a `match` query on the `customer_full_name` field.


## Step 6: Use the agent in a search pipeline

After testing the agent, you can integrate it into a search pipeline for a seamless natural language search experience.

### 6.1 Create a search pipeline

Create a pipeline with the `agentic_query_translator` processor, referencing the agent ID from the previous step.

```json
PUT /_search/pipeline/agentic-pipeline
{
     "request_processors": [
        {
            "agentic_query_translator": {
                "agent_id": "your_agent_id"
            }
        }
     ]
}
```
{% include copy-curl.html %}

### 6.2 Search the index

To perform agentic search, use the `agentic` query clause with your natural language question and specify the search pipeline.

```json
GET shipment/_search?search_pipeline=agentic-pipeline
{
    "query": {
        "agentic": {
            "query_text": "How many orders were placed by Diane Goodwin?",
            "query_fields":"customer_full_name"
        }
    }
}
```
{% include copy-curl.html %}

The agent generates the DSL query (as seen in Step 5) and executes it against the index. The response contains the final search results, including the `aggregations` object.

**Sample Response**
```json
{
  "took": 5,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 1,
      "relation": "eq"
    },
    "max_score": 3.4228706,
    "hits": [
      {
        "_index": "shipment",
        "_id": "HVauupgBVvsXVzThCXHa",
        "_score": 3.4228706,
        "_source": {
          "category": [
            "Women's Shoes",
            "Women's Clothing"
          ],
          "currency": "EUR",
          "customer_first_name": "Diane",
          "customer_full_name": "Diane Goodwin",
          "customer_gender": "FEMALE",
          "customer_id": 22,
          "customer_last_name": "Goodwin",
          "customer_phone": "",
          "day_of_week": "Sunday",
          "day_of_week_i": 6,
          "email": "diane@goodwin-family.zzz",
          "manufacturer": [
            "Low Tide Media",
            "Pyramidustries"
          ],
          "order_date": "2023-11-26T21:44:38+00:00",
          "order_id": 574586,
          "products": [
            {
              "base_price": 59.99,
              "discount_percentage": 0,
              "quantity": 1,
              "manufacturer": "Low Tide Media",
              "tax_amount": 0,
              "product_id": 5419,
              "category": "Women's Shoes",
              "sku": "ZO0376303763",
              "taxless_price": 59.99,
              "unit_discount_amount": 0,
              "min_price": 31.79,
              "_id": "sold_product_574586_5419",
              "discount_amount": 0,
              "created_on": "2016-12-18T21:44:38+00:00",
              "product_name": "Winter boots - brown",
              "price": 59.99,
              "taxful_price": 59.99,
              "base_unit_price": 59.99
            },
            {
              "base_price": 11.99,
              "discount_percentage": 0,
              "quantity": 1,
              "manufacturer": "Pyramidustries",
              "tax_amount": 0,
              "product_id": 19325,
              "category": "Women's Clothing",
              "sku": "ZO0212402124",
              "taxless_price": 11.99,
              "unit_discount_amount": 0,
              "min_price": 6.47,
              "_id": "sold_product_574586_19325",
              "discount_amount": 0,
              "created_on": "2016-12-18T21:44:38+00:00",
              "product_name": "Shorts - dark blue/pink/dark green",
              "price": 11.99,
              "taxful_price": 11.99,
              "base_unit_price": 11.99
            }
          ],
          "sku": [
            "ZO0376303763",
            "ZO0212402124"
          ],
          "taxful_total_price": 71.98,
          "taxless_total_price": 71.98,
          "total_quantity": 2,
          "total_unique_products": 2,
          "type": "order",
          "user": "diane",
          "geoip": {
            "country_iso_code": "GB",
            "location": {
              "lon": -0.1,
              "lat": 51.5
            },
            "continent_name": "Europe"
          },
          "event": {
            "dataset": "sample_ecommerce"
          }
        }
      }
    ]
  },
  "aggregations": {
    "order_count": {
      "value": 1
    }
  }
}
```

Notice the `aggregations` object in the response. The `order_count` value of `3` directly answers the question "How many orders were placed by Diane Goodwin?" based on the sample data.

If you would like to check what query DSL was generated by the agent, you can use the `verbose_pipeline` API:

```json
GET shipment/_search?search_pipeline=agentic-pipeline&verbose_pipeline=true

{
  "query": {
    "agentic": {
      "query_text": "How many orders were placed by Diane Goodwin?",
      "query_fields":"customer_full_name"
    }
  }
}
```
You can see the match query with aggregation clause generated: 

```json
{
  "took": 2974,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 1,
      "relation": "eq"
    },
    "max_score": null,
    "hits": [...]
  },
  "aggregations": {
    "order_count": {
      "value": 1
    }
  },
  "processor_results": [
    {
      "processor_name": "agentic_query_translator",
      "duration_millis": 2970763781,
      "status": "success",
      "input_data": {
        "verbose_pipeline": true,
        "query": {
          "agentic": {
            "query_text": "How many orders were placed by Diane Goodwin?"
          }
        }
      },
      "output_data": { // here  is the llm generated query
        "query": {
          "bool": {
            "adjust_pure_negative": true,
            "must": [
              {
                "match_phrase": {
                  "customer_full_name": {
                    "query": "Diane Goodwin",
                    "zero_terms_query": "NONE",
                    "boost": 1.0,
                    "slop": 0
                  }
                }
              }
            ],
            "boost": 1.0
          }
        },
        "aggregations": {
          "order_count": {
            "cardinality": {
              "field": "order_id"
            }
          }
        }
      }
    }
  ]
}
```

```json
## Next steps

This is an experimental feature. See the following GitHub issues for information about future enhancements:

- [[RFC] Design for Agentic Search #1479](https://github.com/opensearch-project/ml-commons/issues/1479)
- [[RFC] Agentic Search in OpenSearch #4005](https://github.com/opensearch-project/OpenSearch/issues/4005)
