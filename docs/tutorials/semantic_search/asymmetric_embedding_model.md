# Topic

This tutorial shows how to generate embeddings using a local asymmetric embedding model in OpenSearch implemented in a Docker container .

Note: Replace the placeholders that start with `your_` with your own values.

# Steps

## 1. Spin up a docker OpenSearch Cluster

With docker you can create a multi node cluster follow this docker-compose file as an example https://opensearch.org/docs/latest/install-and-configure/install-opensearch/docker/#sample-docker-compose-file-for-development
.Make sure you have docker desktop installed

  ### a. Update cluster settings
The current step uses a docker-compose file that uses two opensearch Non-ML nodes. This requires you to update cluster settings
so you can run a model.
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

  ###  b. Use a docker compose file

Now that you have the docker-compose file you can use it by having docker dektop running in the background and then running 
the command (within the same directory of the compose file) `docker-compose up -d` which will start opensearch in the background. 

## 2. Prepare the model for OpenSearch
In this tutorial you will use a Hugging Face intfloat/multilingual-e5-small model (https://huggingface.co/intfloat/multilingual-e5-small) an asymmetric
text embedding model capable of handling different languages.
  
###  a. Clone the model
  You can find the steps within the models homepage click on the three dots just left of the train button. Then click **clone repository**
for this specific tutorial you will have to execture teh following. Making sure you find a approriate place to host the model.
1. `git lfs install`
2. `git clone https://huggingface.co/intfloat/multilingual-e5-small`
  ###  b. Zip the contents
In order to send the OpenSearch the embedding model make sure to zip the model contents more specifically you will need to zip the following
items in the directory that has the items `model.onnx, sentencepiece.bpe.model, tokenizer.json`. The **model.onnx** file is found within the 
onnx directory of the repository you cloned. Now that you have the contents run the following in the relevant directory `zip -r intfloat-multilingual-e5-small-onnx.zip model.onnx tokenizer.json sentencepiece.bpe.model`
This will create a zip file with the name **intfloat-multilingual-e5-small-onnx.zip**
  ###  c. Calculate hash
  Now that you have the zip file you must now calculate its hash so that you can use it on model registration. RUn the following within
the directory that has the zip `shasum -a 256 intfloat-multilingual-e5-small-onnx.zip`. 
  ###  d. service the zip file using a python server
  With the zip file and its hash we should service it so that OpenSearch can find it and download it. Since this is for a local development
we can simply host this locally using python. Navigate to the directory that has the zip file and run the following `python3 -m http.server 8080 --bind 0.0.0.0           
` After step 4 you can cancel this server by executing ctrl + c. 
 

## 3. Register a model group
We will create a model group to associate the model run the following and take note of the model group id.
```
POST /_plugins/_ml/model_groups/_register
{
  "name": "Asymmetric Model Group",
  "description": "A model group for local assymetric models"
}
```

## 4. Register the model
Now we can register the model which will retrieve the model from the python server since this is running within a docker
container you will have to use the url `http://host.docker.internal:8080/intfloat-multilingual-e5-small-onnx.zip`. When
running the command below make sure to take note of the model id returned by the OpenSearch call after calling the task API.

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
        "query_prefix" : "query: ", 
        "passage_prefix" : "passage: ",
        "all_config" : "{  \"_name_or_path\": \"intfloat/multilingual-e5-small\",  \"architectures\": [    \"BertModel\"  ],  \"attention_probs_dropout_prob\": 0.1,  \"classifier_dropout\": null,  \"hidden_act\": \"gelu\",  \"hidden_dropout_prob\": 0.1,  \"hidden_size\": 384,  \"initializer_range\": 0.02,  \"intermediate_size\": 1536,  \"layer_norm_eps\": 1e-12,  \"max_position_embeddings\": 512,  \"model_type\": \"bert\",  \"num_attention_heads\": 12,  \"num_hidden_layers\": 12,  \"pad_token_id\": 0,  \"position_embedding_type\": \"absolute\",  \"tokenizer_class\": \"XLMRobertaTokenizer\",  \"transformers_version\": \"4.30.2\",  \"type_vocab_size\": 2,  \"use_cache\": true,  \"vocab_size\": 250037}"
    },
    "url": "http://host.docker.internal:8080/intfloat-multilingual-e5-small-onnx.zip"
}

```
This returns a task id you can check whether the registration succeeded by running 
```
GET /_plugins/_ml/tasks/your_task_id
```
After success make sure to take note of the model_id

## 5. Deploy The model

After the registration is complete you can now deploy it
```
POST /_plugins/_ml/models/your_model_id/_deploy
```
Again this returns a task if run the aforementioned get task endpoint to check the status and after sometime
the model id is now in the **DEPLOYED** state.

## 6. Run Inference
Wit the model now deployed you can run inference by seeing embeddings. In this secnario you can specify two types of embeddings
one for passages and one for queries.

For example for embedding a passage you can run this
```
POST /_plugins/_ml/_predict/text_embedding/your_model_id
{                    
  "parameters" : {
    "content_type" : "passage"
  },
  "text_docs":[ "Today is Friday, tomorrow will be my break day, After that I will go to the library, when is lunch?"], 
  "target_response": ["sentence_embedding"]
}
```
you should see a similar embedding of size 384.
```json
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
            0.0419328,
            0.047480892,
            ...
            0.31158513,
            0.21784715,
            0.29523832
          ]
        }
      ]
    }
  ]
}
```

Here is an example of a query embedding.
```
POST /_plugins/_ml/_predict/text_embedding/your_model_id
{                    
  "parameters" : {
    "content_type" : "query"
  },
  "text_docs": ["What day is it today?"],
  "target_response": ["sentence_embedding"]
}
```
which gives back a result
```json
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
            0.2338349,
            -0.13603798,
            ...
            0.37335885,
            0.10653384,
            0.21653183
          ]
        }
      ]
    }
  ]
}
```
## Next steps

- Create an ingest pipeline for your documents with assymetric embeddings
- Run a query using KNN with your asymmetric model


# References

Wang, Liang, et al. (2024). *Multilingual E5 Text Embeddings: A Technical Report*. arXiv preprint arXiv:2402.05672. [Link](https://arxiv.org/abs/2402.05672)
