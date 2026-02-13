# Background

## V1: Prompt engineering to mimic function calling
We have released agent framework long time ago. By that time, the function calling was not mature. 
So we used prompt engineering to build function calling. We ask LLM to return response with a formated json
You can refer to the template defined in PromptTemplate.java file.
Example: 
```json
{
  "thought": "Now I need to use ListIndexTool to get all indices",
  "action": "ListIndexTool",
  "action_input": ""
}
```

User need to create complicated and customized LLM with our ml-commons connector: 

1. create connector
```json
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
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/anthropic.claude-instant-v1/invoke",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{\"prompt\":\"${parameters.prompt}\", \"max_tokens_to_sample\":${parameters.max_tokens_to_sample}, \"temperature\":${parameters.temperature},  \"anthropic_version\":\"${parameters.anthropic_version}\" }"
    }
  ]
}
```
2. Create model
```json
POST /_plugins/_ml/models/_register
{
    "name": "Bedrock Claude Instant model",
    "function_name": "remote",
    "description": "Bedrock Claude instant-v1 model",
    "connector_id": "your_LLM_connector_id"
}
```
Test model with predict 
```json
POST /_plugins/_ml/models/your_LLM_model_id/_predict
{
  "parameters": {
    "prompt": "\n\nHuman: how are you? \n\nAssistant:"
  }
}
```

