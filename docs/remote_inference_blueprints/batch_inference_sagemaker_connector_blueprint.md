### Sagemaker connector blueprint example for batch inference:

Read more details on https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/

Integrate the SageMaker Batch Transform API using the connector below with a new action type "batch_predict". 
For more details to use batch transform to run inference with Amazon SageMaker, please refer to https://docs.aws.amazon.com/sagemaker/latest/dg/batch-transform.html.

SageMaker uses your pre-created model to execute the batch transform job. For creating your model in SageMaker
that supports batch transform, please refer to https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_CreateModel.html. In this example, the following primary 
container is used to create the text-embedding DJL model in SageMaker.
```json
"ModelName": "DJL-Text-Embedding-Model-imageforjsonlines",
"PrimaryContainer": {
"Environment": {
"SERVING_LOAD_MODELS" : "djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2"
},
"Image": "763104351884.dkr.ecr.us-east-1.amazonaws.com/djl-inference:0.22.1-cpu-full"
}
```
#### 1. Create your Model connector and Model group

##### 1a. Register Model group
```json
POST /_plugins/_ml/model_groups/_register
{
  "name": "sagemaker_model_group",
  "description": "Your sagemaker model group"
}
```
This request response will return the `model_group_id`, note it down.
Sample response:
```json
{
  "model_group_id": "IMobmY8B8aiZvtEZeO_i",
  "status": "CREATED"
}
```

##### 1b. Create Connector
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "DJL Sagemaker Connector: all-MiniLM-L6-v2",
  "version": "1",
  "description": "The connector to sagemaker embedding model all-MiniLM-L6-v2",
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<your access_key>",
    "secret_key": "<your secret_key>",
    "session_token": "<your session_token>"
  },
  "parameters": {
    "region": "us-east-1",
    "service_name": "sagemaker",
    "DataProcessing": {
        "InputFilter": "$.content",
        "JoinSource": "Input",
        "OutputFilter": "$"
    },
    "ModelName": "DJL-Text-Embedding-Model-imageforjsonlines",
    "TransformInput": { 
      "ContentType": "application/json",
      "DataSource": { 
         "S3DataSource": { 
            "S3DataType": "S3Prefix",
            "S3Uri": "s3://offlinebatch/sagemaker_djl_batch_input.json"
         }
      },
      "SplitType": "Line"
    },
    "TransformJobName": "SM-offline-batch-transform-07-12-13-30",
    "TransformOutput": { 
      "AssembleWith": "Line",
      "Accept": "application/json",
      "S3OutputPath": "s3://offlinebatch/output"
   },
   "TransformResources": { 
      "InstanceCount": 1,
      "InstanceType": "ml.c5.xlarge"
   },
   "BatchStrategy": "SingleRecord"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "content-type": "application/json"
      },
      "url": "https://runtime.sagemaker.us-east-1.amazonaws.com/endpoints/OpenSearch-sagemaker-060124023703/invocations",
      "request_body": "${parameters.input}",
      "pre_process_function": "connector.pre_process.default.embedding",
      "post_process_function": "connector.post_process.default.embedding"
    },
    {
        "action_type": "batch_predict",
        "method": "POST",
        "headers": {
            "content-type": "application/json"
        },
        "url": "https://api.sagemaker.us-east-1.amazonaws.com/CreateTransformJob",
        "request_body": "{ \"BatchStrategy\": \"${parameters.BatchStrategy}\", \"ModelName\": \"${parameters.ModelName}\", \"DataProcessing\" : ${parameters.DataProcessing}, \"TransformInput\": ${parameters.TransformInput}, \"TransformJobName\" : \"${parameters.TransformJobName}\", \"TransformOutput\" : ${parameters.TransformOutput}, \"TransformResources\" : ${parameters.TransformResources}}"
    }
  ]
}
```
SageMaker supports data processing through a subset of the defined JSONPath operators, and supports Associating Inferences results with Input Records. 
Please refer to this [AWS doc](https://docs.aws.amazon.com/sagemaker/latest/dg/batch-transform-data-processing.html)

#### Sample response
```json
{
  "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```

### 2. Register model to the model group and link the created connector:

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "SageMaker model for realtime embedding and offline batch inference",
    "function_name": "remote",
    "model_group_id": "IMobmY8B8aiZvtEZeO_i",
    "description": "SageMaker hosted DJL model",
    "connector_id": "XU5UiokBpXT9icfOM0vt"
}
```
Sample response:
```json
{
  "task_id": "rMormY8B8aiZvtEZIO_j",
  "status": "CREATED",
  "model_id": "lyjxwZABNrAVdFa9zrcZ"
}
```
### 3. Test offline batch inference using the connector

```json
POST /_plugins/_ml/models/dBK3t5ABrxVhHgFYhg7Q/_batch_predict
{
  "parameters": {
    "TransformJobName": "SM-offline-batch-transform-07-15-11-30"
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
          "name": "response",
          "dataAsMap": {
            "job_arn": "arn:aws:sagemaker:us-east-1:802041417063:transform-job/SM-offline-batch-transform"
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
The "job_arn" is returned immediately from this request, and you can use this job_arn to check the job status 
in the SageMaker service. Once the job is done, you can check your batch inference results in the S3 that is 
specified in the "S3OutputPath" field in your connector.