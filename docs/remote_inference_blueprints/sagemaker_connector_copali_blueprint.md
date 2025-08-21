### Copali connector blueprint example for multimodal embedding:

Read more details on https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/

## 1. Deploy Copali Model in SageMaker

First, we'll deploy the `vidore/colpali-v1.3-hf` model from Hugging Face to a SageMaker endpoint. Run the following Python code in a SageMaker Notebook.

```python
import sagemaker
import boto3
from sagemaker.huggingface import HuggingFaceModel

try:
    role = sagemaker.get_execution_role()
except ValueError:
    iam = boto3.client('iam')
    # Replace with your SageMaker execution role name if different
    role = iam.get_role(RoleName='sagemaker_execution_role')['Role']['Arn']

# Hub Model configuration. https://huggingface.co/models
hub = {
    'HF_MODEL_ID':'vidore/colpali-v1.3-hf',
    'HF_TASK':'visual-document-retrieval'
}

# create Hugging Face Model Class
huggingface_model = HuggingFaceModel(
    transformers_version='4.49.0',
    pytorch_version='2.6.0',
    py_version='py312',
    env=hub,
    role=role, 
)

# deploy model to SageMaker Inference
predictor = huggingface_model.deploy(
    initial_instance_count=1, # number of instances
    instance_type='ml.m5.xlarge' # ec2 instance type
)

# After deployment, you can find your endpoint name in the
# Amazon SageMaker > Inference > Endpoints console.
print(f"SageMaker Endpoint Name: {predictor.endpoint_name}")
```
Save the `SageMaker Endpoint Name` from the output. You will need it in the following steps.

## 2. Create the Connector and Register the Model
Based on your platform, there are different steps to create a connector. Regardless of the platform, the outcome will be a `connector_id` for model registration.

### A. Using a local OpenSearch instance
This approach is for users running OpenSearch on their own infrastructure.

Once the model endpoint is available on SageMaker, you can create a connector and register the model in OpenSearch.
Please replace `<endpoint-name>` with the name of your SageMaker endpoint.

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "copali model",
    "function_name": "remote",
    "description": "copali model for ai dev con",
    "connector": {
        "name": "Amazon SageMaker connector",
        "description": "connector for copali in SageMaker",
        "version": 1,
        "protocol": "aws_sigv4",
        "parameters": {
            "region": "us-east-1",
            "service_name": "sagemaker"
        },
        "credential": {
            "access_key": "{{access_key}}",
            "secret_key": "{{secret_key}}",
            "session_token": "{{session_token}}"
        },
        "actions": [
            {
                "action_type": "predict",
                "method": "POST",
                "url": "https://runtime.sagemaker.us-east-1.amazonaws.com/endpoints/{{endpoint_name}}/invocations",
                "headers": {
                    "content-type": "application/json"
                },
                "request_body": "${parameters.inputs}"
            }
        ]
    }
}
```

You will get a `model_id` in the response:

```json
{
    "task_id": "gNilzZgBOh0h20Y9GokF",
    "status": "CREATED",
    "model_id": "gdilzZgBOh0h20Y9Goki"
}
```

### B. Using Amazon OpenSearch Service
This is the recommended approach when using Amazon's managed service.

#### a. Create an IAM Role for the OpenSearch Connector
In the AWS IAM console, create a new role with the following trust relationship. This allows the OpenSearch service to assume this role.

- **Custom trust policy:**
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sts:AssumeRole",
            "Effect": "Allow",
            "Principal": {
                "Service": "opensearchservice.amazonaws.com"
            }
        }
    ]
}
```

#### b. Attach Permissions
Create and attach the following two inline policies to the role.

- **Policy 1: Allow SageMaker Invocation**
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "sagemaker:InvokeEndpoint"
            ],
            "Effect": "Allow",
            "Resource": "arn:aws:sagemaker:your_region:your_account_id:endpoint/your_sagemaker_endpoint_name"
        }
    ]
}
```

- **Policy 2: Allow OpenSearch and IAM PassRole**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "arn:aws:iam::your_account_id:role/your_opensearch_sagemaker_role_name"
    },
    {
      "Effect": "Allow",
      "Action": "es:ESHttpPost",
      "Resource": "arn:aws:es:your_region:your_account_id:domain/your_domain_name/*"
    }
  ]
}
```
After creating the role, copy its ARN. You will need it to create the connector.

#### c. Map the IAM Role in OpenSearch Dashboards
Follow the documentation to map the IAM role you just created to the `ml_full_access` role in your OpenSearch domain.