3. Create agent
```json
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
We only support one memory type for this phase : "type": "conversation_index". Which we store conversation
message and tool input/output into a local opensearch system index. 

Sampe response
```
{
  "agent_id": "fQ75lI0BHcHmo_czdqcJ"
}
```

4. Run Agent: start a new conversation
```json
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "what's the population increase of Seattle from 2021 to 2023?"
  }
}
```
Sampe response
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
5. Continue a conversation
```json
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
```json
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

You can also customize which tool to use in the Predict API. For example, if you want to translate the previous answer into Chinese, you don't need to retrieve data from knowledge base. Use selected_tools to just run the bedrock_claude_model.

Note: The agent will run the tools sequentially in the new order defined in selected_tools.
```json
POST /_plugins/_ml/agents/your_agent_id/_execute
{
  "parameters": {
    "question": "Translate last answer into Chinese?",
    "selected_tools": ["bedrock_claude_model"]
  }
}
```

## V2: Integrate with LLM function calling

Later, we see LLM function calling is mature. We enhanced the interface to support function calling.

We register model this way. This part is almost same as before. Here we just create connector inside
model, this way also supported in V1. 
The enhancement is we make the request_boday more structured. 

1. Create LLM
```json
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
      "region": "us-west-2",
      "service_name": "bedrock",
      "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0"
    },
    "credential": {
      "access_key": "{{access_key}}",
      "secret_key": "{{secret_key}}"
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
Then we can predict with 
```json
POST _plugins/_ml/models/your_model_id/_predict
{
    "parameters": {
        "system_prompt": "You are a helpful assistant.",
        "prompt": "hello"
    }
}
```

2. Create Agent
```json
POST _plugins/_ml/agents/_register
{
  "name": "RAG Agent",
  "type": "conversational",
  "description": "this is a test agent",
  "app_type": "rag",
  "llm": {
    "model_id": "mRIVN5wBHMiVeAIOEXj3",
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
                "model_id": "w0T3NJwBZFG13462QeiT"
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
                "model_id": "w0T3NJwBZFG13462QeiT"
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
You can see the enhancment: We add a new `_llm_interface` to `parameters` block, and the value `"_llm_interface": "bedrock/converse/claude"` 
will tell ml-commons code how to hande the function calling for the model. You can read MLChatAgentRunner class for detail logic.
And you can see we add input_schema to tool definition which required for function calling.

## V3: Unified agent interface

From V1, and V2 we can see some common pain point: agent interface (input/output) is not standardized. 
So if user want switch to a new model, they have to change the request_body and the _llm_interface. 
We read  Strands agent python SDK code, we see they maintain a standard agent interface: str | content blocks | message list. 
And Strands always store the conversation history as a standard message block defined by itself. 
To make it comptible with other LLMs, it maintains model provider for each LLM, to adapt the input and output. 
So user can switch LLM easily, the agent will read the Strnad's standard formated message from session history,
then use model provider to convert it to target LLM format. After receiving LLM response, it convert their response
into Strands standard message format and persisted.

Another difference is the V1 and V2 we only load human message and LLM final answer to chat history.
Not include the tool useage and tool result messages. But Strands will include all. That's something we also want to update.

After V2, we built agentic memory feature in ml-commons. The basic usage:
1. User create a memory container
```json
POST _plugins/_ml/memory_containers/_create
{
  "name": "agentic memory test",
  "description": "Store conversations with semantic search and summarization"
}
```
2. Client (for example the chat agent) can persist their conversation history to memory container's working/short-term memory:
The memory will be stored into such local index with mapping
```json
{
  "_meta": {
    "schema_version": 2
  },
  "properties": {
    "owner_id": {
      "type": "keyword"
    },
    "memory_container_id": {
      "type": "keyword"
    },
    "payload_type": {
      "type": "keyword"
    },
    "messages": {
      "type": "nested",
      "properties": {
        "role": {
          "type": "keyword"
        },
        "content": {
          "type": "flat_object"
        }
      }
    },
    "message_id": {
      "type": "integer"
    },
    "checkpoint_id": {
      "type": "keyword"
    },
    "binary_data": {
      "type": "binary"
    },
    "structured_data": {
      "type": "flat_object"
    },
    "structured_data_blob": {
      "type": "object",
      "enabled": false
    },
    "namespace": {
      "type": "flat_object"
    },
    "namespace_size": {
      "type": "integer"
    },
    "metadata": {
      "type": "flat_object"
    },
    "tags": {
      "type": "flat_object"
    },
    "parameters": {
      "type": "flat_object"
    },
    "infer": {
      "type": "boolean"
    },
    "created_time": {
      "type": "date",
      "format": "strict_date_time||epoch_millis"
    },
    "last_updated_time": {
      "type": "date",
      "format": "strict_date_time||epoch_millis"
    },
    "owner": USER_MAPPING_PLACEHOLDER,
    "tenant_id": {
      "type": "keyword"
    }
  }
}
```
To support multi-modal, we suggest use store the message in `structured_data_blob` field which not indexed.
And store other searchable information to `metadata` , also do isolation in namespace. for example
```
"namespace": {
    "agent_id": "abc123",
    "session_id": "test_session_123"
}
```
### Current PoC
Pavan created an RFC for a unified agent interface and completed a POC (which adds a new input class, e.g., AgentInput, and updates the agent runner, e.g., ChatAgentRunner). We believe the RFC and POC could be enhanced in the following areas:

#### 1. not support multi-modal
@pyek-bot I know we have some limitations in conversation memory for storing multi-modal data. But I think we can store it in agentic memory. I tested the code in the 3.5 branch, and it seems the current implementation does not store multi-modal data in agentic memory either.
```
POST _plugins/_ml/agents/{{agent_id}}/_execute
{
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

See the memory only stores the text , not including the image.
```
{
  "payload_type": "conversational",
  "created_time": 1770163121412,
  "metadata": {
    "type": "message"
  },
  "structured_data_blob": {
    "input": """What's in this image?
""",
    "updated_time": "2026-02-03T23:58:44.137092705Z",
    "create_time": "2026-02-03T23:58:41.411825258Z",
    "additional_info": {},
    "response": """The image shows the text "Hello World!!" in a light gray, monospaced font on a dark background.""",
    "final_answer": true
  },
  "last_updated_time": 1770163124138,
  "infer": false,
  "namespace_size": 1,
  "namespace": {
    "session_id": "SqjxJZwBA915NIzsrDH1"
  },
  "memory_container_id": "8qjvJZwBA915NIzsjSuG"
}
```

**Suggestion**: Conversation memory has limitations. It only supports text input by default, making it hard to support content blocks/messages How about we just support agentic memory for this new interface, as conversation memory is on the deprecation path?

Suggest store in structured_data_blob.
```
"structured_data_blob": {
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
"data": "iVBORw0KGgoAA"
}
}
],
"last_updated_time": 1770163124138,
....
}
```

The [InputType](https://github.com/opensearch-project/ml-commons/blob/main/common/src/main/java/org/opensearch/ml/common/input/execute/agent/InputType.java) enum has three values: TEXT, CONTENT_BLOCKS, and MESSAGES. I think we should also add the input type to the `metadata` field. Then later, when we retrieve historical messages, we will know how to convert the input to the correct LLM message format for each model provider

#### Current code becomes more and more complex, suggest use version based solution
After reviewing the discussion, I'd like to propose an alternative approach that prioritizes clean separation over complex conditional logic.

The current challenge is:
`conversation_index` memory only supports text storage. The new unified agent interface supports multi-modal input (content blocks, messages). We need backward compatibility for existing agents which is challenging to support multi-modal input.

If you read the current ChatAgentRunner.java , it's really complex because it has V1, V2, and V3 logics together in one class.
Since function calling is mature, we should consider deprecate V1 code at least, and gradually deprecate V2. but since all code in
one place , it's very challenging.

### Proposed Approach: Version-based separation

#### For existing agents:
Support only plain text with the new input field:
```
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": "what tools do you have access to?"
}
```
This maintains full backward compatibility - existing agents continue to work with conversation_index memory.

#### For new agents wanting full multi-modal support:
Introduce a versioned agent type, and it only supports `agentic_memory`
```
POST /_plugins/_ml/agents/_register
{
  "name": "Claude 3.7 Agent",
  "type": "conversational/v2",
  "description": "Agent with full multi-modal support",
  "model": {
    "model_id": "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
    "model_provider": "bedrock/converse",
    "credential": {...}
  },
  "tools": [...],
  "memory": {
    "type": "agentic_memory"
  }
}
```

This option don't need to care complex backward compatibility issue, code complexity is low and clear opt-in to new features.

# Appendix: Strands python SDK Research

## Sample code
```python
import asyncio
import os

from strands import Agent
from strands.models import BedrockModel
from strands.session import FileSessionManager

# Load local image bytes
def load_image_bytes(path: str) -> bytes:
    with open(path, "rb") as f:
        return f.read()

async def main():
    # 1) Model — make sure this is a multimodal-capable model
    model = BedrockModel(
        # example: use a vision-capable model if available
        model_id="us.anthropic.claude-3-7-sonnet-20250219-v1:0"
    )

    # 2) Session manager (optional if you want persistent history)
    session_dir = "./multimodal_sessions"
    os.makedirs(session_dir, exist_ok=True)

    session_manager = FileSessionManager(
        session_id="multimodal_test",
        storage_dir=session_dir
    )

    # 3) Build agent
    agent = Agent(
        model=model,
        session_manager=session_manager,
        system_prompt="You will describe images and respond appropriately."
    )

    # 4) Prepare a multimodal content list
    image_bytes = load_image_bytes("example.png")  # your image path

    multimodal_prompt = [
        {"text": "Here is an image, please describe it:"},
        {
            "image": {
                # format is required per Strands ContentBlock spec
                "format": "png",
                "source": {"bytes": image_bytes}
            }
        },
        {"text": "What do you see in this image?"}
    ]

    print("\nSending multimodal content to agent...\n")

    # 5) Invoke the agent
    response = agent(multimodal_prompt)

    # 6) Print the response
    print("Agent response:")
    print(response)

    # 7) Inspect what’s stored in session history
    print("\nConversation history:")
    for msg in agent.messages:
        print(msg["role"], msg["content"])

if __name__ == "__main__":
    asyncio.run(main())
```

## Persisted message

After running the sample code, we can see messages persisted to local file system
```
multimodal_sessions
└── session_multimodal_test
    ├── agents
    │   └── agent_default
    │       ├── agent.json
    │       └── messages
    │           ├── message_0.json
    │           └── message_1.json
    ├── multi_agents
    └── session.json
```
message json file content
```
# message 0
{
  "message": {
    "role": "user",
    "content": [
      {
        "text": "Here is an image, please describe it:"
      },
      {
        "image": {
          "format": "png",
          "source": {
            "bytes": {
              "__bytes_encoded__": true,
              "data": "iVBORw..."
            }
          }
        }
      },
      {
        "text": "What do you see in this image?"
      }
    ]
  },
  "message_id": 0,
  "redact_message": null,
  "created_at": "2026-02-06T08:40:20.053028+00:00",
  "updated_at": "2026-02-06T08:40:20.053034+00:00"
}

# message 1
{
  "message": {
    "role": "assistant",
    "content": [
      {
        "text": "This image shows a fresh, vibrant red strawberry placed on what appears to be a light-colored surface, possibly wood or stone. The strawberry has its characteristic textured surface with tiny seeds embedded in the flesh and still has its green leafy cap (calyx) attached. The background is a soft, blurred green, creating a pleasant natural bokeh effect that helps the strawberry stand out as the main focus of the image. The strawberry looks perfectly ripe and juicy, showcasing its bright color and appealing texture in this close-up shot."
      }
    ]
  },
  "message_id": 1,
  "redact_message": null,
  "created_at": "2026-02-06T08:40:23.950911+00:00",
  "updated_at": "2026-02-06T08:40:23.950916+00:00"
}
```

# Appendix: Pavan'S RFC
## Summary

This RFC proposes a significant simplification of the agent creation and execution interface in OpenSearch ML-Commons. The current process requires multiple complex steps to create a functional agent, including manually registering connectors, models, and agents separately. This proposal aims to streamline the entire workflow into a single, intuitive API call while also enhancing the agent execution API to support multi-modal inputs and standardized content block formats.

## Motivation

Using and setting up agents in OpenSearch is complicated and requires multiple error-prone steps. As AI-driven workflows grow more complex, there is a significant need to simplify the agent creation and usage process.

## Current Pain Points

Today, in order to create a simple chat agent, users need to follow these steps:

1. **Register Connector** - Users must carefully craft complex JSON payloads including error-prone `request_body` and `url` parameters
2. **Register Model** - Requires manually noting down `connector_id` from step 1 (redundant step since we already know a connector was created)
3. **Register Agent** - Requires `model_id`, manual `_llm_interface` configuration, and proper `question` to `prompt` mapping
4. **Execute Agent** - Tightly coupled to `question` parameter with no multi-modal support

For MCP (Model Context Protocol) tools, additional steps are required:
- Enable cluster settings to allow MCP
- Register MCP connector separately
- Reference MCP connector in agent registration

## Current Workflow

### 1. Register Connector
```
POST /_plugins/_ml/connectors/_register
{
    "name": "My Claude3.7 connector",
    "description": "my test connector",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "us-east-1",
      "service_name": "bedrock",
      "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0"
    },
    "credential": {
      "access_key": "{{ _.access_key }}",
      "secret_key": "{{ _.secret_key }}",
      "session_token": "{{ _.session_token }}"
    },
    "actions": [
      {
        "action_type": "predict",
        "method": "POST",
        "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
        "headers": {
          "content-type": "application/json"
        },
        "request_body": "{\"system\": [{\"text\": \"${parameters.system_prompt}\"}], \"messages\": [${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.prompt}\"}]}${parameters._interactions:-}]${parameters.tool_configs:-} }"
      }
    ]
  }
```

Here users need to carefully tune and provide the necessary parameters especially the error prone `request_body` and `url`. This can be easily identified by the provided `model`.

### 2. Register Model
Following connector creation, users need to note down the `connector_id` and setup a model.
```
POST /_plugins/_ml/models/_register
{
  "name": "agent model",
  "function_name": "remote",
  "description": "<>",
  "connector_id": "<>"
}
```

### 3. Register Agent

Finally, using the `model_id` generated users can register Agents.
Agents can be registered primarily in 2 ways (with & without MCP server)

### 3.1 Register Agent With tools (without MCP)
```
POST /_plugins/_ml/agents/_register
{
  "name": "Claude 3.7 without t2ppl",
  "type": "conversational",
  "description": "this is a test agent",
  "llm": {
    "model_id": "<>",
    "parameters": {
      "max_iteration": 50,
      "system_prompt": "Assistant is a large language model. Assistant is designed to be able to assist with a wide range of tasks, from answering simple questions to providing in-depth explanations and discussions on a wide range of topics. As a language model.  Assistant is able to generate human-like text based on the input it receives, allowing it to engage in natural-sounding conversations and provide responses that are coherent and relevant to the topic at hand.",
      "prompt": "${parameters.question}"
    }
  },
  "tools": [
    {
      "type": "ListIndexTool"
    },
    {
      "type": "SearchIndexTool"
    },
    {
      "type": "IndexMappingTool"
    }
  ],
  "parameters": {
    "_llm_interface": "bedrock/converse/claude"
  },
  "memory": {
    "type": "conversation_index"
  }
}
```

Here, users need to again provide an `llm` block with the `model_id` and any necessary parameters. It is critical in the agent framework to ensure that the prompt in the `request_body` of the connector is properly mapped to `question` as this is the only input that is accepted.

Additionally, users need to provide the `_llm_interface` depending on the model and provider used to support function calling.

A lot of the parameters are redundant and error prone. Therefore there is a significant need to revamp the agent creation flow.

Once an agent is registered, users need to note down the `agent_id` and can execute the agent.

### 3.2 Register Agent with MCP Tools

If user wants to register an agent with MCP tools, this adds additional steps.

#### 3.2.1 Enable settings to support MCP
```
PUT /_cluster/settings/
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "<mcp server url>"
    ],
    "plugins.ml_commons.mcp_connector_enabled": "true"
  }
}
```

#### 3.2.2 Register MCP Connector
```
POST /_plugins/_ml/connectors/_create
{
  "name":        "My MCP Connector",
  "description": "Connects to the external MCP server for weather tools",
  "version":     1,
  "protocol":    "mcp_sse",
  "url":         "https://my-mcp-server.domain.com",
  "credential": {
    "mcp_server_key": "THE_MCP_SERVER_API_KEY"
  },
  "parameters":{
    "sse_endpoint": "/sse" 
  },
  "headers": {
    "Authorization": "Bearer ${credential.mcp_server_key}"
  }
}
```

Note down the MCP `connector_id`

#### 3.2.3 Register Agent with MCP Connector
```
POST /_plugins/_ml/agents/_register
{
  "name": "PER Claude 3.7 (SearchIndex)",
  "type": "plan_execute_and_reflect",
  "description": "this is a test agent",
  "llm": {
    "model_id": "<>",
    "parameters": {
      "prompt": "${parameters.question}"
  }},
  "memory": {
    "type": "conversation_index"
  },
  "parameters": {
    "_llm_interface": "bedrock/converse/claude",
        "mcp_connectors": [
      {
        "mcp_connector_id": "<>"
      },
      {
        "mcp_connector_id": "<>"
      }
    ]
  },
  "app_type": "os_chat"
}
```

This adds another 2 steps to an already complicated process. Therefore, we should simplify the MCP creation as well.

### 4. Execute Agent
```
POST /_plugins/_ml/agents/{{agent_id}}/_execute
{
    "parameters": {
        "question": "hi"
    }
}
```

The `_execute` API is tightly coupled with `question` parameter. This is the only way to pass an input to the agent.

Additionally, this API does not easily support multi modal data and requires changes at the connector level. It also does not support messages and content block style input. Nor does it support AG-UI directly.

Therefore, this RFC recommends a new standard set of APIs to register and execute agents.

## Goals

1. Simplify Agent Creation: Automatically create connector & model in a single agent register call
2. Remove Redundant Parameters: Eliminate _llm_interface, mapping of question to prompt & provide standard inputs for model providers
3. Proper Validation: Add validation for supported model providers
4. Backward Compatibility: Ensure full backward compatibility with existing APIs
5. Simplify MCP Registration: Streamline MCP tool registration process
6. Enhanced Execution API: Support multi-modal input, content block style input, and messages style input
7. Remove Input Limitations: Remove the restriction on providing question as the only input
8. AG-UI Support: Standard support for AG-UI protocol
9. Multi-modal input: Ensure multi-modal input is supported out of the box and stored within memory

## Design

### Simplified Agent Registration

Replace the current multi-step connector/model creation with a unified model block in agent registration by introducing a new `model` block schema

```
{
  "model": {
    "model_id": "string",           // Required: Provider's model identifier
    "model_provider": "string",     // Required: e.g., "bedrock/converse"
    "credential": {},                 // Required: Provider-specific credentials
    "model_parameters": {           // Optional: Model-specific parameters
      "temperature": 0.7,
      "max_tokens": 4096
    }
  }
}
```

When the new model block is provided, the system will automatically:
1. Create a model group (.plugins-ml-model-group)
2. Create an inline connector with proper configuration
3. Create and register the model (.plugins-ml-model)
4. Create the agent with proper llm block referencing the auto-generated model
5. Auto-configure _llm_interface based on model_provider

### Enhanced Execute API
The `_execute` API will support 3 types of inputs via a new `input` field:

1. Plain text: Simple string input
2. Multi-modal: Content blocks with text, images, video, documents
3. Messages + Multi-modal: Role-based messages with content blocks

Internally, depending on the model provider and model_id, the input will be parsed to the required format before forwarding the request to the remote service.

### Features

1. Automatic Resource Management: Connector and model creation handled automatically
2. Multi-modal Support: Images, video, and documents as input
3. Messages Style Input: Full conversation history with role-based messages
4. Content Block Format: Following Bedrock Converse API style
5. Backward Compatibility: Existing APIs continue to work unchanged

## API Definition

### 1. Register Agent
```
POST /_plugins/_ml/agents/_register
{
  "name": "Claude 3.7 without t2ppl",
  "type": "conversational",
  "description": "this is a test agent",
  "model": {
    "model_id": "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
    "model_provider": "bedrock/converse",
    "credential": {
      "access_key": "YOUR_ACCESS_KEY",
      "secret_key": "YOUR_SECRET_KEY",
      "session_token": "YOUR_SESSION_TOKEN"
    }
  },
  "tools": [
    {
      "type": "ListIndexTool"
    },
    {
      "type": "SearchIndexTool"
    },
    {
      "type": "IndexMappingTool"
    }
  ],
  "memory": {
    "type": "conversation_index"
  }
}
```

### 2. Execute Agent

#### 2.1 Plain Text
```
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": "what tools do you have access to?"
}
```

#### 2.2 Content blocks
```
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": [
    {
      "type": "text",
      "text": "what can you see in this new image?"
    },
    {
      "type": "image",
      "source": {
        "type": "base64",
        "format": "png",
        "data": "iVBORw0KGgoAAAANSUhEUgAAAG4AAAAeCAYAAADNeSs6..."
      }
    }
  ]
}
```

**Supported Content Types:**
- **`text`**: Plain text content
- **`image`**: Image content
    - Source types: `base64`, `url`
- **`video`**: Video content
    - Source types: `base64`, `url`
- **`document`**: Document content
    - Source types: `base64`, `url`

#### 2.3 Execute Agent - Messages with Content Blocks
```
POST /_plugins/_ml/agents/{agent_id}/_execute
{
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "I like red"
        }
      ]
    },
    {
      "role": "assistant",
      "content": [
        {
          "type": "text",
          "text": "Thanks for telling me that! I'll remember it."
        }
      ]
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "What colour do I like?"
        }
      ]
    }
  ]
}
```

Each message will be stored into memory. Each content object also supports multi-modal.

## Backward Compatibility

The existing APIs and parameters will continue to work:

1. llm.model_id: Still supported for pre-registered models
2. parameters.question: Still supported in _execute API
3. _llm_interface: Still supported for manual configuration
4. Existing connector/model registration flow: Unchanged
5. The new model block and input field are additive enhancements that will work when the agent is registered using the new APIs

## Scope & Phasing

### Phase 1
1. Simplify the agent creation by removing the redundant model & connector creation
2. Standardize the input for agent execution
3. Only support conversational and plan_execute_and_reflect (PER) agent types
4. Support only Claude Bedrock Converse models

Limitations:
1. PER will not support messages style input as it currently uses a large prompt
   Dependent on https://github.com/opensearch-project/ml-commons/issues/4392
2. Multi modal content will not be stored when `conversation_index` memory is used as it is a limitation of that memory type.

### Phase 2
1. Defaults:
- Add defaults for tools (SearchIndexTool, ListIndexTool, IndexMappingTool)
- Set conversational agent as default agent type
- Choose memory by default (conversation_index)
- Need to discuss how to support agentic_memory by default as it requires setup of memory containers
2. Simplify MCP creation: Remove the need for creating an MCP connector and support that within the agent registration
3. Support `flow` and `conversational_flow` agents
4. Support messages style in PER agent
5. Fully support Agentic memory to store multi modal data

### Phase 3
1. Create inline model document inside agent document to prevent duplicates
2. Look into a quick `execute` API to run agents by allowing user to provide credentials (Quick start)

Looking forward to any feedback, alternative designs and refinements.