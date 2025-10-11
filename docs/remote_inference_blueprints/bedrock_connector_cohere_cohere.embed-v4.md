# Connector blueprint examples for semantic search using Cohere Embed v4 model on Amazon Bedrock

## 1. Add connector endpoint to trusted URLs:

Note: no need to do this after 2.11.0

```json
PUT /_cluster/settings
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
        ]
    }
}
```

## 2. Create connector for Amazon Bedrock:

If you are using self-managed Opensearch, you should supply AWS credentials:

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: Cohere Embed v4",
  "description": "Test connector for Amazon Bedrock Cohere Embed v4",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SECURITY TOKEN HERE>"
  },
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "service_name": "bedrock",
    "truncate": "<NONE|START|END>",
    "input_type": "<search_document|search_query|classification|clustering>",
    "model": "cohere.embed-v4:0",
    "embedding_types": [<"float"|"int8"|"binary">],
    "output_dimension": <256 | 512 | 1024 | 1536>
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
          "x-amz-content-sha256": "required",
          "content-type": "application/json"
      },
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "request_body": """
        { 
          "texts": ${parameters.texts},
          "truncate": "${parameters.truncate}",
          "input_type": "${parameters.input_type}",
          "embedding_types": ${parameters.embedding_types},
          "output_dimension": ${parameters.output_dimension}
        }
      """,
      "pre_process_function": "connector.pre_process.cohere.embedding",
      "post_process_function": """
        def name = "sentence_embedding";
        def data_type = "FLOAT32";
        def result;
        if (params.embeddings.int8 != null) {
          data_type = "INT8";
          result = params.embeddings.int8;
        }
        else if (params.embeddings.uint8 != null) {
          data_type = "UINT8";
          result = params.embeddings.uint8;
        }
        if (params.embeddings.binary != null) {
          data_type = "BINARY";
          result = params.embeddings.binary;
        } 
        else if (params.embeddings.ubinary != null) {
          data_type = "UBINARY";
          result = params.embeddings.ubinary;
        }
        else if (params.embeddings.float != null) {
          data_type = "FLOAT32";
          result = params.embeddings.float;
        }
        if (result == null) {
          return "Invalid embedding result";
        }
        def embedding_list = new StringBuilder("[");
        for (int m=0; m<result.length; m++) {
          def embedding_size = result[m].length;
          def embedding = new StringBuilder("[");
          def shape = [embedding_size];
          for (int i=0; i<embedding_size; i++) {
            def val;
            if ("FLOAT32".equals(data_type)) {
              val = result[m][i].floatValue();
            }
            else if ("INT8".equals(data_type) || "UINT8".equals(data_type)) {
              val = result[m][i].intValue();
            }
            else if ("BINARY".equals(data_type) || "UBINARY".equals(data_type)) {
              val = result[m][i].intValue();
            }
            embedding.append(val);
            if (i < embedding_size - 1) {
              embedding.append(",");
            }
          }
          embedding.append("]");
          // workaround for compatible with neural-search
          def dummy_data_type = 'FLOAT32';
          def json = '{' + '"name":"' + name + '",' + '"data_type":"' + dummy_data_type + '",' + '"shape":' + shape + ',' + '"data":' + embedding + '}';
          embedding_list.append(json);
          if (m < result.length - 1) {
            embedding_list.append(",");
          }
        }
        embedding_list.append("]");
        return embedding_list.toString();
      """
    }
  ]
}
```

If using the AWS Opensearch Service, you can provide an IAM role arn that allows access to the bedrock service.
Refer to this [AWS doc](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/ml-amazon-connector.html)

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: Cohere Embed v4",
  "description": "Test connector for Amazon Bedrock Cohere Embed v4",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "roleArn": "<PLEASE ADD YOUR AWS ROLE ARN HERE>"
  },
  "parameters": {
    "region": "<PLEASE ADD YOUR AWS REGION HERE>",
    "service_name": "bedrock",
    "truncate": "<NONE|START|END>",
    "input_type": "<search_document|search_query|classification|clustering>",
    "model": "cohere.embed-v4:0",
    "embedding_types": [<"float"|"int8"|"binary">],
    "output_dimension": <256 | 512 | 1024 | 1536>
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
          "x-amz-content-sha256": "required",
          "content-type": "application/json"
      },
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "request_body": """
        { 
          "texts": ${parameters.texts},
          "truncate": "${parameters.truncate}",
          "input_type": "${parameters.input_type}",
          "embedding_types": ${parameters.embedding_types},
          "output_dimension": ${parameters.output_dimension}
        }
      """,
      "pre_process_function": "connector.pre_process.cohere.embedding",
      "post_process_function": """
        def name = "sentence_embedding";
        def data_type = "FLOAT32";
        def result;
        if (params.embeddings.int8 != null) {
          data_type = "INT8";
          result = params.embeddings.int8;
        }
        else if (params.embeddings.uint8 != null) {
          data_type = "UINT8";
          result = params.embeddings.uint8;
        }
        if (params.embeddings.binary != null) {
          data_type = "BINARY";
          result = params.embeddings.binary;
        } 
        else if (params.embeddings.ubinary != null) {
          data_type = "UBINARY";
          result = params.embeddings.ubinary;
        }
        else if (params.embeddings.float != null) {
          data_type = "FLOAT32";
          result = params.embeddings.float;
        }
        if (result == null) {
          return "Invalid embedding result";
        }
        def embedding_list = new StringBuilder("[");
        for (int m=0; m<result.length; m++) {
          def embedding_size = result[m].length;
          def embedding = new StringBuilder("[");
          def shape = [embedding_size];
          for (int i=0; i<embedding_size; i++) {
            def val;
            if ("FLOAT32".equals(data_type)) {
              val = result[m][i].floatValue();
            }
            else if ("INT8".equals(data_type) || "UINT8".equals(data_type)) {
              val = result[m][i].intValue();
            }
            else if ("BINARY".equals(data_type) || "UBINARY".equals(data_type)) {
              val = result[m][i].intValue();
            }
            embedding.append(val);
            if (i < embedding_size - 1) {
              embedding.append(",");
            }
          }
          embedding.append("]");
          // workaround for compatible with neural-search
          def dummy_data_type = 'FLOAT32';
          def json = '{' + '"name":"' + name + '",' + '"data_type":"' + dummy_data_type + '",' + '"shape":' + shape + ',' + '"data":' + embedding + '}';
          embedding_list.append(json);
          if (m < result.length - 1) {
            embedding_list.append(",");
          }
        }
        embedding_list.append("]");
        return embedding_list.toString();
      """
    }
  ]
}
```

