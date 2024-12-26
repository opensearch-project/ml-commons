# Tutorial: Running Asymmetric Semnantic Search within OpenSearch

This tutorial demonstrates how generating text embeddings using an asymmetric embedding model in OpenSearch. The example model used in this tutorial is the multilingual
`intfloat/multilingual-e5-small` model from Hugging Face.
In this tutorial, you'll learn how to prepare the model, register it in OpenSearch, and run inference to generate embeddings.

> **Note**: Make sure to replace all placeholders (e.g., `your_`) with your specific values.

---

## Prerequisites

- OpenSearch installed on your machine
- Access to the Hugging Face `intfloat/multilingual-e5-small` model (or another model of your choice).
- Basic knowledge of Linux commands
---

## Step 1: Start OpenSearch locally

See here for directions to install and run [OpenSearch](https://opensearch.org/docs/latest/install-and-configure/install-opensearch/index/). 

Run OpenSearch locally and make sure to do the following.

###  Update cluster settings

Ensure your cluster is configured to allow registering models. You can do this by updating the cluster settings using the following request:

```
PUT _cluster/settings
{
  "persistent": {
    "plugins.ml_commons.allow_registering_model_via_url": "true",
    "plugins.ml_commons.only_run_on_ml_node": "false",
    "plugins.ml_commons.model_access_control_enabled": "true",
    "plugins.ml_commons.native_memory_threshold": "99"
  }
}
```

This configuration ensures that OpenSearch can accept machine learning models from external URLs and can run models across non-ML nodes.


---

## Step 2: Prepare the model for OpenSearch

In this tutorial, you’ll use the Hugging Face `intfloat/multilingual-e5-small` model, which is capable of generating multilingual embeddings. Follow these steps to prepare and zip the model for use in OpenSearch.

### a. Clone the model from Hugging Face

To download the model, use the following steps:

1. Install Git Large File Storage (LFS) if you haven’t already:

   ```
   git lfs install
   ```

2. Clone the model repository:

   ```
   git clone https://huggingface.co/intfloat/multilingual-e5-small
   ```

This will download the model files into a directory on your local machine.

### b. Zip the model files

To upload the model to OpenSearch, you must zip the necessary model files (`model.onnx`, `sentencepiece.bpe.model`, and `tokenizer.json`). The `model.onnx` file is located in the `onnx` directory of the cloned repository.

Run the following command in the directory containing these files:

```
zip -r intfloat-multilingual-e5-small-onnx.zip model.onnx tokenizer.json sentencepiece.bpe.model
```

This command will create a zip file named `intfloat-multilingual-e5-small-onnx.zip`, with the all necessary files.

### c. Calculate the model file's hash

Before registering the model, you need to calculate the SHA-256 hash of the zip file. Run this command to generate the hash:

```
shasum -a 256 intfloat-multilingual-e5-small-onnx.zip
```

Note the hash value; You'll need it during the model registration.

### d. Serve the model file using a Python HTTP server

To allow OpenSearch to access the model file, you can serve it through HTTP. Because this tutorial uses a local development environment, you can use Python's built-in HTTP server command:

Navigate to the directory containing the zip file and run the following command:

```
python3 -m http.server 8080 --bind 0.0.0.0
```

This will serve the zip file at `http://0.0.0.0:8080/intfloat-multilingual-e5-small-onnx.zip`. After registering the model, you can stop the server by pressing `Ctrl + C`.

---

## Step 3: Register a model group

Before registering the model itself, you need to create a model group. This helps organize models in OpenSearch. Run the following request to create a new model group:

```
POST /_plugins/_ml/model_groups/_register
{
  "name": "Asymmetric Model Group",
  "description": "A model group for local asymmetric models"
}
```

Note of the `model_group_id` returned in the response; you'll use it to register the model.

---

## Step 4: Register the model

Now that you have the model zip file and the model group ID, you can register the model in OpenSearch:

```
POST /_plugins/_ml/models/_register
{
    "name": "e5-small-onnx",
    "version": "1.0.0",
    "description": "Asymmetric multilingual-e5-small model",
    "model_format": "ONNX",
    "model_group_id": "your_group_id",
    "model_content_hash_value": "your_model_zip_content_hash_value",
    "model_config": {
        "model_type": "bert",
        "embedding_dimension": 384,
        "framework_type": "sentence_transformers",
        "query_prefix": "query: ",
        "passage_prefix": "passage: ",
        "all_config": "{ \"_name_or_path\": \"intfloat/multilingual-e5-small\", \"architectures\": [ \"BertModel\" ], \"attention_probs_dropout_prob\": 0.1, \"hidden_size\": 384, \"num_attention_heads\": 12, \"num_hidden_layers\": 12, \"tokenizer_class\": \"XLMRobertaTokenizer\" }"
    },
    "url": "http://localhost:8080/intfloat-multilingual-e5-small-onnx.zip"
}
```

Replace `your_group_id` and `your_model_zip_content_hash_value` with the values from previous steps. This will initiate the model registration process, and you’ll receive a task ID in the response.

To check the status of the registration, run the following request:

```
GET /_plugins/_ml/tasks/your_task_id
```

Once successful, note the `model_id` returned; you'll need it for deployment and inference.

---

## Step 5: Deploy the model

After the model is registered, you can deploy it by running the following request:

```
POST /_plugins/_ml/models/your_model_id/_deploy
```

Check the status of the deployment using the task ID:

```
GET /_plugins/_ml/tasks/your_task_id
```

When the model is successfully deployed, it's state will change to the **DEPLOYED** state, and you can use it for inference.

---

## Step 6: Run inference

Now that your model is deployed, you can use it to generate text embeddings for both queries and passages.

### Generating passage embeddings

To generate embeddings for a passage, use the following request:

```
POST /_plugins/_ml/_predict/text_embedding/your_model_id
{
  "parameters": {
    "content_type": "passage"
  },
  "text_docs": [
    "Today is Friday, tomorrow will be my break day. After that, I will go to the library. When is lunch?"
  ],
  "target_response": ["sentence_embedding"]
}
```

The response will include a sentence embedding of size 384:

```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [384],
          "data": [0.0419328, 0.047480892, ..., 0.31158513, 0.21784715]
        }
      ]
    }
  ]
}
```

### Generating query embeddings

Similarly, you can generate embeddings for a query:

```
POST /_plugins/_ml/_predict/text_embedding/your_model_id
{
  "parameters": {
    "content_type": "query"
  },
  "text_docs": ["What day is it today?"],
  "target_response": ["sentence_embedding"]
}
```

The response will look like this:

```
{
  "inference_results": [
    {
      "output": [
        {
          "name": "sentence_embedding",
          "data_type": "FLOAT32",
          "shape": [384],
          "data": [0.2338349, -0.13603798, ..., 0.37335885, 0.10653384]
        }
      ]
    }
  ]
}
```

---

# Applying semantic search using an ML Inference processor

In this section you'll run semantic search on facts about New York City. First, you'll create an ingest pipeline
using the ML inference processor to create embeddings on ingestion. Then create a search pipeline to run a search using
the same asymmetric embedding model. 


## 2. Create an ingest pipeline

### 2.1 Create a test KNN index
```
PUT nyc_facts
{
  "settings": {
    "index": {
      "default_pipeline": "asymmetric_embedding_ingest_pipeline",
      "knn": true,
      "knn.algo_param.ef_search": 100
    }
  },
  "mappings": {
    "properties":  {
      "fact_embedding": {
        "type": "knn_vector",
        "dimension": 384,
        "method": {
          "name": "hnsw",
          "space_type": "l2",
          "engine": "nmslib",
          "parameters": {
            "ef_construction": 128,
            "m": 24
          }
        } 
      }
    }
  }
}
```

### 2.2 Create an ingest pipeline

```
PUT _ingest/pipeline/asymmetric_embedding_ingest_pipeline
{
	"description": "ingest passage text and generate a embedding using an asymmetric model",
	"processors": [
		{
			"ml_inference": {

				"model_input": "{\"text_docs\":[\"${input_map.text_docs}\"],\"target_response\":[\"sentence_embedding\"],\"parameters\":{\"content_type\":\"query\"}}",
				"function_name": "text_embedding",
				"model_id": "{{ _.model_id }}",
				"input_map": [
					{
						"text_docs": "description"
					}
				],
				"output_map": [
					{
						"fact_embedding": "$.inference_results[0].output[0].data",
						"embedding_size": "$.inference_results.*.output.*.shape[0]"
					}
				]
			}
		}
	]
}
```

### 2.3 Simulate the pipeline

You can test the pipeline using the simulate endpoint. Simulate the pipeline by running the following request:
```
POST /_ingest/pipeline/asymmetric_embedding_ingest_pipeline/_simulate
{
   "docs": [
	  {
         "_index": "my-index",
         "_id": "1",
         "_source": {
             "title": "Central Park",
             "description": "A large public park in the heart of New York City, offering a wide range of recreational activities."
         }
		}
	]
}
```
The response contains the embedding generated by the model after we "ingested" a document using the pipeline.
```
{
   "docs": [
      {
         "doc": {
             "_index": "my-index",
             "_id": "1",
             "_source": {
                 "description": "A large public park in the heart of New York City, offering a wide range of recreational activities.",
                 "fact_embedding": [
                     [
                         0.06344555,
                         0.30067796,
                         ...
                         0.014804064,
                         -0.022822019						
                     ]
                 ],
                 "title": "Central Park",
                 "embedding_size": [
                     384.0
                 ]
             },
             "_ingest": {
                 "timestamp": "2024-12-16T20:59:07.152169Z"
             }
         }
      }
	]
}
```

### 2.4 Test data ingestion
When you perform bulk ingestion, the ingest pipeline will generate embeddings for each document: 
```
POST /_bulk
{ "index": { "_index": "nyc_facts" } }
{ "title": "Central Park", "description": "A large public park in the heart of New York City, offering a wide range of recreational activities." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Empire State Building", "description": "An iconic skyscraper in New York City offering breathtaking views from its observation deck." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Statue of Liberty", "description": "A colossal neoclassical sculpture on Liberty Island, symbolizing freedom and democracy in the United States." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Brooklyn Bridge", "description": "A historic suspension bridge connecting Manhattan and Brooklyn, offering pedestrian walkways with great views." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Times Square", "description": "A bustling commercial and entertainment hub in Manhattan, known for its neon lights and Broadway theaters." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Yankee Stadium", "description": "Home to the New York Yankees, this baseball stadium is a historic landmark in the Bronx." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "The Bronx Zoo", "description": "One of the largest zoos in the world, located in the Bronx, featuring diverse animal exhibits and conservation efforts." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "New York Botanical Garden", "description": "A large botanical garden in the Bronx, known for its diverse plant collections and stunning landscapes." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Flushing Meadows-Corona Park", "description": "A major park in Queens, home to the USTA Billie Jean King National Tennis Center and the Unisphere." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Citi Field", "description": "The home stadium of the New York Mets, located in Queens, known for its modern design and fan-friendly atmosphere." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Rockefeller Center", "description": "A famous complex of commercial buildings in Manhattan, home to the NBC studios and the annual ice skating rink." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Queens Botanical Garden", "description": "A peaceful, beautiful botanical garden located in Flushing, Queens, featuring seasonal displays and plant collections." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Arthur Ashe Stadium", "description": "The largest tennis stadium in the world, located in Flushing Meadows-Corona Park, Queens, hosting the U.S. Open." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Wave Hill", "description": "A public garden and cultural center in the Bronx, offering stunning views of the Hudson River and a variety of nature programs." }
{ "index": { "_index": "nyc_facts" } }
{ "title": "Louis Armstrong House", "description": "The former home of jazz legend Louis Armstrong, located in Corona, Queens, now a museum celebrating his life and music." }

```

## 3. Run Semantic Search

### 3.1 Create a Search Pipeline
Create a search pipeline that will convert your query into an embedding and run K-NN search on the index to return the best-matching documents:

```
PUT /_search/pipeline/asymmetric_embedding_search_pipeline
{
   "description": "ingest passage text and generate a embedding using an asymmetric model",
   "request_processors": [
      {
        "ml_inference": {
            "query_template": "{\"size\": 3,\"query\": {\"knn\": {\"fact_embedding\": {\"vector\": ${query_embedding},\"k\": 4}}}}",
            "function_name": "text_embedding",
            "model_id": "{{ _.model_id }}",
            "model_input": "{ \"text_docs\": [\"${input_map.query}\"], \"target_response\": [\"sentence_embedding\"], \"parameters\" : {\"content_type\" : \"query\" } }",
            "input_map": [
               {
                  "query": "query.term.fact_embedding.value"
               }
            ],
            "output_map": [
               {
                  "query_embedding": "$.inference_results[0].output[0].data",
                  "embedding_size": "$.inference_results.*.output.*.shape[0]"
               }
            ]
         }
      }
   ]
}

```

### 3.1 Run Semantic Search
Run a query about sporting activities in New York City:
```
GET /nyc_facts/_search?search_pipeline=asymmetric_embedding_search_pipeline
{
  "query": {
    "term": {
      "fact_embedding": {
        "value": "What are some places for sports in NYC?",
       "boost": 1 
      }
    }
  }
}
```

The response contains the top 3 matching documents: 
```json
{
  "took": 22,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": 0.12496973,
    "hits": [
      {
        "_index": "nyc_facts",
        "_id": "hb9X0ZMBICPs-TP0ijZX",
        "_score": 0.12496973,
        "_source": {
          "fact_embedding": [
            ...
          ],
          "embedding_size": [
            384.0
          ],
          "description": "A large public park in the heart of New York City, offering a wide range of recreational activities.",
          "title": "Central Park"
        }
      },
      {
        "_index": "nyc_facts",
        "_id": "ir9X0ZMBICPs-TP0ijZX",
        "_score": 0.114651985,
        "_source": {
          "fact_embedding": [
            ...
          ],
          "embedding_size": [
            384.0
          ],
          "description": "Home to the New York Yankees, this baseball stadium is a historic landmark in the Bronx.",
          "title": "Yankee Stadium"
        }
      },
      {
        "_index": "nyc_facts",
        "_id": "j79X0ZMBICPs-TP0ijZX",
        "_score": 0.110090025,
        "_source": {
          "fact_embedding": [
            ...
          ],
          "embedding_size": [
            384.0
          ],
          "description": "A famous complex of commercial buildings in Manhattan, home to the NBC studios and the annual ice skating rink.",
          "title": "Rockefeller Center"
        }
      }
    ]
  }
}
```
---

## References

- Wang, Liang, et al. (2024). *Multilingual E5 Text Embeddings: A Technical Report*. arXiv preprint arXiv:2402.05672. [Link](https://arxiv.org/abs/2402.05672)

---

