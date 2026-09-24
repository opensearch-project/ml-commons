"""
LoCoMo Benchmark — OpenSearch Agentic Memory vs mem0

Evaluates long-term memory retrieval quality using the LoCoMo dataset (10 conversations,
1,540 QA questions). Compares BM25, semantic, and hybrid search against mem0 baseline (66.9%).

Usage:
  python3 benchmark.py                # run all 10 conversations
  python3 benchmark.py --max-conv 1   # run only conversation 0
  python3 benchmark.py                # resume from checkpoint

Environment Variables (required):
  OPENSEARCH_URL          OpenSearch endpoint (e.g. https://localhost:9200)
  OPENSEARCH_USER         OpenSearch username
  OPENSEARCH_PASSWORD     OpenSearch password
  AWS_REGION              AWS region for Bedrock (e.g. us-east-1)
  OPENAI_API_KEY          OpenAI API key (for GPT-4o-mini judge)
  EMBEDDING_MODEL_ID      OpenSearch ML model ID for embeddings
  LLM_MODEL_ID            OpenSearch ML model ID for LLM extraction

Optional:
  OPENSEARCH_VERIFY_SSL   Set to "true" to verify SSL (default: false)
  ANSWER_MODEL            Bedrock model ID (default: us.anthropic.claude-sonnet-4-6)
  JUDGE_MODEL             OpenAI model for judging (default: gpt-4o-mini)
"""
import os
import sys
import json
import time
import argparse
import requests
import urllib3
import boto3
import openai
from datetime import datetime
from memory_client import MemoryClient

urllib3.disable_warnings()

# --- Config from environment ---
URL = os.environ["OPENSEARCH_URL"]
USER = os.environ["OPENSEARCH_USER"]
PASSWORD = os.environ["OPENSEARCH_PASSWORD"]
REGION = os.environ.get("AWS_REGION", "us-east-1")
EMBED_ID = os.environ["EMBEDDING_MODEL_ID"]
LLM_ID = os.environ["LLM_MODEL_ID"]
VERIFY_SSL = os.environ.get("OPENSEARCH_VERIFY_SSL", "false").lower() == "true"
ANSWER_MODEL = os.environ.get("ANSWER_MODEL", "us.anthropic.claude-sonnet-4-6")
JUDGE_MODEL = os.environ.get("JUDGE_MODEL", "gpt-4o-mini")
EMBEDDING_DIMENSION = int(os.environ.get("EMBEDDING_DIMENSION", "1024"))

AUTH = (USER, PASSWORD)
INDEX_PREFIX = "locomo"
RESULTS_FILE = "results/benchmark_results.json"
DATASET_FILE = "dataset/locomo10.json"

bedrock = boto3.client("bedrock-runtime", region_name=REGION)
oai = openai.OpenAI(api_key=os.environ["OPENAI_API_KEY"])

CAT_NAMES = {"1": "Single-hop", "2": "Multi-hop", "3": "Temporal", "4": "Commonsense"}

ANSWER_PROMPT = """Answer concisely based on the memories. Infer if needed. Say "I don't know" only if truly absent.

Memories:
{memories}

Question: {question}
Answer:"""

JUDGE_PROMPT = "Is this answer correct?\nGold: {gold}\nGenerated: {generated}\nBe generous with paraphrases. Reply CORRECT or WRONG only."

parser = argparse.ArgumentParser(description="LoCoMo Benchmark for OpenSearch Agentic Memory")
parser.add_argument("--max-conv", type=int, default=10, help="Max conversations to run (default: all 10)")
args = parser.parse_args()


def log(msg):
    print(f'[{datetime.now().strftime("%H:%M:%S")}] {msg}', flush=True)


def call_claude(prompt, max_tokens=80):
    for attempt in range(3):
        try:
            resp = bedrock.converse(
                modelId=ANSWER_MODEL,
                messages=[{"role": "user", "content": [{"text": prompt}]}],
                inferenceConfig={"maxTokens": max_tokens},
            )
            return resp["output"]["message"]["content"][0]["text"].strip()
        except Exception as e:
            log(f"  Claude retry {attempt+1}/3: {type(e).__name__}: {str(e)[:80]}")
            time.sleep(5 * (attempt + 1))
    return ""


