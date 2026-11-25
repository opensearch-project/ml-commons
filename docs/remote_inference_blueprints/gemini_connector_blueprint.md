# Google Gemini connector blueprint example for Chat And Agentic Workflow

This blueprint integrates [Google Gemini](https://ai.google.dev/docs) for question-answering capabilities. Adapt and extend this blueprint as needed for your specific use case.

Note: This Blueprint uses the ReAct not function calling


## 1. Add connector endpoint to trusted URLs:
Note: skip this step starting 2.19.0

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://generativelanguage\\.googleapis\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for Google Gemini:

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Google Gemini Connector",
    "description": "Connector for Google Gemini API",
    "version": 1,
    "protocol": "http",
    "parameters": {
        "model": "gemini-2.5-flash",
        "response_filter": "$.candidates[0].content.parts[0].text"
    },
    "credential": {
        "gemini_api_key": "<PLEASE ADD YOUR GEMINI API KEY HERE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent",
            "headers": {
                "Content-Type": "application/json",
                "x-goog-api-key": "${credential.gemini_api_key}"
            },
            "request_body": "{\"systemInstruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt:-You are a helpful assistant.}\"}]},\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.prompt}\"}]}]}"
        }
    ]
}
```

#### Sample response
```json
{
  "connector_id": "V7-h8JsBLFaHfLhAy6OG"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_gemini",
    "description": "Model group for Google Gemini"
}
```

#### Sample response
```json
{
  "model_group_id": "c7-z8JsBLFaHfLhAKaNF",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "Google Gemini Chat model",
  "function_name": "remote",
  "model_group_id": "c7-z8JsBLFaHfLhAKaNF",
  "description": "Google Gemini 2.5 Flash",
  "connector_id": "V7-h8JsBLFaHfLhAy6OG"
}
```

#### Sample response
```json
{
    "task_id": "Wb-i8JsBLFaHfLhAoaPn",
    "status": "CREATED",
    "model_id": "Wr-i8JsBLFaHfLhAoqNC"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/Wr-i8JsBLFaHfLhAoqNC/_predict
{
  "parameters": {
    "prompt": "How are you?"
  }
}
```

#### Sample response
```json
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "response",
                    "dataAsMap": {
                        "response": "As an AI, I don't have feelings or a physical state, but I'm functioning perfectly and ready to help you!\n\nHow can I assist you today?"
                    }
                }
            ],
            "status_code": 200
        }
    ]
}
```

## 6. Register Agent

```json
POST /_plugins/_ml/agents/_register
{
    "name": "Gemini Chat Agent",
    "type": "conversational",
    "description": "Chat agent using Google Gemini with ReAct prompting",
    "app_type": "os_chat",
    "llm": {
        "model_id": "Wr-i8JsBLFaHfLhAoqNC",
        "parameters": {
            "max_iteration": 15,
            "message_history_limit": 15
        }
    },
    "memory": {
        "type": "conversation_index"
    },
    "tools": [
        {
            "type": "SearchIndexTool"
        },
        {
            "type": "IndexMappingTool"
        },
        {
            "type": "ListIndexTool"
        }
    ]
}
```

#### Sample response
```json
{
    "agent_id": "eL-08JsBLFaHfLhAo6PE"
}
```


## 7. Execute Agent

```json
POST /_plugins/_ml/agents/eL-08JsBLFaHfLhAo6PE/_execute
{
  "parameters": {
    "question": "What indices do I have in my cluster?, how does product related index look like, find few sample docs",
    "verbose": true
  }
}
```

#### Sample response
```json
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "memory_id",
                    "result": "gr-68JsBLFaHfLhAPKP0"
                },
                {
                    "name": "parent_interaction_id",
                    "result": "g7-68JsBLFaHfLhAPaNA"
                },
                {
                    "name": "response",
                    "result": "{\n    \"thought\": \"The user is asking for a list of all indices in the cluster, the structure of a 'product related' index, and a sample document from such an index. I need to first list all indices to answer the first part of the question and identify potential 'product related' indices. After that, I can use IndexMappingTool and SearchIndexTool. So, I will start by listing all indices.\",\n    \"action\": \"ListIndexTool\",\n    \"action_input\": {}\n}"
                },
                {
                    "name": "response",
                    "result": "row,health,status,index,uuid,pri(number of primary shards),rep(number of replica shards),docs.count(number of available documents),docs.deleted(number of deleted documents),store.size(store size of primary and replica shards),pri.store.size(store size of primary shards)\n1,green,open,.plugins-ml-memory-message,dlYpNA9GQCejY_CR6hwEKw,1,0,25,1,41.2kb,41.2kb\n2,green,open,.plugins-ml-model-group,LWRfEYucQCa2grYS-onjbw,1,0,2,0,18.3kb,18.3kb\n3,green,open,.plugins-ml-memory-meta,UYjnouGqTQCmVoB6vKfDIg,1,0,5,1,23.7kb,23.7kb\n4,green,open,.plugins-ml-config,Qi-idbUDQg-LD85f2lEcAw,1,0,1,0,4kb,4kb\n5,green,open,.plugins-ml-model,U3z4yPgLS6qzaT2zLSXwqw,1,0,48,3,559.6mb,559.6mb\n6,yellow,open,products-index,MzsBIjCDR-G9itfh7Z9b3w,1,1,37,0,17.2kb,17.2kb\n7,green,open,.plugins-ml-agent,rg2u0zVgSBGSIqWjDtY6Qw,1,0,4,0,49.4kb,49.4kb\n8,green,open,.plugins-ml-task,_iNGVCPDS8ua-2EmvB6LNg,1,0,7,0,67.1kb,67.1kb\n9,green,open,.plugins-ml-connector,kEEG4CvuQYeDy3FPD2V0ag,1,0,2,0,36kb,36kb\n"
                },
                {
                    "name": "response",
                    "result": "{\n    \"thought\": \"I have already listed the indices in the previous step, which partially answers the first part of the user's request. Now I need to address 'how does product related index look like' and 'find 1 sample doc'. Based on the index list, 'products-index' is clearly the product-related index. I will use the IndexMappingTool to get its structure (mapping) and then the SearchIndexTool to find a sample document. First, I will get the index mapping for 'products-index'.\",\n    \"action\": \"IndexMappingTool\",\n    \"action_input\": {\n        \"index\": \"products-index\"\n    }\n}"
                },
                {
                    "name": "response",
                    "result": "index: products-index\n\nmappings:\nproperties={average_rating={type=float}, bought_together={type=keyword}, brand={type=text, fields={keyword={type=keyword, ignore_above=256}}}, categories={type=keyword}, category={type=text, fields={keyword={type=keyword, ignore_above=256}}}, color={type=text, fields={keyword={type=keyword, ignore_above=256}}}, currency={type=text, fields={keyword={type=keyword, ignore_above=256}}}, description={type=text}, details={dynamic=false, properties={additional_attributes={type=text}, best_sellers_rank={type=text}, brand={type=keyword}, color={type=keyword}, country={type=keyword}, date_available={type=keyword}, dimensions={type=keyword}, manufacturer={type=keyword}, model_number={type=keyword}, weight={type=keyword}}}, features={type=text}, images={type=object, dynamic=false}, in_stock={type=boolean}, main_category={type=keyword}, parent_asin={type=keyword}, price={type=float}, product_id={type=text, fields={keyword={type=keyword, ignore_above=256}}}, product_name={type=text, fields={keyword={type=keyword, ignore_above=256}}}, rating_number={type=float}, size={type=text, fields={keyword={type=keyword, ignore_above=256}}}, store={type=keyword}, title={type=text, analyzer=standard}, videos={type=object, dynamic=false}}\n\n\nsettings:\nindex.creation_date=1769269102518\nindex.number_of_replicas=1\nindex.number_of_shards=1\nindex.provided_name=products-index\nindex.replication.type=DOCUMENT\nindex.uuid=MzsBIjCDR-G9itfh7Z9b3w\nindex.version.created=137267827\n\n\n"
                },
                {
                    "name": "response",
                    "result": "{\n    \"thought\": \"I have successfully listed all indices and retrieved the mapping for the 'products-index'. Now, I need to find one sample document from the 'products-index' to fully answer the user's request. I will use the SearchIndexTool with a match_all query and limit the results to 1 document.\",\n    \"action\": \"SearchIndexTool\",\n    \"action_input\": {\n        \"index\": \"products-index\",\n        \"query\": \"{\\\"query\\\": {\\\"match_all\\\": {}}, \\\"size\\\": 1}\"\n    }\n}"
                },
                {
                    "name": "response",
                    "result": "Failed to run the tool SearchIndexTool with the error message Expected [START_OBJECT] but found [VALUE_STRING]."
                },
                {
                    "name": "response",
                    "result": "{\n    \"thought\": \"The previous attempt to search for a sample document failed because the `action_input` was not correctly formatted as a stringified object, and the `query` parameter within it might have been incorrectly represented as a string containing a JSON object rather than a direct JSON object. According to the `RESPONSE FORMAT INSTRUCTIONS`, the `action_input` must be a string, which *may be* a stringified object. I will re-attempt to search for a sample document by correctly formatting the `action_input` as a stringified JSON object, where the `query` parameter is an actual OpenSearch DSL JSON object.\",\n    \"action\": \"SearchIndexTool\",\n    \"action_input\": \"{\\\"index\\\":\\\"products-index\\\",\\\"query\\\":{\\\"query\\\":{\\\"match_all\\\":{}},\\\"size\\\":1}}\"\n}"
                },
                {
                    "name": "response",
                    "result": "{\"_index\":\"products-index\",\"_source\":{\"color\":\"white\",\"size\":\"10\",\"price\":140.0,\"product_id\":\"P6001\",\"description\":\"Red cushioned sneakers\",\"currency\":\"USD\",\"in_stock\":true,\"category\":\"shoes\",\"product_name\":\"Nike Air Max white\",\"brand\":\"Nike\"},\"_id\":\"43\",\"_score\":1.0}\n"
                },
                {
                    "name": "response",
                    "result": "Your cluster contains the following indices: .plugins-ml-memory-message, .plugins-ml-model-group, .plugins-ml-memory-meta, .plugins-ml-config, .plugins-ml-model, products-index, .plugins-ml-agent, .plugins-ml-task, and .plugins-ml-connector. The 'products-index' is identified as the product-related index and its mapping includes fields like `average_rating` (float), `brand` (text/keyword), `categories` (keyword), `price` (float), `product_id` (text/keyword), `product_name` (text/keyword), `in_stock` (boolean), `description` (text), and nested `details` for additional attributes. A sample document from the 'products-index' is: {\"_index\":\"products-index\",\"_source\":{\"color\":\"white\",\"size\":\"10\",\"price\":140.0,\"product_id\":\"P6001\",\"description\":\"Red cushioned sneakers\",\"currency\":\"USD\",\"in_stock\":true,\"category\":\"shoes\",\"product_name\":\"Nike Air Max white\",\"brand\":\"Nike\"},\"_id\":\"43\",\"_score\":1.0}."
                }
            ]
        }
    ]
}
```

## Agentic Search Support

This section shows how to configure Gemini models for Agentic Search workflows, which require separate models for the QueryPlanningTool and the conversational agent.

### Step 1: Create Connector for Query Planning Tool

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Google Gemini Connector",
    "description": "Connector for Google Gemini API - Query Planning Tool",
    "version": 1,
    "protocol": "http",
    "parameters": {
        "model": "gemini-2.5-pro",
        "response_filter": "$.candidates[0].content.parts[0].text"
    },
    "credential": {
        "gemini_api_key": "<PLEASE ADD YOUR GEMINI API KEY HERE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent",
            "headers": {
                "Content-Type": "application/json",
                "x-goog-api-key": "${credential.gemini_api_key}"
            },
            "request_body": "{\"systemInstruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt:-You are a helpful assistant.}\"}]},\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.user_prompt}\"}]}]}"
        }
    ]
}
```

