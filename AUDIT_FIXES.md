# Memory Retention RFC — Audit Fixes

These are the confirmed issues from a 20-agent adversarial audit of the
`feature/memory-retention/rfc-implementation` branch that should be fixed
before ship.

---

## 1. RetentionRule explicit-null field removal doesn't persist

**Problem:**

When a user sends an update to remove a retention limit (e.g.,
`{"retention_policy": {"sessions": {"max_count": null}}}`), the in-memory
merge correctly sets `max_count` to null. But `RetentionRule.toXContent()`
only emits non-null fields, so the serialized update doc omits `max_count`
entirely. OpenSearch's partial-update merge then preserves the stored value,
and the retention job keeps enforcing a cap the user explicitly removed.

**Solution:**

In `RetentionRule.toXContent()`, emit explicit null fields when the field was
explicitly cleared. We need to propagate the `retentionDaysExplicitlySet` and
`maxCountExplicitlySet` flags through the merge (or alternatively, always
replace the entire `retention_policy` object in the update doc rather than
relying on recursive partial merge).

The simplest fix: after the merge in `MemoryConfiguration.update()`, when
constructing the merged RetentionRule, preserve the "explicitly set" flags so
`toXContent()` can emit `builder.nullField(...)` for cleared fields. The
pattern already exists in the same file for the whole-policy opt-out:

```java
} else if (retentionPolicyExplicitlyNull) {
    builder.nullField(RETENTION_POLICY_FIELD);
}
```

Apply the same pattern per-field inside `RetentionRule.toXContent()`.

**Files to modify:**
- `common/src/main/java/org/opensearch/ml/common/memorycontainer/RetentionRule.java`
  - Add logic to `toXContent()` to emit `nullField` when explicitly cleared
  - May need to carry the explicitlySet flags through the merge
- `common/src/main/java/org/opensearch/ml/common/memorycontainer/MemoryConfiguration.java`
  - In the retention policy merge (around line 685), ensure the constructed
    merged RetentionRule preserves explicit-set metadata

---

## 2. ModelGuardrail timeout: set back to 5 seconds

**Problem:**

`ML_GUARDRAIL_TIMEOUT_IN_SECONDS` was raised from 5s to 60s. Combined with
fail-closed semantics (`isAccepted` defaults to false), a hung guardrail model
pins every ML predict thread for 60s then rejects — causing thread-pool
starvation of the `opensearch_ml_predict_remote` pool under moderate load.

**Solution:**

Change the constant back to 5 seconds:

```java
public static final long ML_GUARDRAIL_TIMEOUT_IN_SECONDS = 5;
```

**File to modify:**
- `common/src/main/java/org/opensearch/ml/common/model/ModelGuardrail.java` (line 56)

---

## 3. Session max_count eviction: add count short-circuit

**Problem:**

`identifyCountBasedExpiredSessions` → `identifyCountBasedPage` enumerates ALL
non-pinned sessions at 100/page (sorted created_time DESC), counting until it
passes `maxCount`. A container with 100k sessions and max_count=100k issues
1,000 sequential search requests per run to delete nothing.

The long-term (`executeLongTermMaxCount`) and history paths already have a
`size(0)/trackTotalHits` pre-count that short-circuits when total <= maxCount.
The session path is missing this.

**Solution:**

Mirror the pattern from `executeLongTermMaxCount` (line 870):

1. First: do a `size(0), trackTotalHits(true)` count query for non-pinned
   sessions in this container
2. If `totalCount <= maxCount`: return empty set immediately (no excess)
3. If `totalCount > maxCount`: compute `excess = totalCount - maxCount`, then
   use `collectOldestDocIds(sessionIndex, containerId, CREATED_TIME_FIELD, excess, ...)`
   to fetch only the excess oldest session IDs

This replaces the current `identifyCountBasedPage` approach entirely for
sessions, making it consistent with LT and history.

**Files to modify:**
- `plugin/src/main/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessor.java`
  - Replace `identifyCountBasedExpiredSessions` / `identifyCountBasedPage`
    with the count-first pattern

---

## 4. Orphan sweep: O(total docs) enumeration → O(unique sessions)

**Problem:**

`enumerateSessionIdsFromWorkingMemory()` pages through every working-memory
document in the container (at 1000 docs/page) to extract distinct
`namespace.session_id` values. For 5M working-memory messages across 2,000
sessions, this is 5,000 sequential search requests.

The `namespace` field is mapped as `flat_object` in `ml_memory_working.json`
(line 42-44). `flat_object` does NOT support terms aggregations or field
collapsing, so we cannot simply do `collapse("namespace.session_id")` or a
composite aggregation.

**Solution — add a top-level `session_id` keyword field:**

The cleanest fix is to denormalize `session_id` into a top-level keyword field
on working-memory documents, enabling efficient aggregation/collapse.

