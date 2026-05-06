"""
Setup script for LoCoMo Benchmark.

Registers embedding + LLM models and creates the memory container.
Run this ONCE before running benchmark.py.

Prerequisites:
  - OpenSearch 3.6+ cluster running with ML Commons plugin
  - AWS credentials with Bedrock access (for Titan embed + Claude)
  - Environment variables set (see .env.example)

Usage:
  python3 setup.py
"""
import os
import sys
import json
import time
import requests
import urllib3

urllib3.disable_warnings()

URL = os.environ["OPENSEARCH_URL"]
USER = os.environ["OPENSEARCH_USER"]
PASSWORD = os.environ["OPENSEARCH_PASSWORD"]
REGION = os.environ.get("AWS_REGION", "us-east-1")
VERIFY_SSL = os.environ.get("OPENSEARCH_VERIFY_SSL", "false").lower() == "true"
EMBEDDING_DIMENSION = int(os.environ.get("EMBEDDING_DIMENSION", "1024"))

AUTH = (USER, PASSWORD)


def get_aws_credentials():
    """Get AWS credentials from environment or AWS config."""
    import boto3
    session = boto3.Session()
    creds = session.get_credentials().get_frozen_credentials()
    return creds.access_key, creds.secret_key, creds.token


def enable_agentic_memory():
    """Enable agentic memory cluster settings."""
    r = requests.put(
        f"{URL}/_cluster/settings",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={
            "persistent": {
                "plugins.ml_commons.agentic_memory_enabled": True,
                "plugins.ml_commons.only_run_on_ml_node": False,
                "plugins.ml_commons.trusted_connector_endpoints_regex": ["^.*$"],
            }
        },
    )
    r.raise_for_status()
    print("✓ Agentic memory enabled")


def register_model(name, connector):
    """Register and deploy a model."""
    r = requests.post(
        f"{URL}/_plugins/_ml/models/_register",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={"name": name, "function_name": "remote", "connector": connector},
    )
    r.raise_for_status()
    model_id = r.json()["model_id"]

    requests.post(f"{URL}/_plugins/_ml/models/{model_id}/_deploy", auth=AUTH, verify=VERIFY_SSL)
    time.sleep(2)
    print(f"✓ Registered and deployed: {name} → {model_id}")
    return model_id


def main():
    ak, sk, st = get_aws_credentials()
    if not ak:
        print("ERROR: No AWS credentials found. Run 'aws configure' or set AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY.")
        sys.exit(1)

    print(f"OpenSearch: {URL}")
    print(f"Region: {REGION}")
    print(f"Embedding dimension: {EMBEDDING_DIMENSION}")
    print()

    # Step 1: Enable agentic memory
    enable_agentic_memory()

    # Step 2: Register embedding model (Bedrock Titan v2)
    embed_id = register_model(
        "benchmark-embed-titan",
        {
            "name": "embed",
            "version": 1,
            "protocol": "aws_sigv4",
            "parameters": {
                "region": REGION,
                "service_name": "bedrock",
                "model": "amazon.titan-embed-text-v2:0",
                "dimensions": EMBEDDING_DIMENSION,
                "normalize": True,
                "embeddingTypes": ["float"],
            },
            "credential": {"access_key": ak, "secret_key": sk, "session_token": st},
            "actions": [
                {
                    "action_type": "predict",
                    "method": "POST",
                    "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
                    "headers": {"content-type": "application/json", "x-amz-content-sha256": "required"},
                    "request_body": '{ "inputText": "${parameters.inputText}", "dimensions": ${parameters.dimensions}, "normalize": ${parameters.normalize}, "embeddingTypes": ${parameters.embeddingTypes} }',
                    "pre_process_function": "connector.pre_process.bedrock.embedding",
                    "post_process_function": "connector.post_process.bedrock.embedding",
                }
            ],
        },
    )

    # Step 3: Register LLM (Bedrock Claude via Converse API)
    llm_id = register_model(
        "benchmark-llm-claude",
        {
            "name": "claude",
            "version": 1,
            "protocol": "aws_sigv4",
            "parameters": {"region": REGION, "service_name": "bedrock", "model": "us.anthropic.claude-sonnet-4-6"},
            "credential": {"access_key": ak, "secret_key": sk, "session_token": st},
            "actions": [
                {
                    "action_type": "predict",
                    "method": "POST",
                    "headers": {"content-type": "application/json"},
                    "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse",
                    "request_body": '{"system":[{"text":"${parameters.system_prompt}"}],"messages":[{"role":"user","content":[{"text":"${parameters.user_prompt}"}]}]}',
                }
            ],
        },
    )

    # Step 4: Verify models work
    print("\nVerifying models...")
    r = requests.post(
        f"{URL}/_plugins/_ml/models/{embed_id}/_predict",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={"parameters": {"inputText": "test"}},
    )
    assert "inference_results" in r.json(), f"Embedding model failed: {r.text[:200]}"
    print("✓ Embedding model works")

    r = requests.post(
        f"{URL}/_plugins/_ml/models/{llm_id}/_predict",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={"parameters": {"system_prompt": "Reply OK", "user_prompt": "test"}},
    )
    assert "inference_results" in r.json(), f"LLM model failed: {r.text[:200]}"
    print("✓ LLM model works")

    # Print env vars for benchmark.py
    print(f"\n{'='*60}")
    print("Setup complete! Add these to your .env file:")
    print(f"{'='*60}")
    print(f"EMBEDDING_MODEL_ID={embed_id}")
    print(f"LLM_MODEL_ID={llm_id}")
    print(f"EMBEDDING_DIMENSION={EMBEDDING_DIMENSION}")
    print(f"\nThen run: python3 benchmark.py --max-conv 1")


if __name__ == "__main__":
    main()
