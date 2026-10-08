### OpenAI connector blueprint example for completion:

> [!WARNING]
> **`gpt-3.5-turbo-instruct` is retired, so this blueprint no longer works as written.** OpenAI
> announced the shutdown of the legacy GPT model snapshots on 2025-09-26 with a shutdown date of
> 2026-09-28, which has now passed. The remaining legacy completions alias
> (`gpt-3.5-turbo-completions`) shuts down on 2026-10-23. See
> [OpenAI deprecations](https://developers.openai.com/api/docs/deprecations).
>
> Migrate to [open_ai_connector_chat_blueprint.md](open_ai_connector_chat_blueprint.md), which
> targets `/v1/chat/completions`. This repository's integration tests use `gpt-4o-mini`, which
> accepts the same `max_tokens` and `temperature` parameters shown below.
>
> OpenAI lists `gpt-5.6-terra` as the substitute for `gpt-3.5-turbo-instruct`, but it is not a
> drop-in replacement for the request body below: it rejects `max_tokens` (requiring
> `max_completion_tokens`) and rejects a `temperature` override.
>
> The example below is retained for reference.

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