### Step 1: Add field to index mapping

In `common/src/main/resources/index-mappings/ml_memory_working.json`:

```json
"session_id": {
  "type": "keyword"
}
```

### Step 2: Populate on write

In `TransportAddMemoriesAction.java`, where working memory documents are
indexed, the session_id is already available from
`input.getNamespace().get(SESSION_ID_FIELD)`. Add it as a top-level field in
the index request source alongside `namespace`.

### Step 3: Use composite/terms aggregation in orphan sweep

Replace `enumerateSessionIdsFromWorkingMemory()` with a composite aggregation:

```java
CompositeAggregationBuilder agg = new CompositeAggregationBuilder("session_ids",
    List.of(new TermsValuesSourceBuilder("sid").field("session_id")))
    .size(ORPHAN_SESSION_BATCH_SIZE);
```

Page through the composite aggregation using `after_key` — each page returns
up to 1000 unique session IDs directly, with cost proportional to unique
sessions, not total documents.

### Step 4: Update queries

The orphan sweep queries (`cascadeDeleteWorkingMemory`,
`deleteOrphanedWorkingMemory`, etc.) currently filter on
`namespace.session_id`. Update them to use the top-level `session_id` field
for better performance (keyword vs flat_object subpath).

### Backward compatibility note

Existing working-memory documents won't have the new `session_id` field. The
orphan sweep should fall back to the current `namespace.session_id` exists
query for documents missing the top-level field, or run a one-time backfill.
Alternatively, accept that pre-upgrade orphans use the old (slower) path until
they're naturally cleaned up.

**Files to modify:**
- `common/src/main/resources/index-mappings/ml_memory_working.json`
- `plugin/src/main/java/org/opensearch/ml/action/memorycontainer/memory/TransportAddMemoriesAction.java`
- `plugin/src/main/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessor.java`
  (enumerateSessionIdsFromWorkingMemory, cascadeDeleteWorkingMemory,
  deleteOrphanedWorkingMemory)

---

## 5. Orphan sweep: ORPHAN_SESSION_ID_CAP never advances across runs

**Problem:**

The orphan enumeration always starts from `searchAfter=null` sorted `_id ASC`
and stops at 50k unique session IDs. If the first 50k in _id order are all
live sessions, orphaned working memory beyond the cap is never examined on any
subsequent run.

**Solution:**

Randomize the sort key so each run starts from a different position in the
keyspace. Over multiple runs, all regions are probabilistically covered.

Replace:
```java
.sort("_id", SortOrder.ASC)
```

With a random score sort (or rotate based on run count):
```java
.sort(SortBuilders.scriptSort(
    new Script(ScriptType.INLINE, "painless",
        "doc['_id'].value.hashCode() ^ params.seed", Map.of("seed", System.currentTimeMillis())),
    ScriptSortBuilder.ScriptSortType.NUMBER))
```

Alternatively (simpler): use a hash of (container_id + run_timestamp) as a
prefix filter or sort tiebreaker so each run covers a different slice.

Also update the log message from "Remaining orphans will be caught on next
run" to "Remaining orphans will be covered in future runs" since coverage is
now probabilistic rather than guaranteed-next-run.

If the fix in #4 above (composite aggregation on top-level session_id) is
implemented, this cap becomes much less likely to be hit since enumeration
cost drops by orders of magnitude and the cap could be raised significantly.

**Files to modify:**
- `plugin/src/main/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessor.java`
  (enumerateSessionIdsFromWorkingMemory)

---

## 6. Unconditional 5s throttle between containers → throttle only after deletions

**Problem:**

`scheduleNext()` delays 5 seconds after EVERY container, including containers
that needed zero work. At 10k containers × 5s = ~14 hours of pure sleep per
run, potentially exceeding the 24h job interval.

**Solution:**

Only throttle after containers where deletions actually occurred. Containers
that were skipped (opt-out, no policy, no expired data, backfill-only) should
proceed immediately to the next container.

Change `processContainer` to track whether any deletions happened and pass
that to the chain:

```java
private void processContainer(SearchHit hit, ActionListener<Boolean> listener) {
    // ... existing logic ...
    // listener.onResponse(true) when deletions occurred
    // listener.onResponse(false) when no deletions (skip, opt-out, etc.)
}

private void processContainerChain(SearchHit[] hits, int index, Object[] nextPageSortValues) {
    if (index >= hits.length) { ... }

    processContainer(hits[index], ActionListener.wrap(hadDeletions -> {
        if (hadDeletions) {
            scheduleNext(hits, index + 1, nextPageSortValues);
        } else {
            // No deletions — proceed immediately
            processContainerChain(hits, index + 1, nextPageSortValues);
        }
    }, e -> {
        log.error(...);
        processContainerChain(hits, index + 1, nextPageSortValues);
    }));
}
```

