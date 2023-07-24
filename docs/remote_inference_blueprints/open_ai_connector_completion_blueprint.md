```json
{
    "name": "OpenAI Connector",
    "description": "The connector to public OpenAI model service for completions",
    "version": 1,
    "protocol": "http",
    "parameters": {
        "endpoint": "api.openai.com",
        "max_tokens": 7,
        "temperature": 0,
        "model": "text-davinci-003"
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
