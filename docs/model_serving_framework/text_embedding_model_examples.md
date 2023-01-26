Model serving framework (released in 2.4) supports running NLP models inside OpenSearch cluster. 
It only supports text embedding NLP model now. This document will show you some examples of how to upload
and run text embedding models via ml-commons REST APIs. We use [Huggingface](https://huggingface.co/) models to build these examples.
Read [ml-commons doc](https://opensearch.org/docs/latest/ml-commons-plugin/model-serving-framework/) to learn more details.

We build examples with this Huggingface sentence transformers model [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)

From 2.5, we support uploading [torchscipt](https://pytorch.org/docs/stable/jit.html) and [ONNX](https://onnx.ai/) model.

Note: 
- This doc doesn't include how to trace models to torchscript/ONNX.
- Model serving framework is experimental feature. If you see any bug or have any suggestion, feel free to cut Github issue.

# 0. Prepare cluster

We suggest to start dedicated ML node to separate ML workloads from data nodes. From 2.5, ml-commons will run ML tasks on ML nodes only by default.
If you want to run some testing models on data node, you can disable this cluster setting `plugins.ml_commons.only_run_on_ml_node`.

```
PUT /_cluster/settings
{
  "persistent" : {
    "plugins.ml_commons.only_run_on_ml_node" : false 
  }
}
```

And we add native memory circuit breaker in 2.5 to avoid OOM error when loading too many models. 
By default, the native memory threshold is 90%. If exceeds the threshold, will throw exception. 
For testing purpose, you can set `plugins.ml_commons.native_memory_threshold` as 100% to disable it.

```
PUT _cluster/settings
{
  "persistent" : {
    "plugins.ml_commons.native_memory_threshold" : 100 
  }
}
```
ml-commons supports several other settings. You can tune them according to your requirement. Find more on this [ml-commons settings doc](https://opensearch.org/docs/latest/ml-commons-plugin/cluster-settings).

# 1. Torchscript
We can trace this example model [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) to torchscript with two options:

## 1.1 trace sentence transformers model
If you have [`sentence-transformers`](https://pypi.org/project/sentence-transformers/) installed. You can save this model directly: `SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')` and then trace the model using torchScipt.
Refer to [opensearch-py-ml doc](https://opensearch-project.github.io/opensearch-py-ml/reference/api/sentence_transformer.save_as_pt.html) and
opensearch-py-ml code: [sentencetransformermodel.py#save_as_pt](https://github.com/opensearch-project/opensearch-py-ml/blob/main/opensearch_py_ml/ml_models/sentencetransformermodel.py#L687)

Sentence transformer model already includes post-processing logic. So no need to specify `pooling_mode`/`normalize_result` when upload model.

- Step 1: upload model. This step will save model to model index.
```
# Sample request
POST /_plugins/_ml/models/_upload
{
  "name": "sentence-transformers/all-MiniLM-L6-v2",
  "version": "1.0.0",
  "description": "test model",
  "model_format": "TORCH_SCRIPT",
  "model_config": {
    "model_type": "bert",
    "embedding_dimension": 384,
    "framework_type": "sentence_transformers"
  },
  "url": "https://github.com/opensearch-project/ml-commons/tree/2.x/ml-algorithms/src/test/resources/org/opensearch/ml/engine/algorithms/text_embedding/all-MiniLM-L6-v2_torchscript_sentence-transformer.zip?raw=true"
}

# Sample response
{
    "task_id": "zgla5YUB1qmVrJFlwzW-",
    "status": "CREATED"
}
```
Then you can get task by calling get task API. When task completed, you will see `model_id` in response.
```
GET /_plugins/_ml/tasks/<task_id>

# Sample request
GET /_plugins/_ml/tasks/zgla5YUB1qmVrJFlwzW-

# Sample response
{
    "model_id": "zwla5YUB1qmVrJFlwzXJ",
    "task_type": "UPLOAD_MODEL",
    "function_name": "TEXT_EMBEDDING",
    "state": "COMPLETED",
    "worker_node": [
        "0TLL4hHxRv6_G3n6y1l0BQ"
    ],
    "create_time": 1674590208957,
    "last_update_time": 1674590211718,
    "is_async": true
}
```
- Step 2: deploy/load model. This step will read model content from index and deploy to node. 

```
# Deploy to all nodes
POST /_plugins/_ml/models/<model_id>/_load
# Deploy to specific nodes
POST /_plugins/_ml/models/<model_id>/_load
{
    "node_ids": [ "<node_id>", "<node_id>" ] # replace "node_id" with your own node id
}

# Sample request
POST /_plugins/_ml/models/zwla5YUB1qmVrJFlwzXJ/_load

# Sample response
{
    "task_id": "0Alb5YUB1qmVrJFlADVT",
    "status": "CREATED"
}
```
Similar to upload model, you can get task by calling get task API. When task completed, you can run predict API.

```
# Sample request
GET /_plugins/_ml/tasks/0Alb5YUB1qmVrJFlADVT

# Sample response
{
    "model_id": "zwla5YUB1qmVrJFlwzXJ",
    "task_type": "LOAD_MODEL",
    "function_name": "TEXT_EMBEDDING",
    "state": "COMPLETED",
    "worker_node": [
        "0TLL4hHxRv6_G3n6y1l0BQ"
    ],
    "create_time": 1674590224467,
    "last_update_time": 1674590226409,
    "is_async": true
}
```

- Step 3: inference/predict

```
POST /_plugins/_ml/_predict/text_embedding/<model_id>

# Sample request
POST /_plugins/_ml/_predict/text_embedding/zwla5YUB1qmVrJFlwzXJ
{
    "text_docs": [ "today is sunny" ],
    "return_number": true,
    "target_response": [ "sentence_embedding" ]
}

# Sample response
{
    "inference_results": [
        {
            "output": [
                {
                    "name": "sentence_embedding",
                    "data_type": "FLOAT32",
                    "shape": [
                        384
                    ],
                    "data": [
                        -0.023314998,
                        0.08975688,
                        0.07847973,
                        ...
                    ]
                }
            ]
        }
    ]
}
```
- Step 4 (optional): profile

You can use profile API to get model deployment information and monitor inference latency. Refer to [this doc](https://opensearch.org/docs/latest/ml-commons-plugin/api/#profile)

By default, it will monitor last 100 predict requests. You can tune this setting [plugins.ml_commons.monitoring_request_count](https://opensearch.org/docs/latest/ml-commons-plugin/cluster-settings/#predict-monitoring-requests) to control monitoring how many requests.  

```
# Sample request
GET /_plugins/_ml/profile/models/zwla5YUB1qmVrJFlwzXJ

# Sample response
{
    "nodes": {
        "0TLL4hHxRv6_G3n6y1l0BQ": { # node id
            "models": {
                "zwla5YUB1qmVrJFlwzXJ": { # model id
                    "model_state": "LOADED",
                    "predictor": "org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel@1a0b0793",
                    "target_worker_nodes": [ # plan to deploy model to these nodes
                        "0TLL4hHxRv6_G3n6y1l0BQ"
                    ],
                    "worker_nodes": [ # model deployed to these nodes
                        "0TLL4hHxRv6_G3n6y1l0BQ"
                    ],
                    "model_inference_stats": { // in Millisecond, time used in model part
                        "count": 10,
                        "max": 35.021633,
                        "min": 31.924348,
                        "average": 33.9418092,
                        "p50": 34.0341065,
                        "p90": 34.8487421,
                        "p99": 35.00434391
                    },
                    "predict_request_stats": { // in Millisecond, end-to-end time including model and all other parts 
                        "count": 10,
                        "max": 36.037992,
                        "min": 32.903162,
                        "average": 34.9731029,
                        "p50": 35.073967999999994,
                        "p90": 35.868510300000004,
                        "p99": 36.02104383
                    }
                }
            }
        }
    }
}
```
- Step 5: unload model. This step will destroy model from nodes. Model document won't be deleted from model index.

```
# Unload one model
POST /_plugins/_ml/models/<model_id>/_unload
# Unload all models
POST /_plugins/_ml/models/_unload

# Sample request
POST /_plugins/_ml/models/zwla5YUB1qmVrJFlwzXJ/_unload

# Sample response
{
    "0TLL4hHxRv6_G3n6y1l0BQ": { # node id
        "stats": {
            "zwla5YUB1qmVrJFlwzXJ": "unloaded"
        }
    }
}
```

## 1.2 trace huggingface transformers model
Without [`sentence-transformers`](https://pypi.org/project/sentence-transformers/) installed, you can trace this model `AutoModel.from_pretrained('sentence-transformers/all-MiniLM-L6-v2')`.
But model traced this way doesn't include post-processing. So user have to specify post-process logic with `pooling_mode` and `normalize_result`. 

Supported pooling method: `mean`, `mean_sqrt_len`, `max`, `weightedmean`, `cls`.

The only difference is the uploading model input, for load/predict/profile/unload model, you can refer to ["1.1 trace sentence transformers model"](#11-trace-sentence-transformers-model).

```
# Sample request
POST /_plugins/_ml/models/_upload
{
  "name": "sentence-transformers/all-MiniLM-L6-v2",
  "version": "1.0.0",
  "description": "test model",
  "model_format": "TORCH_SCRIPT",
  "model_config": {
    "model_type": "bert",
    "embedding_dimension": 384,
    "framework_type": "huggingface_transformers",
    "pooling_mode":"mean",
    "normalize_result":"true"
  },
  "url": "https://github.com/opensearch-project/ml-commons/tree/2.x/ml-algorithms/src/test/resources/org/opensearch/ml/engine/algorithms/text_embedding/all-MiniLM-L6-v2_torchscript_huggingface.zip?raw=true"
}
```

# 2. ONNX
User can export Pytorch model to ONNX, then upload and run it with ml-commons APIs.
This example ONNX model also needs to specify post-process logic with `pooling_mode` and `normalize_result`.

Supported pooling method: `mean`, `mean_sqrt_len`, `max`, `weightedmean`, `cls`.

The only difference is the uploading model input, for load/predict/profile/unload model, you can refer to ["1.1 trace sentence transformers model"](#11-trace-sentence-transformers-model).

```
# Sample request
POST /_plugins/_ml/models/_upload
{
  "name": "sentence-transformers/all-MiniLM-L6-v2",
  "version": "1.0.0",
  "description": "test model",
  "model_format": "ONNX",
  "model_config": {
    "model_type": "bert",
    "embedding_dimension": 384,
    "framework_type": "huggingface_transformers",
    "pooling_mode":"mean",
    "normalize_result":"true"
  },
  "url": "https://github.com/opensearch-project/ml-commons/tree/2.x/ml-algorithms/src/test/resources/org/opensearch/ml/engine/algorithms/text_embedding/all-MiniLM-L6-v2_onnx.zip?raw=true"
}
```
