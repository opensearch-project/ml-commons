# Topic

This doc introduces how to build semantic search in Amazon managed OpenSearch with [AWS CloudFormation](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/cfn-template.html) and Sagemaker.
If you are not using Amazon OpenSearch, you can refer to [sagemaker_connector_blueprint](https://github.com/opensearch-project/ml-commons/blob/main/docs/remote_inference_blueprints/sagemaker_connector_blueprint.md) and [OpenSearch semantic search](https://opensearch.org/docs/latest/search-plugins/semantic-search/).

The CloudFormation integration will automate the manual process in this [semantic_search_with_sagemaker_embedding_model tutorial](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/semantic_search_with_sagemaker_embedding_model.md).

The CloudFormation template will create IAM role and then use Lambda function to create AI connector and model.

Make sure your sageMaker model inputs follow the format as an array of Strings, so the [default pre-process function](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/#preprocessing-function) can work
```
["hello world", "how are you"]
```
and output follow such format as an array of array, with each array corresponds to the result of an input String, so the [default post-process function](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/blueprints/#post-processing-function) can work
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

If your model input/output is not the same with default, you can build your own pre/post process function with [painless script](https://opensearch.org/docs/latest/api-reference/script-apis/exec-script/).

For example, Bedrock Titan embedding model ([blueprint](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/remote_inference_blueprints/bedrock_connector_titan_embedding_blueprint.md#2-create-connector-for-amazon-bedrock)) input is 
```
{ "inputText": "your_input_text" }
```
Neural-search plugin will send such input to ml-commons
```
{ "text_docs": [ "your_input_text1", "your_input_text2"] }
```
So you need to build such pre-process function to transform `text_docs` to `inputText`:
```
"pre_process_function": """
    StringBuilder builder = new StringBuilder();
    builder.append("\"");
    String first = params.text_docs[0];// Get the first doc, ml-commons will iterate all docs
    builder.append(first);
    builder.append("\"");
    def parameters = "{" +"\"inputText\":" + builder + "}"; // This is the Bedrock Titan embedding model input
    return  "{" +"\"parameters\":" + parameters + "}";"""
```

Default Bedrock Titan embedding model output:
```
{
  "embedding": <float_array>
}
```
But neural-search plugin expects such format
```
{
  "name": "sentence_embedding",
  "data_type": "FLOAT32",
  "shape": [ <embedding_size> ],
  "data": <float_array>
}
```
Similarly, you need to build post-process function to transform Bedrock Titan embedding model output, so neural-search plugin can recognize:

```
"post_process_function": """
      def name = "sentence_embedding";
      def dataType = "FLOAT32";
      if (params.embedding == null || params.embedding.length == 0) {
        return params.message;
      }
      def shape = [params.embedding.length];
      def json = "{" +
                 "\"name\":\"" + name + "\"," +
                 "\"data_type\":\"" + dataType + "\"," +
                 "\"shape\":" + shape + "," +
                 "\"data\":" + params.embedding +
                 "}";
      return json;
    """
```

Note: You should replace the placeholders with prefix `your_` with your own value.

# Steps

## 0. Create OpenSearch cluster

Go to AWS OpenSearch console UI and create OpenSearch domain.

Note the domain ARN, which will be used in next step.

## 1. Map backend role

AWS OpenSearch Integration CloudFormation template will use Lambda to create AI connector with some IAM role. You need to 
map IAM role to `ml_full_access` to grant it permission. 
You can refer to [semantic_search_with_sagemaker_embedding_model#map-backend-role](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/tutorials/aws/semantic_search_with_sagemaker_embedding_model.md#22-map-backend-role) part. 

The IAM role can be located in the "Lambda Invoke OpenSearch ML Commons Role Name" field on the CloudFormation template. Please refer to the screenshot in step 2.1.

The default IAM role is `LambdaInvokeOpenSearchMLCommonsRole`, so you need to map this backend role `arn:aws:iam::your_aws_account_id:role/LambdaInvokeOpenSearchMLCommonsRole` to `ml_full_access`.

For quick start, you can also map all roles to `ml_full_access` with wildcard `arn:aws:iam::your_aws_account_id:role/*`

As `all_access` has more permission than `ml_full_access`, it's ok to map backend role to `all_acess`.


## 2. Run CloudFormation template

You can find CloudFormation template integration on AWS OpenSearch console.

![Alt text](images/semantic_search/semantic_search_remote_model_Integration_1.png)

For all options below, you can find OpenSearch AI connector and model id in "Outputs" of CloudFormation stack when it completes.

If  you see any failure, you can find log on Sagemaker Console by searching "Log Groups" with CloudFormation stack name.

### 2.1 Option1: Deploy pretrained model to Sagemaker

You can deploy pretrained Huggingface sentence-transformer embedding model from [DJL](https://djl.ai/) model repo.

Keep other fields as default value if not mentioned below:

1. You must fill your "Amazon OpenSearch Endpoint"
2. You can use default setting of "Sagemaker Configuration" part for quick start. If necessary, you can fine tune the values. You can find all supported Sagemaker instance type [here](https://aws.amazon.com/sagemaker/pricing/).
3. You must leave "SageMaker Endpoint Url" as empty. By inputting this value, you will not deploy model to Sagemaker to create a new inference endpoint.
4. You can leave "Custom Image" part as empty, it will use `djl-inference:0.22.1-cpu-full` as default value. You can find more available image from [this document](https://docs.aws.amazon.com/deep-learning-containers/latest/devguide/deep-learning-containers-images.html).
5. You must leave "Custom Model Data Url" as empty for this option.
6. The default value "Custom Model Environment" is `djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2`, find all supported model in Appendix part of this doc.

![Alt text](images/semantic_search/semantic_search_remote_model_Integration_2.png)


### 2.2 Option2: Create model with your existing Sagemaker inference endpoint

If you already have a Sagemaker inference endpoint, you can create remote model directly with it.

Keep other fields as default value if not mentioned below:
1. You must fill your "Amazon OpenSearch Endpoint"
2. You must fill your "SageMaker Endpoint Url".
3. You must leave "Custom Image", "Custom Model Data Url" and "Custom Model Environment" as empty.

![Alt text](images/semantic_search/semantic_search_remote_model_Integration_3.png)


# Appendix
## Huggingface sentence-transformer embedding model in DJL model repo
```
djl://ai.djl.huggingface.pytorch/sentence-transformers/LaBSE/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L12-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L12-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-distilroberta-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-mpnet-base-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-mpnet-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/all-roberta-large-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/allenai-specter/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-base-nli-cls-token/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-base-nli-max-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-base-nli-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-base-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-base-wikipedia-sections-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-large-nli-cls-token/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-large-nli-max-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-large-nli-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/bert-large-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/clip-ViT-B-32-multilingual-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/distilbert-base-nli-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/distilbert-base-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/distilbert-base-nli-stsb-quora-ranking/
djl://ai.djl.huggingface.pytorch/sentence-transformers/distilbert-multilingual-nli-stsb-quora-ranking/
djl://ai.djl.huggingface.pytorch/sentence-transformers/distiluse-base-multilingual-cased-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/facebook-dpr-ctx_encoder-multiset-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/facebook-dpr-ctx_encoder-single-nq-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/facebook-dpr-question_encoder-multiset-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/facebook-dpr-question_encoder-single-nq-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-MiniLM-L-12-v3/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-MiniLM-L-6-v3/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-MiniLM-L12-cos-v5/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-MiniLM-L6-cos-v5/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-bert-base-dot-v5/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-bert-co-condensor/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-base-dot-prod-v3/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-base-tas-b/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-base-v3/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-base-v4/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-cos-v5/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-dot-v5/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-multilingual-en-de-v2-tmp-lng-aligned/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilbert-multilingual-en-de-v2-tmp-trained-scratch/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-distilroberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-roberta-base-ance-firstp/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-roberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/msmarco-roberta-base-v3/
djl://ai.djl.huggingface.pytorch/sentence-transformers/multi-qa-MiniLM-L6-cos-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/multi-qa-MiniLM-L6-dot-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/multi-qa-distilbert-cos-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/multi-qa-distilbert-dot-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nli-bert-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nli-bert-large-max-pooling/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nli-distilbert-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nli-distilroberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nli-roberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nli-roberta-large/
djl://ai.djl.huggingface.pytorch/sentence-transformers/nq-distilbert-base-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-MiniLM-L12-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-MiniLM-L3-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-MiniLM-L6-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-TinyBERT-L6-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-albert-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-albert-small-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-distilroberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-mpnet-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-xlm-r-multilingual-v1/
djl://ai.djl.huggingface.pytorch/sentence-transformers/quora-distilbert-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/quora-distilbert-multilingual/
djl://ai.djl.huggingface.pytorch/sentence-transformers/roberta-base-nli-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/roberta-base-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/roberta-large-nli-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/roberta-large-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-bert-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-bert-large/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-distilbert-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-distilroberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-roberta-base-v2/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-roberta-base/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-roberta-large/
djl://ai.djl.huggingface.pytorch/sentence-transformers/stsb-xlm-r-multilingual/
djl://ai.djl.huggingface.pytorch/sentence-transformers/use-cmlm-multilingual/
djl://ai.djl.huggingface.pytorch/sentence-transformers/xlm-r-100langs-bert-base-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/xlm-r-bert-base-nli-stsb-mean-tokens/
djl://ai.djl.huggingface.pytorch/sentence-transformers/xlm-r-distilroberta-base-paraphrase-v1/
```