#### Sample response
```json
{
  "connector_id": "abc123XYZ"
}
```

### Step 2: Create Model for Query Planning Tool

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "Google Gemini QPT model",
  "function_name": "remote",
  "description": "Google Gemini 2.5 Pro for Query Planning Tool",
  "connector_id": "abc123XYZ"
}
```

#### Sample response
```json
{
    "task_id": "def456XYZ",
    "status": "CREATED",
    "model_id": "ghi789XYZ"
}
```

### Step 3: Create Connector for Agent

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "Google Gemini Connector",
    "description": "Connector for Google Gemini API - ReAct Agent",
    "version": 1,
    "protocol": "http",
    "parameters": {
        "model": "gemini-2.5-pro",
        "response_filter": "$.candidates[0].content.parts[0].text"
    },
    "credential": {
        "gemini_api_key": "<PLEASE ADD YOUR GEMINI API KEY HERE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent",
            "headers": {
                "Content-Type": "application/json",
                "x-goog-api-key": "${credential.gemini_api_key}"
            },
            "request_body": "{\"systemInstruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt:-You are a helpful assistant.}\"}]},\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.prompt}\"}]}]}"
        }
    ]
}
```

#### Sample response
```json
{
  "connector_id": "jkl012XYZ"
}
```

### Step 4: Create Model for ReAct Agent

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "Google Gemini Agent model",
  "function_name": "remote",
  "description": "Google Gemini 2.5 Flash Lite for ReAct Agent",
  "connector_id": "jkl012XYZ"
}
```

#### Sample response
```json
{
    "task_id": "mno345XYZ",
    "status": "CREATED",
    "model_id": "pqr678XYZ"
}
```

### Step 5: Create Agent with QueryPlanningTool

```json
POST /_plugins/_ml/agents/_register
{
    "name": "Gemini Chat Agent",
    "type": "conversational",
    "description": "Chat agent using Google Gemini with ReAct prompting for Agentic Search",
    "app_type": "os_chat",
    "llm": {
        "model_id": "pqr678XYZ",
        "parameters": {
            "max_iteration": 15,
            "message_history_limit": 15,
            "question": "${parameters.user_prompt}"
        }
    },
    "memory": {
        "type": "conversation_index"
    },
    "tools": [
        {
            "type": "SearchIndexTool"
        },
        {
            "type": "QueryPlanningTool",
            "parameters": {
                "model_id": "ghi789XYZ"
            }
        },
        {
            "type": "IndexMappingTool"
        },
        {
            "type": "ListIndexTool"
        }
    ]
}
```

#### Sample response
```json
{
    "agent_id": "stu901XYZ"
}
```

### Step 6: Create Agentic Search Pipeline

```json
PUT /_search/pipeline/my_agentic_search_pipeline
{
    "request_processors": [
        {
            "agentic_query_translator": {
                "agent_id": "stu901XYZ"
            }
        }
    ],
    "response_processors": [
        {
            "agentic_context": {
                "agent_steps_summary": true,
                "dsl_query": true
            }
        }
    ]
}
```

### Step 7: Make Agentic Search Request

```json
POST /_search?search_pipeline=my_agentic_search_pipeline
{
    "query": {
        "agentic": {
            "query_text": "Find top athletic brand shoes"
        }
    }
}
```


