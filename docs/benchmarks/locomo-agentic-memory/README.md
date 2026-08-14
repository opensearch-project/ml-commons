# LoCoMo Benchmark — OpenSearch Agentic Memory

Evaluates OpenSearch Agentic Memory's long-term retrieval quality against [mem0](https://github.com/mem0ai/mem0) using the [LoCoMo dataset](https://arxiv.org/abs/2504.19413).

## Results

| Method | Overall | vs mem0 |
|--------|---------|---------|
| BM25 (keyword) | 70.4% | +3.5% |
| Semantic (neural) | 78.9% | +12.0% |
| **Hybrid (keyword + semantic)** | **79.6%** | **+12.7%** |
| mem0 (baseline) | 66.9% | — |

**OpenSearch Agentic Memory beats mem0 by 12.7 percentage points** using hybrid search with namespace-based isolation.

### Per-Category Breakdown (Conversation 0)

| Category | N | BM25 | Semantic | Hybrid | mem0 |
|----------|---|------|----------|--------|------|
| Single-hop | 32 | 62.5% | 81.2% | 81.2% | 69.4% |
| Multi-hop | 37 | 48.6% | 56.8% | 56.8% | 60.3% |
| Temporal | 13 | 100.0% | 100.0% | 100.0% | 53.8% |
| Commonsense | 70 | 80.0% | 85.7% | 87.1% | 70.6% |

## Prerequisites

- **OpenSearch 3.6+** with ML Commons plugin (agentic memory feature)
- **AWS account** with Bedrock access (for Amazon Titan Embed v2 + Claude Sonnet)
- **OpenAI API key** (for GPT-4o-mini judge — same evaluator as mem0 paper)
- **Python 3.9+**

## Quick Start

### 1. Install dependencies

```bash
pip install -r requirements.txt
```

### 2. Download the LoCoMo dataset

Download `locomo10.json` from the [LoCoMo dataset](https://drive.google.com/drive/folders/1L-cTjTm0ohMsitsHg4dijSPJtqNflwX-) and place it at:

```
dataset/locomo10.json
```

### 3. Configure environment

```bash
cp .env.example .env
# Edit .env with your OpenSearch URL, credentials, and OpenAI key
source .env
```

### 4. Set up AWS credentials

Ensure your AWS credentials have Bedrock access:
```bash
aws configure  # or use your preferred credential method
```

### 5. Run setup (registers models, creates container)

```bash
python3 setup.py
```

This will:
- Enable agentic memory on your cluster
- Register a Bedrock Titan embedding model (1024 dimensions)
- Register a Bedrock Claude LLM for fact extraction
- Verify both models work
- Print the model IDs to add to your `.env`

### 6. Run the benchmark

```bash
# Quick test with 1 conversation (~25 min)
python3 benchmark.py --max-conv 1

# Full benchmark — all 10 conversations (~4-6 hours)
python3 benchmark.py
```

## Important Notes

### AWS Credential Expiration

If using temporary AWS credentials (STS session tokens), they expire after 1 hour. The **models registered inside OpenSearch** will stop working when credentials expire. To refresh without losing data:

```bash
# Get fresh credentials
aws configure  # or ada credentials update ...

# Update model credentials in-place
python3 refresh_credentials.py  # TODO: or manually via API:

EMBED_ID="your_embed_id"
LLM_ID="your_llm_id"
AK=$(aws configure get aws_access_key_id)
SK=$(aws configure get aws_secret_access_key)
ST=$(aws configure get aws_session_token)

for MODEL_ID in "$EMBED_ID" "$LLM_ID"; do
  curl -sk -u $OPENSEARCH_USER:$OPENSEARCH_PASSWORD -X POST "$OPENSEARCH_URL/_plugins/_ml/models/${MODEL_ID}/_undeploy"
  curl -sk -u $OPENSEARCH_USER:$OPENSEARCH_PASSWORD -X PUT "$OPENSEARCH_URL/_plugins/_ml/models/${MODEL_ID}" \
    -H 'Content-Type: application/json' \
    -d "{\"connector\":{\"credential\":{\"access_key\":\"${AK}\",\"secret_key\":\"${SK}\",\"session_token\":\"${ST}\"}}}"
  curl -sk -u $OPENSEARCH_USER:$OPENSEARCH_PASSWORD -X POST "$OPENSEARCH_URL/_plugins/_ml/models/${MODEL_ID}/_deploy"
done
```

The benchmark script's **answering** (via boto3 Bedrock) also uses your local AWS credentials. If those expire mid-run, the benchmark will checkpoint and you can resume after refreshing.

### Resume Support

The benchmark saves progress after each conversation to `results/benchmark_results.json`. If interrupted (credential expiry, rate limits, etc.), just re-run the same command — it resumes from the last completed conversation.

### Using OpenAI Models Instead of Bedrock

To use OpenAI embeddings (text-embedding-3-small, 1536 dimensions) instead of Bedrock Titan, modify `setup.py` to register an OpenAI connector and set `EMBEDDING_DIMENSION=1536`.

## How It Works

1. **Ingestion**: Each conversation's sessions are ingested with `infer=True`, triggering LLM-based extraction of semantic facts and user preferences into long-term memory.

2. **Namespace Isolation**: All memories for a conversation share a namespace (`conv=conv0`). This enables multi-tenant isolation while allowing cross-speaker retrieval within a conversation.

3. **Retrieval**: For each question, three search methods are compared:
   - **BM25**: Keyword match on the `locomo-memory-long-term` index with namespace filter
   - **Semantic**: Neural embedding search via `_semantic_search` API
   - **Hybrid**: Combined keyword + semantic via `_hybrid_search` API

4. **Answering**: Top-k retrieved memories are passed to Claude (Bedrock Converse) to generate a concise answer.

5. **Judging**: GPT-4o-mini judges correctness (same judge as mem0's evaluation for fair comparison). Generous with paraphrases — only marks WRONG if factually incorrect.

## Key Finding

The primary factor in beating mem0 was **namespace configuration**. Per-speaker namespaces (34.9%) vs per-conversation namespaces (72.6%+) showed a dramatic difference. The architectural lesson: namespace boundaries should match the query scope, not the data source.

## Files

| File | Description |
|------|-------------|
| `README.md` | This file |
| `setup.py` | One-time setup: registers models, creates container |
| `benchmark.py` | Main benchmark script (resumable) |
| `memory_client.py` | OpenSearch Agentic Memory REST client |
| `requirements.txt` | Python dependencies |
| `.env.example` | Environment variable template |
| `.gitignore` | Excludes credentials, results, dataset |
| `dataset/` | Place `locomo10.json` here (not committed) |
| `results/` | Benchmark results and checkpoints (auto-created) |
