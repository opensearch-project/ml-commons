# conversational-search-processors
OpenSearch search processors providing conversational search capabilities
=======
# Plugin for Conversations Using Search Processors in OpenSearch
This repo is a WIP plugin for handling conversations in OpenSearch ([Per this RFC](https://github.com/opensearch-project/ml-commons/issues/1150)).

Conversational Retrieval Augmented Generation (RAG) is implemented via Search processors that combine user questions and OpenSearch query results as input to an LLM, e.g. OpenAI, and return answers.

## Creating a search pipeline with the GenerativeQAResponseProcessor

```
PUT /_search/pipeline/<search pipeline name>
{
  "response_processors": [
    {
        "retrieval_augmented_generation": {
            "tag": <tag>,
            "description": <description>,
            "model_id": "<model_id>",
            "context_field_list": [<field>] (e.g. ["text"])
        }
    }
  ]
}
```

The 'model_id' parameter here needs to refer to a model of type REMOTE that has an HttpConnector instance associated with it.

## Making a search request against an index using the above processor
```
GET /<index>/_search\?search_pipeline\=<search pipeline name>
{
  "_source": ["title", "text"],
  "query" : {
    "neural": {
      "text_vector": {
         "query_text": <query string>,
         "k": <integer> (e.g. 10),
         "model_id": <model_id>
      }
    }
  },
  "ext": {
      "generative_qa_parameters": {
        "llm_model": <LLM model> (e.g. "gpt-3.5-turbo"),
        "llm_question": <question string>
      }
  }
}
```

## Retrieval Augmented Generation response
```
{
  "took": 3,
  "timed_out": false,
  "_shards": {
    "total": 3,
    "successful": 3,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 110,
      "relation": "eq"
    },
    "max_score": 0.55129033,
    "hits": [
      {
       "_index": "...",
        "_id": "...",
        "_score": 0.55129033,
        "_source": {
          "text": "...",
          "title": "..."
        }
      },
      {
      ...
      }
      ...
      {
      ...
      }
    ]
  }, // end of hits
  "ext": {
    "retrieval_augmented_generation": {
      "answer": "..."
    }
  }
}
```
The RAG answer is returned as an "ext" to SearchResponse following the "hits" array.