Sample response:
```json
{
  "connector_id": "ISj-wZABNrAVdFa9cLju"
}
```
For more information of the model inference parameters in the connector, please refer to this [AWS doc](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html)

## 3. Create model group:

```json
POST /_plugins/_ml/model_groups/_register
{
    "name": "remote_model_group_cohere",
    "description": "model group for cohere models"
}
```

Sample response:
```json
{
  "model_group_id": "IMobmY8B8aiZvtEZeO_i",
  "status": "CREATED"
}
```

## 4. Register model to model group & deploy model:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "cohere.embed-v4",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "cohere embed v4 model",
    "connector_id": "ISj-wZABNrAVdFa9cLju"
}
```

Sample response:
```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "KSj-wZABNrAVdFa937iS"
}
```

## 5. Test model inference

```json
POST /_plugins/_ml/models/KSj-wZABNrAVdFa937iS/_predict
{
  "parameters": {
    "texts" : ["上海", "This is a test"]
  }
}
```
or 
```json
POST /_plugins/_ml/_predict/text_embedding/KSj-wZABNrAVdFa937iS
{
    "text_docs":["上海", "This is a test"],
    "return_number": true,
    "target_response": ["sentence_embedding"]
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
            1536
          ],
          "data": [
            0.012939453,
            -0.0017471313,
            0.0056152344,
            -0.0059814453,
            ...
          ]
        },
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [
            1536
          ],
          "data": [
            -0.000957489,
            0.023803711,
            0.001045227,
            -0.01373291,
            ...
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```
