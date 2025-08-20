# Topic

> Agentic Memory is available in preview in OpenSearch 3.2. To read more details, see the OpenSearch document [Agentic Memory APIs](https://docs.opensearch.org/latest/ml-commons-plugin/api/agentic-memory-apis/index/).

AI Agents are often stateless by default, meaning they can only retain information within the same conversation but not across different conversations. This limitation prevents them from retaining key information and providing personalized experiences based on past interactions.

Agentic memory in OpenSearch solves this problem by providing agents with the ability to:
- Store memory across conversations and retrieve relevant context via semantic search
- Extract and summarize key facts to improve retrieval accuracy and prevent context overflow
- Update or delete memory when new information contradicts existing memories
- Ensuring sensitive information remains properly secured across different user contexts

# Prerequisites

- OpenSearch version 3.2.0+ with ml-commons plugin installed
- AWS credentials with access to Amazon Bedrock services
- Python 3.10+

# Steps

## 1. Enable Agentic Memory Feature

First, enable the agentic memory feature in your OpenSearch cluster:

```
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.agentic_memory_enabled": "true"
  }
}
```

## 2. Register Embedding Model

In this tutorial, we will use two models: a text-embedding model and a large language model.

First, register an Amazon Titan Text Embeddings V2 model on Bedrock:

```
POST /_plugins/_ml/models/_register
{
  "name": "Bedrock embedding model",
  "function_name": "remote",
  "description": "Embedding model for memory",
  "connector": {
    "name": "embedding",
    "description": "The connector to bedrock Titan embedding model",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "your_aws_region",
      "service_name": "bedrock",
      "model": "amazon.titan-embed-text-v2:0",
      "dimensions": 1024,
      "normalize": true,
      "embeddingTypes": ["float"]
    },
    "credential": {
      "access_key": "your_aws_access_key",
      "secret_key": "your_aws_secret_key",
      "session_token": "your_aws_session_token"
    },
    "actions": [{
      "action_type": "predict",
      "method": "POST",
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "headers": {"content-type": "application/json", "x-amz-content-sha256": "required"},
      "request_body": "{ \"inputText\": \"${parameters.inputText}\", \"dimensions\": ${parameters.dimensions}, \"normalize\": ${parameters.normalize}, \"embeddingTypes\": ${parameters.embeddingTypes} }",
      "pre_process_function": "connector.pre_process.bedrock.embedding",
      "post_process_function": "connector.post_process.bedrock.embedding"
    }]
  }
}
```

## 3. Create LLM for Fact Extraction

Register a Bedrock Claude model for fact extraction:

```
POST /_plugins/_ml/models/_register
{
  "name": "Bedrock infer model",
  "function_name": "remote",
  "description": "LLM model for memory processing",
  "connector": {
    "name": "Amazon Bedrock Connector: LLM",
    "description": "The connector to bedrock Claude 3.7 sonnet model",
    "version": 1,
    "protocol": "aws_sigv4",
    "parameters": {
      "region": "your_aws_region",
      "service_name": "bedrock",
      "max_tokens": 8000,
      "temperature": 1,
      "anthropic_version": "bedrock-2023-05-31",
      "model": "us.anthropic.claude-3-7-sonnet-20250219-v1:0"
    },
    "credential": {
      "access_key": "your_aws_access_key",
      "secret_key": "your_aws_secret_key",
      "session_token": "your_aws_session_token"
    },
    "actions": [{
      "action_type": "predict",
      "method": "POST",
      "headers": {"content-type": "application/json"},
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "request_body": "{ \"system\": \"${parameters.system_prompt}\", \"anthropic_version\": \"${parameters.anthropic_version}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature}, \"messages\": ${parameters.messages} }"
    }]
  }
}
```

## 4. Create Memory Container

Create a memory container, using the two models we created in the previous steps:

```
POST /_plugins/_ml/memory_containers/_create
{
  "name": "memory container",
  "description": "Memory container",
  "memory_storage_config": {
    "llm_model_id": "your_llm_model_id",
    "embedding_model_type": "TEXT_EMBEDDING",
    "embedding_model_id": "your_embedding_model_id",
    "dimension": 1024
  }
}
```

Sample response:
```
{ 'memory_container_id': 'xthvpZgBOh0h20Y9O3Sa', 'status': 'created' }
```

## 5. Implement Strands Agent with Memory Tools

We will now create a memory agent using the [Strands Agents](https://strandsagents.com/latest/) framework and OpenSearch agentic memory.

Implement the agent and its memory tools:

```python
from strands import Agent, tool
import requests
from typing import Dict, List, Optional, Any
from requests.auth import HTTPBasicAuth

OPENSEARCH_URL = "http://localhost:9200"
OPENSEARCH_USERNAME = "your_username"
OPENSEARCH_PASSWORD = "your_password"
CONTAINER_ID = "your_memory_container_id"

def opensearch_request(method: str, endpoint: str, json_data: Optional[Dict] = None) -> requests.Response:
    url = f"{OPENSEARCH_URL}{endpoint}"
    auth = HTTPBasicAuth(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD)
    
    response = getattr(requests, method.lower())(url, json=json_data, auth=auth, verify=False)
    if not response.ok:
        print(f"Error response: {response.status_code} - {response.text}")
    response.raise_for_status()
    return response

@tool
def add_memory(messages: List[Dict[str, str]], session_id: Optional[str] = None, 
               agent_id: Optional[str] = None, tags: Optional[Dict[str, str]] = None, 
               infer: bool = True) -> Dict[str, Any]:
    """Add memory to the container with optional LLM fact extraction."""
    payload = {"messages": messages, "infer": infer}
    if session_id:
        payload["session_id"] = session_id
    if agent_id:
        payload["agent_id"] = agent_id
    if tags:
        payload["tags"] = tags
    
    response = opensearch_request("POST", f"/_plugins/_ml/memory_containers/{CONTAINER_ID}/memories", payload)
    return response.json()

@tool
def search_memories(query: str) -> Dict[str, Any]:
    """Search memories in the container using semantic or keyword search."""
    payload = {"query": query}
    response = opensearch_request("POST", f"/_plugins/_ml/memory_containers/{CONTAINER_ID}/memories/_search", payload)
    return response.json()

@tool
def update_memory(memory_id: str, text: str) -> Dict[str, Any]:
    """Update existing memory content by memory ID."""
    payload = {"text": text}
    response = opensearch_request("PUT", f"/_plugins/_ml/memory_containers/{CONTAINER_ID}/memories/{memory_id}", payload)
    return response.json()

@tool
def delete_memory(memory_id: str) -> Dict[str, Any]:
    """Delete memory by memory ID."""
    response = opensearch_request("DELETE", f"/_plugins/_ml/memory_containers/{CONTAINER_ID}/memories/{memory_id}")
    return response.json()

def main():
    agent = Agent(
        tools=[add_memory, search_memories, update_memory, delete_memory],
        system_prompt="You are a memory assistant that can add, search, update, and delete memories."
    )
    
    print(f"Memory Agent initialized with container ID: {CONTAINER_ID}")
    while True:
        user_input = input("\n> ")
        if user_input.lower() == 'exit':
            break
        try:
            agent(user_input)
        except Exception as e:
            print(f"Error: {e}")

if __name__ == "__main__":
    main()
```

## 6. Using the Memory Agent

The following example demonstrates how agentic memory enables context to persist across different conversation sessions, remembers user corrections, and uses stored preferences to provide personalized recommendations.


### Session 1: Initial Information Storage
```
> remember that my name is alex and I am software engineer at AWS
I'll add this information to your memory.
Tool #1: add_memory
Perfect! I've successfully stored your information in memory. I now remember that:
- Your name is Alex
- You are a software engineer at AWS

> I like listening to music
I'll add this preference to your memory.
Tool #2: add_memory
Great! I've added your music preference to memory. Now I remember that you like listening to music, along with your other information.

> i like hiking and travelling
I'll add this information about your interests to memory for you.
Tool #3: add_memory
Great! I've successfully added your interests to memory. The system extracted and stored two key facts:
1. You enjoy hiking
2. You enjoy traveling

This information is now saved and can be retrieved later when relevant to future conversations. Is there anything else about your hobbies or interests you'd like me to remember?

> i like foods of all kind
I'll add this information about your food preferences to memory.
Tool #4: add_memory
Perfect! I've added your food preferences to memory. The system has stored:
1. You like foods of all kinds

This gives a good picture of your dietary preferences - you're open to trying different cuisines. This information could be helpful for future conversations about travel destinations, restaurant recommendations, or meal planning!
```

### Session 2: Context Retrieval and Updates
```
> what do you know about me
I'll search through your memories to see what information I have about you.
Tool #1: search_memories
Based on the memories I have stored about you, here's what I know:

**Personal Information:**
- Your name is Alex
- You work as a software engineer at AWS
- You enjoy listening to music
- You enjoy hiking
- You enjoy traveling
- You like foods of all kinds

> actually my name is brian.
I'll update your name from Alex to Brian in my memory system.
Tool #2: update_memory
Got it! I've updated my records. Your name is Brian, not Alex. 

> help me plan a weekend trip
Let me first search for what I know about your preferences to help plan your weekend trip.
Tool #3: search_memories
Perfect! Based on your preferences, I can see that you enjoy:
- Hiking and traveling
- Foods of all kinds

Here's a weekend trip plan tailored to your interests:

## Weekend Trip Suggestions

### Mountain/Nature Destination (Perfect for hiking + dining)
**Location Options:**
- Asheville, North Carolina
- Boulder, Colorado
- Sedona, Arizona

### Restaurant Recommendations (since you like all kinds of food):
**Fine Dining:**
- The Admiral (seafood & contemporary American)
- Rhubarb (farm-to-table with meat options)

**Casual/Local Favorites:**
- Buxton Hall Barbecue (amazing BBQ and smoked meats)
- White Duck Taco Shop (creative tacos with meat options)

Would you like me to adjust this plan for a different location?
```

# Conclusion

OpenSearch agentic memory provides a powerful foundation for building context-aware AI agents. By combining semantic search and automatic fact extraction, agents can deliver increasingly personalized and intelligent interactions over time.