def judge(gold, generated):
    for attempt in range(3):
        try:
            resp = oai.chat.completions.create(
                model=JUDGE_MODEL,
                messages=[{"role": "user", "content": JUDGE_PROMPT.format(gold=gold, generated=generated)}],
                max_tokens=10,
            )
            return "CORRECT" in resp.choices[0].message.content.upper()
        except Exception as e:
            log(f"  Judge retry {attempt+1}/3: {type(e).__name__}: {str(e)[:80]}")
            time.sleep(3 * (attempt + 1))
    return False


def search_bm25(question, conv_ns, k=10):
    """BM25 keyword search with namespace filter."""
    filters = [{"term": {f"namespace.{key}": val}} for key, val in conv_ns.items()]
    r = requests.post(
        f"{URL}/{INDEX_PREFIX}-memory-long-term/_search",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={
            "query": {"bool": {"must": {"match": {"memory": question}}, "filter": filters}},
            "size": k,
            "_source": {"excludes": ["memory_embedding"]},
        },
    )
    return [h["_source"].get("memory", "") for h in r.json().get("hits", {}).get("hits", [])]


def search_semantic(client, question, conv_ns, k=10):
    for attempt in range(3):
        try:
            hits = client.semantic_search(question, k=k, namespace=conv_ns)
            return [h["_source"].get("memory", "") for h in hits]
        except Exception as e:
            log(f"  Semantic search retry {attempt+1}/3: {str(e)[:80]}")
            time.sleep(3)
    return []


def search_hybrid(client, question, conv_ns, k=10):
    for attempt in range(3):
        try:
            hits = client.hybrid_search(question, k=k, namespace=conv_ns)
            return [h["_source"].get("memory", "") for h in hits]
        except Exception as e:
            log(f"  Hybrid search retry {attempt+1}/3: {str(e)[:80]}")
            time.sleep(3)
    return []


def answer_from_memories(memories, question):
    if not memories:
        return "I don't know"
    return call_claude(ANSWER_PROMPT.format(memories="\n".join(f"- {m}" for m in memories), question=question))


def get_or_create_container():
    """Get existing container or create one."""
    r = requests.get(
        f"{URL}/_plugins/_ml/memory_containers/_search",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={"query": {"term": {"name.keyword": "locomo-benchmark"}}, "size": 1},
    )
    hits = r.json().get("hits", {}).get("hits", [])
    if hits:
        cid = hits[0]["_id"]
        log(f"Using existing container: {cid}")
        return cid

    log("Creating shared container...")
    r = requests.post(
        f"{URL}/_plugins/_ml/memory_containers/_create",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={
            "name": "locomo-benchmark",
            "configuration": {
                "index_prefix": INDEX_PREFIX,
                "use_system_index": False,
                "embedding_model_type": "TEXT_EMBEDDING",
                "embedding_model_id": EMBED_ID,
                "embedding_dimension": EMBEDDING_DIMENSION,
                "llm_id": LLM_ID,
                "disable_session": True,
                "max_infer_size": 10,
                "parameters": {"llm_result_path": "$.output.message.content[0].text"},
                "strategies": [
                    {"enabled": True, "type": "SEMANTIC", "namespace": ["conv"]},
                    {"enabled": True, "type": "USER_PREFERENCE", "namespace": ["conv"]},
                ],
            },
        },
    )
    cid = r.json().get("memory_container_id")
    if not cid:
        log(f"ERROR creating container: {r.text[:200]}")
        sys.exit(1)
    log(f"Container created: {cid}")
    return cid


# --- Load dataset ---
log(f"Loading {DATASET_FILE}...")
with open(DATASET_FILE) as f:
    data = json.load(f)
total_qa = sum(len([q for q in c["qa"] if str(q.get("category", "")) != "5"]) for c in data)
log(f"Loaded {len(data)} conversations, {total_qa} total questions (excl cat 5)")

# --- Resume support ---
all_results = {}
totals = {"bm25": 0, "semantic": 0, "hybrid": 0, "n": 0}
cat_stats = {c: {"bm25": 0, "semantic": 0, "hybrid": 0, "n": 0} for c in ["1", "2", "3", "4"]}
start_conv = 0
ingested_convs = set()
CONTAINER_ID = None

os.makedirs("results", exist_ok=True)

if os.path.exists(RESULTS_FILE):
    with open(RESULTS_FILE) as f:
        saved = json.load(f)
    all_results = saved.get("results", {})
    totals = saved.get("totals", totals)
    cat_stats = saved.get("cat_stats", cat_stats)
    start_conv = saved.get("last_conv_completed", -1) + 1
    ingested_convs = set(saved.get("ingested_convs", []))
    CONTAINER_ID = saved.get("container_id")
    log(f"RESUMING from conv {start_conv}, {totals['n']}/{total_qa} questions done")