Now, create the connector in OpenSearch to connect to the SageMaker model endpoint.

```json
POST /_plugins/_ml/models/_register?deploy=true
{
    "name": "copali model",
    "function_name": "remote",
    "description": "copali model for ai dev con",
    "connector": {
        "name": "Amazon SageMaker connector",
        "description": "connector for copali in SageMaker",
        "version": 1,
        "protocol": "aws_sigv4",
        "parameters": {
            "region": "us-east-1",
            "service_name": "sagemaker"
        },
        "credential": {
            "roleArn": "your_iam_role_arn_from_step_2a"
        },
        "actions": [
            {
                "action_type": "predict",
                "method": "POST",
                "url": "https://runtime.sagemaker.us-east-1.amazonaws.com/endpoints/{{endpoint_name}}/invocations",
                "headers": {
                    "content-type": "application/json"
                },
                "request_body": "${parameters.inputs}"
            }
        ]
    }
}
```


## 3. Test the Model

### Text-based Prediction

You can use the model for text-based predictions:

```json
POST /_plugins/_ml/models/gdilzZgBOh0h20Y9Goki/_predict
{
    "parameters": {
        "inputs": {
           "queries": ["hello world"]
        }
    }
}
```

The response will contain the query embeddings:

```json
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "response",
                    "dataAsMap": {
                        "query_embeddings": [
                            [
                                [
                                    -0.0022332952357828617,
                                    0.14774081110954285,
                                    ...
                                ]
                            ]
                        ]
                    ,
                      "query_tokens": [
                        {
                          "raw": "<bos>Query: hello world",
                          "tokens": [
                            "<bos>",
                            "Query",
                            ":",
                            "▁hello",
                            "▁world"
                          ]
                        }
                      ]
                    }
                }
            ],
          "status_code": 200
        }
    ]
}
```

### Image-based Prediction

You can also use the model for image-based predictions by providing a base64-encoded image:

```json
POST /_plugins/_ml/models/gdilzZgBOh0h20Y9Goki/_predict
{
    "parameters": {
        "inputs": {
           "images":["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"]
        }
    }
}
```

The response will contain the image embeddings:

```json
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "response",
                    "dataAsMap": {
                        "image_embeddings": [
                            [
                                [
                                    -0.0653999000787735,
                                    0.1100006103515625,
                                    ...
                                ]
                            ]
                        ],
                      "image_mask": [
                        [
                          1.0,
                          1.0,
                          1.0,
                          ...
                        ]
                      ],
                      "patch_shape": {
                        "height": 32.0,
                        "width": 32.0,
                        "total": 1024.0,
                        "image_size": [
                          87.0,
                          29.0
                        ],
                        "patch_size": 14.0
                      }
                    }
                }
            ],
          "status_code": 200
        }
    ]
}
```
### Text-and-Image-based Prediction

You can also use the model for multi modal, using text and image-based predictions by providing a text and a base64-encoded image:

```json
POST /_plugins/_ml/models/gdilzZgBOh0h20Y9Goki/_predict
{
  "parameters": {
    "inputs": {"queries": ["hello world"],
      "images":["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"]
    }
  }
}
```

```json
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "response",
                    "dataAsMap": {
                        "query_embeddings": [
                            [
                                [
                                    -0.0022332952357828617,
                                    0.14774081110954285,
                                    0.15953439474105835,
                                  ...
                                ]
                            ]
                        ],  
                      "query_tokens": [
                        {
                          "raw": "<bos>Query: hello world",
                          "tokens": [
                            "<bos>",
                            "Query",
                            ":",
                            "▁hello",
                            "▁world"
                          ]
                        }
                      ],
                      "image_embeddings": [
                        [
                          [
                            -0.0653999000787735,
                            0.1100006103515625,
                            0.2052547186613083,
                            0.059700652956962585,
                            ...
                          ]
                        ]
                      ],
                      "image_mask": [
                        [
                          1.0,
                          1.0,
                          1.0,
                          1.0,
                          1.0,
                          1.0,
                          1.0,
                          ...
                        ]
                      ],
                          "patch_shape": {
                          "height": 32.0,
                          "width": 32.0,
                          "total": 1024.0,
                          "image_size": [
                            87.0,
                            29.0
                          ],
                          "patch_size": 14.0
                        },
                          "scores": [
                          [
                            8.963132858276367
                          ]
                        ]
                          }
                        }
                        ],
                        "status_code": 200
                        }
                      ]
                    }
```