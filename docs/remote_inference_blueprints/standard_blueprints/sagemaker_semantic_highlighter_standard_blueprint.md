# AWS SageMaker Semantic Highlighter Model Standard Blueprint

This blueprint demonstrates how to deploy a semantic highlighter model using AWS SageMaker and integrate it with OpenSearch. For a detailed Python-based tutorial on deploying the model to SageMaker, please refer to the [Deploying OpenSearch Sentence Highlighter Model To AWS SageMaker Guide](https://github.com/opensearch-project/opensearch-py-ml/blob/main/docs/source/examples/aws_sagemaker_sentence_highlighter_model/README.md).

## Overview

The semantic highlighter model helps identify and highlight the most relevant parts of a text passage based on a given question. This blueprint shows how to:

1. Create a SageMaker connector
2. Register a model group
3. Register and deploy the model
4. Test the model inference

## Prerequisites

1. AWS account with SageMaker access
2. SageMaker endpoint deployed with the semantic highlighter model
3. AWS credentials with appropriate permissions

## Steps

### 1. Create SageMaker Connector

```json
POST /_plugins/_ml/connectors/_create
{
  "name": "semantic_highlighter_connector",
  "description": "Connector for semantic highlighter model on SageMaker",
  "version": "1",
  "protocol": "aws_sigv4",
  "parameters": {
    "region": "<YOUR_AWS_REGION>",
    "service_name": "sagemaker",
    "endpoint": "runtime.sagemaker.{region}.amazonaws.com",
    "model": "{your-endpoint-name}"
  },
  "credential": {
    "access_key": "<YOUR_AWS_ACCESS_KEY>",
    "secret_key": "<YOUR_AWS_SECRET_KEY>",
    "session_token": "<YOUR_AWS_SESSION_TOKEN>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "content-type": "application/json"
      },
      "url": "https://runtime.sagemaker.${parameters.region}.amazonaws.com/endpoints/${parameters.model}/invocations",
      "request_body": "{ \"question\": \"${parameters.question}\", \"context\": \"${parameters.context}\" }",
      "pre_process_function": "// Extract question and context directly from params\nif (params.question != null && params.context != null) {\n    return '{\"parameters\":{\"question\":\"' + params.question + '\",\"context\":\"' + params.context + '\"}}'; \n} \nelse {\n    throw new IllegalArgumentException(\"Missing required parameters: question and context\");\n}"
    }
  ]
}
```

Replace the placeholders:
- `<YOUR_AWS_REGION>`: Your AWS region (e.g., us-east-1)
- `<YOUR_AWS_ACCESS_KEY>`: Your AWS access key
- `<YOUR_AWS_SECRET_KEY>`: Your AWS secret key
- `<YOUR_AWS_SESSION_TOKEN>`: Your AWS session token (if using temporary credentials)
- `{your-endpoint-name}`: Your SageMaker endpoint name

### 2. Create Model Group

```json
POST /_plugins/_ml/model_groups/_register
{
  "name": "semantic_highlighter_group",
  "description": "Model group for semantic highlighter"
}
```

### 3. Register and Deploy Model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "semantic_highlighter_model",
  "function_name": "remote",
  "model_group_id": "<MODEL_GROUP_ID>",
  "description": "Semantic highlighter model for text highlighting",
  "connector_id": "<CONNECTOR_ID>"
}
```

Replace:
- `<MODEL_GROUP_ID>`: The model group ID from step 2
- `<CONNECTOR_ID>`: The connector ID from step 1

### 4. Test Model Inference

```json
POST /_plugins/_ml/models/<MODEL_ID>/_predict
{
  "parameters": {
      "question": "What are the symptoms of heart failure?",
      "context": "Hypertensive heart disease is the No. 1 cause of death associated with high blood pressure. It refers to a group of disorders that includes heart failure, ischemic heart disease, and left ventricular hypertrophy (excessive thickening of the heart muscle). Heart failure does not mean the heart has stopped working. Rather, it means that the heart's pumping power is weaker than normal or the heart has become less elastic. With heart failure, blood moves through the heart's pumping chambers less effectively, and pressure in the heart increases, making it harder for your heart to deliver oxygen and nutrients to your body. To compensate for reduced pumping power, the heart's chambers respond by stretching to hold more blood. This keeps the blood moving, but over time, the heart muscle walls may weaken and become unable to pump as strongly. As a result, the kidneys often respond by causing the body to retain fluid (water) and sodium. The resulting fluid buildup in the arms, legs, ankles, feet, lungs, or other organs, and is called congestive heart failure. High blood pressure may also bring on heart failure by causing left ventricular hypertrophy, a thickening of the heart muscle that results in less effective muscle relaxation between heart beats. This makes it difficult for the heart to fill with enough blood to supply the body's organs, especially during exercise, leading your body to hold onto fluids and your heart rate to increase. Symptoms of heart failure include: Shortness of breath Swelling in the feet, ankles, or abdomen Difficulty sleeping flat in bed Bloating Irregular pulse Nausea Fatigue Greater need to urinate at night High blood pressure can also cause ischemic heart disease. This means that the heart muscle isn't getting enough blood. Ischemic heart disease is usually the result of atherosclerosis or hardening of the arteries (coronary artery disease), which impedes blood flow to the heart. Symptoms of ischemic heart disease may include: Chest pain which may radiate (travel) to the arms, back, neck, or jaw Chest pain with nausea, sweating, shortness of breath, and dizziness; these associated symptoms may also occur without chest pain Irregular pulse Fatigue and weakness Any of these symptoms of ischemic heart disease warrant immediate medical evaluation. Your doctor will look for certain signs of hypertensive heart disease, including: High blood pressure Enlarged heart and irregular heartbeat Fluid in the lungs or lower extremities Unusual heart sounds Your doctor may perform tests to determine if you have hypertensive heart disease, including an electrocardiogram, echocardiogram, cardiac stress test, chest X-ray, and coronary angiogram. In order to treat hypertensive heart disease, your doctor has to treat the high blood pressure that is causing it. He or she will treat it with a variety of drugs, including diuretics, beta-blockers, ACE inhibitors, calcium channel blockers, angiotensin receptor blockers, and vasodilators. In addition, your doctor may advise you to make changes to your lifestyle, including: Diet: If heart failure is present, you should lower your daily intake of sodium to 1,500 mg or 2 g or less per day, eat foods high in fiber and potassium, limit total daily calories to lose weight if necessary, and limit intake of foods that contain refined sugar, trans fats, and cholesterol. Monitoring your weight: This involves daily recording of weight, increasing your activity level (as recommended by your doctor), resting between activities more often, and planning your activities. Avoiding tobacco products and alcohol Regular medical checkups: During follow-up visits, your doctor will make sure you are staying healthy and that your heart disease is not getting worse."
  }
}
```

Replace `<MODEL_ID>` with your deployed model ID.

## Example Response

```json
{
  "inference_results": [
    {
      "output": [
        {
          "highlights": [
            {
              "start": 1454,
              "end": 1654,
              "text": "Symptoms of heart failure include: Shortness of breath Swelling in the feet, ankles, or abdomen Difficulty sleeping flat in bed Bloating Irregular pulse Nausea Fatigue Greater need to urinate at night",
              "position": 1
            }
          ]
        }
      ],
      "status_code": 200
    }
  ]
}
```

## References
- [Deploying OpenSearch Sentence Highlighter Model To AWS SageMaker Guide](https://github.com/opensearch-project/opensearch-py-ml/docs/source/examples/aws_sagemaker_sentence_highlighter_model/README.md)
- [Using OpenSearch Semantic Highlighting Guide](https://docs.opensearch.org/docs/latest/tutorials/vector-search/semantic-highlighting-tutorial/)
- [OpenSearch ML Commons Documentation](https://opensearch.org/docs/latest/ml-commons-plugin/remote-models/index/)
- [SageMaker Endpoints Documentation](https://docs.aws.amazon.com/sagemaker/latest/dg/deploy-model.html)
