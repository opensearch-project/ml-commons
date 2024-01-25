### Sagemaker connector blueprint example for embedding:

Read more details on https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/

Make sure your Sagemaker model input follow such format, so the [default pre-process function](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/#preprocessing-function) can work
```
["hello world", "how are you"]
```
and output follow such format, so the [default post-process function](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/#post-processing-function) can work
```
[
  [
    -0.048237994,
    -0.07612697,
    ...
  ],
  [
    0.32621247,
    0.02328475,
    ...
  ]
]
```

Then, you can create Sagemaker embedding model with default pre/post process function:
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "<YOUR CONNECTOR NAME>",
  "description": "<YOUR CONNECTOR DESCRIPTION>",
  "version": "<YOUR CONNECTOR VERSION>",
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION TOKEN HERE>",
    "service_name": "sagemaker"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "content-type": "application/json"
      },
      "url": "<PLEASE ADD YOUR Sagemaker MODEL INFERENCE ENDPOINT URL>",
      "request_body": "${parameters.inputs}",
      "pre_process_function": "connector.pre_process.default.embedding",
      "post_process_function": "connector.post_process.default.embedding"
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