# --- Get or create shared container ---
if not CONTAINER_ID:
    CONTAINER_ID = get_or_create_container()
client = MemoryClient(URL, USER, PASSWORD, CONTAINER_ID, verify_ssl=VERIFY_SSL)

log(f'\n{"="*70}')
log(f"BENCHMARK CONFIG")
log(f"  Container:   {CONTAINER_ID} (shared, namespace-filtered)")
log(f"  Index:       {INDEX_PREFIX}-memory-long-term")
log(f"  Embedding:   {EMBED_ID}")
log(f"  LLM:         {ANSWER_MODEL} (Bedrock Converse)")
log(f"  Judge:       {JUDGE_MODEL} (OpenAI)")
log(f"  Namespace:   per-conversation (conv=conv0, conv=conv1, ...)")
log(f"  Strategies:  SEMANTIC + USER_PREFERENCE")
log(f"  Baseline:    mem0 = 66.9%")
log(f'{"="*70}\n')

benchmark_start = time.time()
convs_to_run = min(args.max_conv, len(data))

# --- Main loop ---
for ci, conv in enumerate(data):
    if ci < start_conv:
        continue
    if ci >= convs_to_run:
        break

    conv_start = time.time()
    log(f'{"="*70}')
    log(f"CONVERSATION {ci}/9")
    log(f'{"="*70}')

    session_keys = sorted(
        [k for k in conv["conversation"] if k.startswith("session_") and "date" not in k],
        key=lambda x: int(x.split("_")[1]),
    )
    qa = [q for q in conv["qa"] if str(q.get("category", "")) != "5"]
    speaker_a = conv["conversation"].get("speaker_a", "?")
    speaker_b = conv["conversation"].get("speaker_b", "?")
    conv_ns = {"conv": f"conv{ci}"}
    log(f"  Speakers: {speaker_a}, {speaker_b} | Sessions: {len(session_keys)} | Questions: {len(qa)}")

    # --- Ingest ---
    if ci not in ingested_convs:
        log(f"  --- INGESTION PHASE ---")
        ingest_start = time.time()
        ingest_ok = ingest_fail = 0
        for si, sk in enumerate(session_keys):
            turns = conv["conversation"][sk]
            date = conv["conversation"].get(sk + "_date_time", "")
            first_speaker = turns[0]["speaker"] if turns else ""
            msgs = [
                {
                    "role": "user" if t["speaker"] == first_speaker else "assistant",
                    "content": [{"text": f'{t["speaker"]}: {t["text"]}', "type": "text"}],
                }
                for t in turns
            ]
            try:
                client.add_memory(msgs, namespace=conv_ns, tags={"timestamp": date}, infer=True)
                ingest_ok += 1
            except Exception as e:
                log(f"    WARN: {sk} failed ({str(e)[:60]}), retrying...")
                time.sleep(5)
                try:
                    client.add_memory(msgs, namespace=conv_ns, tags={"timestamp": date}, infer=True)
                    ingest_ok += 1
                except Exception:
                    ingest_fail += 1
                    log(f"    SKIP: {sk}")
            if (si + 1) % 5 == 0 or si == len(session_keys) - 1:
                log(f"    Ingested {si+1}/{len(session_keys)} sessions ({date})")
            time.sleep(1.5)

        log(f"  Ingestion done: {ingest_ok} ok, {ingest_fail} failed, {time.time()-ingest_start:.0f}s")
        log(f"  Waiting 60s for LLM extraction...")
        time.sleep(60)
        ingested_convs.add(ci)
    else:
        log(f"  Ingestion already done (from checkpoint)")

    # Count memories
    r2 = requests.post(
        f"{URL}/{INDEX_PREFIX}-memory-long-term/_count",
        auth=AUTH,
        verify=VERIFY_SSL,
        json={"query": {"term": {"namespace.conv": f"conv{ci}"}}},
    )
    mem_count = r2.json().get("count", 0)
    log(f"  Long-term memories for conv{ci}: {mem_count}")

    # --- QA Phase ---
    log(f"  --- QA PHASE ({len(qa)} questions) ---")
    qa_start = time.time()
    conv_results = []
    bm25_ok = sem_ok = hyb_ok = 0

    for i, q in enumerate(qa):
        question = q["question"]
        gold = str(q["answer"])
        cat = str(q.get("category", "?"))

        bm25_mems = search_bm25(question, conv_ns)
        sem_mems = search_semantic(client, question, conv_ns)
        hyb_mems = search_hybrid(client, question, conv_ns)

        bm25_ans = answer_from_memories(bm25_mems, question)
        sem_ans = answer_from_memories(sem_mems, question)
        hyb_ans = answer_from_memories(hyb_mems, question)

        b_ok = judge(gold, bm25_ans)
        s_ok = judge(gold, sem_ans)
        h_ok = judge(gold, hyb_ans)

        bm25_ok += b_ok
        sem_ok += s_ok
        hyb_ok += h_ok
        totals["bm25"] += b_ok
        totals["semantic"] += s_ok
        totals["hybrid"] += h_ok
        totals["n"] += 1
        if cat in cat_stats:
            cat_stats[cat]["bm25"] += b_ok
            cat_stats[cat]["semantic"] += s_ok
            cat_stats[cat]["hybrid"] += h_ok
            cat_stats[cat]["n"] += 1

        conv_results.append(
            {
                "question": question,
                "gold": gold,
                "category": cat,
                "bm25_ans": bm25_ans,
                "sem_ans": sem_ans,
                "hyb_ans": hyb_ans,
                "bm25_ok": b_ok,
                "sem_ok": s_ok,
                "hyb_ok": h_ok,
            }
        )

        status = f'B:{"✓" if b_ok else "✗"} S:{"✓" if s_ok else "✗"} H:{"✓" if h_ok else "✗"}'
        print(f"  [{i+1:3}/{len(qa)}] [{status}] [{CAT_NAMES.get(cat, cat):<12}] {question[:55]}", flush=True)

        if (i + 1) % 20 == 0:
            n = i + 1
            log(f"      BM25={bm25_ok}/{n} ({bm25_ok/n*100:.0f}%) Sem={sem_ok}/{n} ({sem_ok/n*100:.0f}%) Hyb={hyb_ok}/{n} ({hyb_ok/n*100:.0f}%)")

    all_results[f"conv_{ci}"] = conv_results
    n_qa = len(qa)
    log(f"  CONV {ci} COMPLETE ({time.time()-conv_start:.0f}s, {mem_count} memories)")
    log(f"    BM25: {bm25_ok}/{n_qa} ({bm25_ok/n_qa*100:.1f}%) | Semantic: {sem_ok}/{n_qa} ({sem_ok/n_qa*100:.1f}%) | Hybrid: {hyb_ok}/{n_qa} ({hyb_ok/n_qa*100:.1f}%)")

    # Save checkpoint
    with open(RESULTS_FILE, "w") as f:
        json.dump(
            {
                "totals": totals,
                "cat_stats": cat_stats,
                "results": all_results,
                "last_conv_completed": ci,
                "ingested_convs": list(ingested_convs),
                "container_id": CONTAINER_ID,
            },
            f,
            indent=2,
        )

