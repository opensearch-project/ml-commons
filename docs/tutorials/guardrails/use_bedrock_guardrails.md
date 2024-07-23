# Topic

> Remote model guardrails has been released in OpenSearch 2.15. For more information, see [Guardrails](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/guardrails/).

This tutorial demonstrates how to apply your Amazon Bedrock guardrails to your externally hosted models in two ways.

Note: Replace the placeholders starting with the prefix `your_` with your own values.

# Use the Bedrock Guardrails Independent API

## 0. Preparation
Create your own Amazon Bedrock guardrails. For more information, see [Create a guardrail](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-create.html).

## 1. Create a connector with your Bedrock Guardrails endpoint

```
POST _plugins/_ml/connectors/_create
{
  "name": "BedRock claude Connector",
  "description": "BedRock claude Connector",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "us-east-1",
    "service_name": "bedrock",
    "source": "INPUT"
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
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/guardrail/your_guardrailIdentifier/version/1/apply",
      "headers": {
        "content-type": "application/json"
      },
      "request_body": "{\"source\":\"${parameters.source}\", \"content\":[ { \"text\":{\"text\": \"${parameters.input}\"} } ] }"
    }
  ]
}
```

## 2. Create a remote model with your guardrail connector

```
POST _plugins/_ml/models/_register
{
  "name": "bedrock test guardrails API",
  "function_name": "remote",
  "description": "guardrails test model",
  "connector_id": "your_connector_id"
}
```

## 3. Test the model

```
POST _plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "input": "\n\nHuman:how to suicide\n\nAssistant:"
  }
}
```

Response:
```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "action": "GUARDRAIL_INTERVENED",
            "assessments": [
              {
                "contentPolicy": {
                  "filters": [
                    {
                      "action": "BLOCKED",
                      "confidence": "HIGH",
                      "type": "VIOLENCE"
                    },
                    {
                      "action": "BLOCKED",
                      "confidence": "HIGH",
                      "type": "PROMPT_ATTACK"
                    }
                  ]
                },
                "wordPolicy": {
                  "customWords": [
                    {
                      "action": "BLOCKED",
                      "match": "suicide"
                    }
                  ]
                }
              }
            ],
            "blockedResponse": "Sorry, the model cannot answer this question.",
            "output": [
              {
                "text": "Sorry, the model cannot answer this question."
              }
            ],
            "outputs": [
              {
                "text": "Sorry, the model cannot answer this question."
              }
            ],
            "usage": {
              "contentPolicyUnits": 1.0,
              "contextualGroundingPolicyUnits": 0.0,
              "sensitiveInformationPolicyFreeUnits": 0.0,
              "sensitiveInformationPolicyUnits": 0.0,
              "topicPolicyUnits": 1.0,
              "wordPolicyUnits": 1.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```

# Use the guardrails embedded in the Amazon Bedrock Model Inference API

## 0. Preparation
Create your own Bedrock Guardrails. For more details, see the Bedrock document [Create a guardrail](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-create.html).

## 1. Create a connector of Bedrock model with guardrails headers

```
POST /_plugins/_ml/connectors/_create
{
  "name": "BedRock claude Connector",
  "description": "BedRock claude Connector",
  "version": 1,
  "protocol": "aws_sigv4",
  "parameters": {
      "region": "us-east-1",
      "service_name": "bedrock",
      "anthropic_version": "bedrock-2023-05-31",
      "endpoint": "bedrock.us-east-1.amazonaws.com",
      "auth": "Sig_V4",
      "content_type": "application/json",
      "max_tokens_to_sample": 8000,
      "temperature": 0.0001
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
      "url": "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke",
      "headers": { 
        "content-type": "application/json",
        "x-amz-content-sha256": "required",
        "X-Amzn-Bedrock-Trace": "ENABLED",
        "X-Amzn-Bedrock-GuardrailIdentifier": "your_GuardrailIdentifier",
        "X-Amzn-Bedrock-GuardrailVersion": "1"
      },
      "request_body": "{\"prompt\":\"${parameters.prompt}\", \"max_tokens_to_sample\":${parameters.max_tokens_to_sample}, \"temperature\":${parameters.temperature},  \"anthropic_version\":\"${parameters.anthropic_version}\" }",
      "post_process_function": "\n      if (params['amazon-bedrock-guardrailAction']=='INTERVENED') throw new IllegalArgumentException(\"test guardrail from post process function\");\n    "
    }
  ]
}
```
A `post_process_function` is required to define the logic for blocking the input by the guardrail.

## 2. Create a model

```
POST _plugins/_ml/models/_register
{
  "name": "bedrock model with guardrails",
  "function_name": "remote",
  "description": "guardrails test model",
  "connector_id": "your_connector_id"
}
```

## 3. Test the model

```
POST _plugins/_ml/models/your_model_id/_predict
{
  "parameters": {
    "input": "\n\nHuman:how to suicide\n\nAssistant:"
  }
}
```

Response:
```
{
  "error": {
    "root_cause": [
      {
        "type": "m_l_exception",
        "reason": "Fail to execute predict in aws connector"
      }
    ],
    "type": "m_l_exception",
    "reason": "Fail to execute predict in aws connector",
    "caused_by": {
      "type": "script_exception",
      "reason": "runtime error",
      "script_stack": [
        "throw new IllegalArgumentException(\"test guardrail from post process function\");\n    ",
        "      ^---- HERE"
      ],
      "script": " ...",
      "lang": "painless",
      "position": {
        "offset": 73,
        "start": 67,
        "end": 152
      },
      "caused_by": {
        "type": "illegal_argument_exception",
        "reason": "test guardrail from post process function"
      }
    }
  },
  "status": 500
}
```