This means a run over 10k containers where only 50 have expired data takes
~250s of throttle instead of ~14 hours.

**Files to modify:**
- `plugin/src/main/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessor.java`
  (processContainer, processContainerChain, executeRetentionPipeline)

---

## 7. Test coverage gaps (6 items)

These are the critical test gaps that leave data-safety paths unprotected
against regressions. Each can be a separate test method.

### 7a. applyDefaultRetentionPolicy — painless backfill script

Test that:
- When cluster defaults are configured (>0) and container has no policy, the
  conditional script update is issued
- When the script returns NOOP (user set policy concurrently), the container
  is skipped (no deletions run)
- When the script succeeds, executeRetentionPipeline runs with the backfilled
  policy
- When all defaults are -1, no update is issued

### 7b. Epoch-second normalization

Test that:
- A session with `last_updated_time` in epoch seconds (e.g., 1752400000) that
  is newer than the retention cutoff when normalized is NOT deleted
- A session with `last_updated_time` in epoch millis that IS older than the
  cutoff IS deleted
- A session with missing/unparseable last_updated_time is SKIPPED (not deleted)

### 7c. buildEpochAwareCutoffQuery structure

Assert that the delete-by-query for long-term and working-memory includes both
range clauses (gte threshold + lt cutoff; lt secondsScaleCutoff) and
minimumShouldMatch=1. A mutation to plain lt(cutoffMillis) should fail the
test.

### 7d. retention_policy:null opt-out in processor

Test that a container with `"retention_policy": null` in source triggers no
searches, no deletes, and no default-policy backfill update.

### 7e. Non-content updates don't bump last_updated_time

Test that updating only `pinned`, `tags`, `metadata`, or `additional_info` on
a session does NOT include `last_updated_time` in the indexed doc. A mutation
making `shouldBumpLastUpdatedTime` always return true should fail.

### 7f. Session last_updated_time bump on add-memories

Test that:
- Adding a message to an existing session issues a client.update with
  LAST_UPDATED_TIME_FIELD
- Adding a message to a newly created session does NOT issue the bump (session
  was just created with current time)
- The bump failure does not fail the add-memories response

**Files to modify:**
- `plugin/src/test/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessorTests.java`
- `plugin/src/test/java/org/opensearch/ml/action/memorycontainer/memory/TransportUpdateMemoryActionTests.java`
- `plugin/src/test/java/org/opensearch/ml/action/memorycontainer/memory/TransportAddMemoriesActionTests.java`

---

## 8. Add document IDs to retention job logs at DEBUG level

**Decision context:**

Retention deletions are recorded in job logs only (no audit index, no records
written into the agent's history index — that would pollute the prompt
pipeline and create recursive-retention problems). The existing INFO-level
aggregate counts stay unchanged. We're adding DEBUG-level lines that show
WHICH documents were deleted, so an operator investigating "what disappeared"
can raise the log level and trace specific IDs.

**Rules:**

- INFO lines (aggregate counts) stay exactly as they are
- NO memory content in logs at any level — IDs and criteria only (content may
  contain PII/credentials)
- Guard each new line with `if (log.isDebugEnabled())` (or rely on log4j2
  parameterized-message laziness for cheap args; use the explicit guard where
  building the message involves collection formatting)
- Do NOT add extra search requests just to obtain IDs for logging. Log IDs
  where the code already holds them; log the deletion criteria where it
  doesn't (delete-by-query paths).

**Where to add DEBUG lines in `MemoryRetentionJobProcessor.java`:**

ID-based paths (we already hold the exact IDs — log them):
- `deleteSessionBatch` / session deletion: log the expired session IDs per
  batch, e.g. `container={} deleting session ids={}`
- `deleteDocumentsBatchRecursive` (used by long-term max_count and history
  max_count): log the doc IDs per batch with the index name

Delete-by-query paths driven by known session-ID batches (log the batch):
- `cascadeDeleteBatch` (working memory cascade): log the session-ID batch
  driving the DBQ
- `deleteOrphanBatch` (orphaned working memory): log the orphan session-ID
  batch

Pure criteria-based delete-by-query paths (no IDs available — log criteria,
do NOT pre-fetch IDs):
- `executeLongTermRetentionDays`: log index + cutoffMillis
- `executeWorkingMemoryTTL`: log index + cutoffMillis
- `deleteEmptyNamespaceWorkingMemory`: log index + cutoffMillis

Log quantity is not a concern; do not truncate the ID lists.

**Test:** add one test asserting a DEBUG deletion-IDs line is emitted for the
session-deletion path (e.g., via a log appender capture), and that the INFO
aggregate line is unchanged.

**Files to modify:**
- `plugin/src/main/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessor.java`
- `plugin/src/test/java/org/opensearch/ml/jobs/processors/MemoryRetentionJobProcessorTests.java`
