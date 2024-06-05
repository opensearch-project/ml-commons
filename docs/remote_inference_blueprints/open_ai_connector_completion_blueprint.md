### OpenAI connector blueprint example for completion:

#### this blueprint is created from OpenAI doc: https://platform.openai.com/docs/api-reference/completions

```json
POST /_plugins/_ml/connectors/_create
{
    "name": "<YOUR CONNECTOR NAME>",
    "description": "<YOUR CONNECTOR DESCRIPTION>",
    "version": "<YOUR CONNECTOR VERSION>",
    "protocol": "http",
    "parameters": {
        "endpoint": "api.openai.com",
        "max_tokens": 7,
        "temperature": 0,
        "model": "gpt-3.5-turbo-instruct"
    },
    "credential": {
        "openAI_key": "<PLEASE ADD YOUR OPENAI API KEY HERE>"
    },
    "actions": [
        {
            "action_type": "predict",
            "method": "POST",
            "url": "https://${parameters.endpoint}/v1/completions",
            "headers": {
                "Authorization": "Bearer ${credential.openAI_key}"
            },
            "request_body": "{ \"model\": \"${parameters.model}\", \"prompt\": \"${parameters.prompt}\", \"max_tokens\": ${parameters.max_tokens}, \"temperature\": ${parameters.temperature} }"
        }
    ]
}
```

#### Sample response
```json
{
  "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```

### Corresponding Predict request example:

```json
POST /_plugins/_ml/models/<ENTER MODEL ID HERE>/_predict
{
  "parameters": {
    "prompt": "Say this is a test"
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
            "id": "cmpl-7g0NPOJd8IvXTdhecdlR0VGfrLMWE",
            "object": "text_completion",
            "created": 1690245579,
            "model": "gpt-3.5-turbo-instruct",
            "choices": [
              {
                "text": """

                This is indeed a test""",
                "index": 0,
                "finish_reason": "length"
              }
            ],
            "usage": {
              "prompt_tokens": 5,
              "completion_tokens": 7,
              "total_tokens": 12
            }
          }
        }
      ]
    }
  ]
}
```