# --- Final summary ---
n = totals["n"]
if n == 0:
    log("No questions answered.")
    sys.exit(0)

mem0_overall = 66.9
mem0_cats = {"1": 69.4, "2": 60.3, "3": 53.8, "4": 70.6}

log(f'\n{"="*70}')
log(f"FINAL RESULTS — LoCoMo Benchmark")
log(f'{"="*70}')
log(f"Total time: {(time.time()-benchmark_start)/60:.0f} minutes | Questions: {n}")
log(f"")
log(f'  {"Method":<18} {"Overall":>10}  {"vs mem0":>10}')
log(f"  {'-'*45}")
for method in ["bm25", "semantic", "hybrid"]:
    score = totals[method] / n * 100
    delta = score - mem0_overall
    log(f'  {method.upper():<18} {score:>7.1f}%     {"+" if delta>=0 else ""}{delta:.1f}%')
log(f'  {"mem0 (baseline)":<18} {mem0_overall:>7.1f}%')
log(f"")
log(f'  {"Category":<18} {"N":>5}  {"BM25":>7}  {"Semantic":>9}  {"Hybrid":>8}  {"mem0":>7}')
log(f"  {'-'*65}")
for cat in ["1", "2", "3", "4"]:
    s = cat_stats[cat]
    if s["n"]:
        log(
            f'  {CAT_NAMES[cat]:<18} {s["n"]:>5}  {s["bm25"]/s["n"]*100:>6.1f}%  '
            f'{s["semantic"]/s["n"]*100:>8.1f}%  {s["hybrid"]/s["n"]*100:>7.1f}%  {mem0_cats[cat]:>6.1f}%'
        )
log(f"\nResults saved to {RESULTS_FILE}")
