# Aleph Alpha connector blueprint example for luminous-base embedding model
This is an AI connector blueprint for the [Aleph Alpha Luminous-Base Embedding Model](https://docs.aleph-alpha.com/products/pharia-ai/pharia-os/references/inference/endpoints/semantic_embed/).
This model is particularly effective for German language applications, providing nuanced and contextually relevant embeddings. 

## 1. Add connector endpoint to trusted URLs:

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://api\\.aleph-alpha\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for Aleph Alpha:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Aleph Alpha Connector: luminous-base, representation: document",
  "description": "The connector to the Aleph Alpha luminous-base embedding model with representation: document",
  "version": 1,
  "protocol": "http",
  "parameters": {
    "endpoint": "api.aleph-alpha.com",
    "representation": "document",
    "normalize": true
  },
  "credential": {
    "AlephAlpha_API_Token": "<PLEASE ADD YOUR ALEPH ALPHA API TOKEN HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
	  "url": "https://${parameters.endpoint}/semantic_embed",
      "headers": {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Authorization": "Bearer ${credential.AlephAlpha_API_Token}"
      },
      "request_body": "{ \"model\": \"luminous-base\", \"prompt\": \"${parameters.input}\", \"representation\": \"${parameters.representation}\", \"normalize\": ${parameters.normalize}}",
      "pre_process_function": "\n    StringBuilder builder = new StringBuilder();\n    builder.append(\"\\\"\");\n    String first = params.text_docs[0];\n    builder.append(first);\n    builder.append(\"\\\"\");\n    def parameters = \"{\" +\"\\\"input\\\":\" + builder + \"}\";\n    return  \"{\" +\"\\\"parameters\\\":\" + parameters + \"}\";",
      "post_process_function": "\n      def name = \"sentence_embedding\";\n      def dataType = \"FLOAT32\";\n      if (params.embedding == null || params.embedding.length == 0) {\n        return params.message;\n      }\n      def shape = [params.embedding.length];\n      def json = \"{\" +\n                 \"\\\"name\\\":\\\"\" + name + \"\\\",\" +\n                 \"\\\"data_type\\\":\\\"\" + dataType + \"\\\",\" +\n                 \"\\\"shape\\\":\" + shape + \",\" +\n                 \"\\\"data\\\":\" + params.embedding +\n                 \"}\";\n      return json;\n    "
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "bRa3QI0BWgGoN0Ye9K2u"
}
```

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group",
    "description": "This is an example description"
}
```

Sample response:
```json
{
  "model_group_id": "XRbIP40BWgGoN0YeZq1V",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register
{
  "name": "Luminous-base embedding model",
  "function_name": "remote",
  "model_group_id": "XRbIP40BWgGoN0YeZq1V",
  "description": "embedding model, representation: document",
  "connector_id": "bRa3QI0BWgGoN0Ye9K2u"
}
```

Sample response:
```json
{
  "task_id": "r6R9PIsBQRofe4CSlUoG",
  "status": "CREATED"
}
```
Get model id from task
```json
GET /_plugins/_ml/tasks/r6R9PIsBQRofe4CSlUoG
```
Deploy model, in this demo the model id is `sKR9PIsBQRofe4CSlUov`
```json
POST /_plugins/_ml/models/sKR9PIsBQRofe4CSlUov/_deploy
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/sKR9PIsBQRofe4CSlUov/_predict
{
  "parameters": {
    "input": "Test string with German characters: Moët Hennessy"
  }
}
```

Sample response:
```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            5120
          ],
          "data": [
            -0.012756348,
            0.001159668,
            0.0025634766,
            ...
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

