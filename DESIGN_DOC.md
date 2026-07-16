# Agentic Memory Retention Policy — Design Document

| Field | Value |
|-------|-------|
| **Author** | ballewer (Erfan) |
| **Reviewers** | Nathalie, Mingshi (mentor) |
| **SDM** | Yaliang |
| **Status** | In Progress |
| **Last Updated** | 2026-07-16 |
| **RFC** | opensearch-project/ml-commons #4859 |

---

## 1. Executive Summary

This project adds a **retention policy** to the OpenSearch ml-commons agentic memory framework. Customers configure per-memory-type rules (`retention_days` and/or `max_count`) on memory containers for sessions and long-term memory, and `max_count` for history. A background Job Scheduler job enforces these rules by periodically deleting expired or over-count memories. Working memory is not independently managed — it is deleted exclusively via cascade when its parent session expires (working memory deleted first, then session — preserving conversation integrity and preventing orphans). An orphan sweep catches working memory that points to non-existent sessions. Individual memories can be marked `pinned: true` to exempt them from eviction.

**Key design decisions:**

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Eviction policy | Option A — Container-Level Retention Policy | Simple, industry-standard time + count. FIFO eviction. ~3–4 weeks. |
| Deletion mechanism | Option X — Fixed-Interval Job Scheduler | Single background job; zero read/write-path changes. |
| Query-time filtering | Not included | Staleness window accepted. Reduces complexity. |
| History | Included — `max_count` only | Bounded storage without compromising audit integrity. |
| Pinned memories | Day-one feature | Customers need per-document eviction exemption. |

All alternatives evaluated (Options A/B/C × X/Y/Z) are documented in the companion analysis `comparisonofideas.md`.

---

## 2. Background

OpenSearch ml-commons provides an agentic memory framework that enables AI agents to persist and reason over structured information across conversations. Memory is organized into **memory containers**, each storing four memory types:

| Memory Type | Purpose | Lifecycle |
|-------------|---------|-----------|
| **Working memory** | Active conversation state (raw messages, tool calls) | Short-lived, tied to a session |
| **Long-term memory** | Distilled knowledge (user preferences, extracted facts) | Persists across sessions |
| **Sessions** | Conversation metadata (start time, participants, config) | Medium-lived |
| **History** | Audit trail of memory operations | Append-only, must be bounded |

---

## 3. Problem Statement

The memory system has no lifecycle management. Consequences:

1. **Unbounded storage growth** — no mechanism to reclaim space from obsolete memories
2. **Context window pollution** — agents retrieve stale/contradictory information, degrading response quality
3. **Increased inference cost** — larger context windows from irrelevant memories increase token usage
4. **Manual intervention required** — operators have no automated cleanup; must delete manually or accept growth

There is no deletion job, no TTL field, and no retention configuration anywhere in the codebase.

---

## 4. Goals and Non-Goals

### 4.1 Goals

- Customers can configure lifecycle rules (`retention_days`, `max_count`) for sessions and long-term memory, and `max_count` for history
- A background job permanently deletes memories that violate configured policies
- Session deletion cascades to all associated working memory (working memory deleted first, then session)
- Working memory is never deleted independently — conversations stay intact until their session expires
- When sessions are disabled (`disableSession=true`), working memory gets its own age-based TTL
- Individual memories can be pinned to exempt them from eviction
- Orphaned working memory (pointing to non-existent sessions) is cleaned up automatically
- History supports `max_count` only (audit trail does not expire by age)
- Fully backward compatible — existing containers are unaffected unless an admin explicitly configures default retention settings
- Retention is opt-in — no data is deleted unless a user sets a policy on their container, or a cluster admin explicitly configures default values (all defaults ship as `-1`/disabled). An explicit `"retention_policy": null` prevents even admin defaults from being applied.

### 4.2 Non-Goals

- **Query-time filtering** — memories may remain visible for up to one job interval after expiration
- **Write-time eviction** — no enforcement on the write path
- **Access-based / LRU eviction** — eviction is pure FIFO, not usage-weighted
- **Per-namespace / per-user max_count** — cap is container-wide
- **AOSS Serverless / managed service deployment** — self-managed only
- **Dry-run preview endpoint** — not in scope for v1 (second iteration)
- **On-demand purge endpoint** — not in scope for v1 (second iteration)
- **GDPR "right to deletion" compliance** — not a stated goal for v1 (though the infrastructure could support it)
- **New UI / dashboards** — all interaction via REST API
- **Cluster-wide retention ceiling** — no admin-enforced maximum (future work)

---

## 5. Design Overview

### 5.1 Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        MEMORY CONTAINER                              │
│                                                                     │
│  memory_configuration.retention_policy:                              │
│    sessions: { retention_days: 30, max_count: 100 }   ← user-set   │
│    long-term: { max_count: 5000 }                     ← user-set   │
│    history: { max_count: 50000 }                      ← user-set   │
│                                                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌─────────┐       │
│  │Working Memory│ │Long-Term Mem │ │ Sessions │ │ History │       │
│  │              │ │              │ │          │ │         │       │
│  │ (no own      │ │ pinned:true  │ │ pinned   │ │         │       │
│  │  policy —    │ │ last_updated │ │ last_upd │ │ created │       │
│  │  cascade     │ │              │ │          │ │         │       │
│  │  only)       │ │              │ │          │ │         │       │
│  └──────┬───────┘ └──────────────┘ └────┬─────┘ └─────────┘       │
│         │                                │                          │
│         │◄── CASCADE (all working mem  ──┘                          │
│         │    deleted when session dies)                              │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ evaluated by
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│              MEMORY RETENTION JOB (Job Scheduler)                    │
│                                                                     │
│  Interval: configurable (default 24h)                               │
│  Locking: distributed via LockService (single-node execution)       │
│                                                                     │
│  For each container with a retention_policy:                        │
│    1. Identify  → find expired sessions (time/count), skip pinned   │
│    2. Cascade   → delete ALL working memory for those sessions      │
│    3. Sessions  → delete the expired session documents              │
│    4. Long-term → delete expired (time/count), skip pinned          │
│    5. History   → delete over-count only, skip pinned               │
│    6. WM TTL    → (disableSession only) delete old working memory   │
│    7. Orphan    → sweep orphaned working memory (once per job run)  │
│    8. Throttle  → pause 5s before next container (only if deleted)  │
│                                                                     │
│  Deletion: _delete_by_query (throttled, idempotent)                 │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Data Flow

```
Customer                         OpenSearch                      Job Scheduler
   │                                │                                │
   │── POST container/_create ─────▶│                                │
   │   {retention_policy: {...}}    │── persist policy on container   │
   │◀── 201 ───────────────────────│                                │
   │                                │                                │
   │── POST memories ──────────────▶│                                │
   │   {content, pinned: true}     │── store memory (no eviction)   │
   │◀── 200 ───────────────────────│                                │
   │                                │                                │
   │── GET/search memories ────────▶│                                │
   │                                │── return all (including stale) │
   │◀── results ───────────────────│                                │
   │                                │                                │
   │                                │         ┌──── every 24h ──────│
   │                                │         ▼                     │
   │                                │── acquire lock                │
   │                                │── iterate containers          │
   │                                │── evaluate policies           │
   │                                │── _delete_by_query (pinned!=true) │
   │                                │── log counts                  │
   │                                │── release lock                │
   │                                │                                │
   │                                │                                │
```

### 5.3 Staleness Window

Both `retention_days` and `max_count` have a staleness window of up to one job interval:

```
TIME ─────────────────────────────────────────────────────────────────▶

Memory expires (violates policy):
  ┌─────────┐                                              ┌─────────┐
  │ expired │                                              │ deleted │
  │         │◀─── still visible in reads (up to 24h) ────▶│         │
  └─────────┘                                              └─────────┘
              ▲                                            ▲
              │                                            │
         policy violated                              job runs
```

This is an accepted design decision — see §4.2 Non-Goals.

---

## 6. Detailed Design

### 6.1 Data Model

#### 6.1.1 RetentionRule

A new data class stored in `MemoryConfiguration`:

```java
public class RetentionRule implements ToXContentObject, Writeable {
    private final Integer retentionDays;  // nullable — null means disabled
    private final Integer maxCount;       // nullable — null means disabled

    // Transient parse-time metadata for field-level merge (NOT serialized via Writeable,
    // NOT included in equals/hashCode). Only meaningful on the node that parsed the JSON request.
    private final transient boolean retentionDaysExplicitlySet;  // true when field present in JSON (even if null)
    private final transient boolean maxCountExplicitlySet;       // true when field present in JSON (even if null)

    // Both null is valid — means "no deletion for this type" (entry persisted but job skips it)
    // retentionDays is rejected (validation error) for HISTORY type
    // MemoryType.WORKING ("working") is NOT a valid key (deleted only via session cascade)
    // Negative values are rejected (validation error)
    // Zero is rejected (validation error) — use null to disable
}
```

**Note on transient flags:** The `retentionDaysExplicitlySet` and `maxCountExplicitlySet` flags are NOT serialized via `writeTo()` (StreamInput constructor hardcodes both to `false`). This means field-level merge only works correctly when the merge runs on the same node that parsed the original JSON request. Since `TransportUpdateMemoryContainerAction` performs the merge in its `doExecute()` (before any transport serialization), this is always the case in practice.

Container-level storage:

```java
// In MemoryConfiguration.java
// Valid keys: SESSIONS, LONG_TERM, HISTORY
// MemoryType.WORKING is not a valid key — it has no independent policy
// Serialized as JSON keys via MemoryType.getValue(): "sessions", "long-term", "history"
private Map<MemoryType, RetentionRule> retentionPolicy;
```

#### 6.1.2 Pinned Field

A new boolean field on memory document types that are directly subject to retention evaluation (`MLLongTermMemory`, `MLMemorySession`, `MLMemoryHistory`):

```java
private Boolean pinned;  // default: false
```

Index mapping addition (three indices — working memory does not need `pinned` since it is never independently evaluated):

```json
{
  "pinned": { "type": "boolean" }
}
```

Note: Working memory does not have a `pinned` field. It is always deleted atomically with its parent session. If a customer needs to preserve a specific conversation, they pin the **session** — which prevents the session from being evicted and therefore preserves all its working memory.

#### 6.1.3 Serialization

`RetentionRule` implements:
- `ToXContentObject` — for REST API serialization and index storage
- `Writeable` — for StreamInput/Output transport serialization

This ensures the policy survives cluster restarts (persisted in the container document) and can be sent across nodes (transport layer).

#### 6.1.4 Index Schema Changes

No new indices are created. Changes to existing memory index mappings:

**Actual index naming pattern:** `.plugins-ml-am-{indexPrefix}-memory-{type}`

Index names are **per-container** and computed dynamically via `MemoryConfiguration.getIndexName(MemoryType)`. For example, a container with the default prefix produces indices like `.plugins-ml-am-memory-container-memory-sessions`. Multiple containers can share the same physical index when they use the same prefix (especially with `useSystemIndex=true`).

| Index (computed per container) | Mapping File to Update | New Field | Type | Default | schema_version |
|-------------------------------|------------------------|-----------|------|---------|----------------|
| `...memory-long-term` | `ml_memory_long_term.json` | `pinned` | boolean | `false` | 1 → 2 |
| `...memory-sessions` | `ml_memory_sessions.json` | `pinned` | boolean | `false` | 1 → 2 |
| `...memory-history` | `ml_memory_long_term_history.json` | `pinned` | boolean | `false` | 1 → 2 |

Working memory indices are **not** modified — working memory has no `pinned` field and no independent retention policy. It is deleted only via session cascade.

The `retention_policy` field is stored on the container document itself (in the `.plugins-ml-am-memory-container` index, mapping file `ml_memory_container.json`, schema_version 1 → 2) — not on a separate index.

**Shared index safety:** Because multiple containers can share a physical index, **every deletion query in the retention job MUST include a `memory_container_id` term filter** to avoid cross-container data loss.

### 6.2 Default Policy (Opt-In, Admin-Configured)

**Retention is opt-in.** Out of the box, no retention policy exists on any container and no data is ever deleted automatically. Retention enforcement only occurs when:
1. A user explicitly sets a `retention_policy` on their container, OR
2. A cluster administrator explicitly configures default retention settings (which are then applied to containers that have no policy of their own).

**All default settings ship as `-1` (disabled):**

| Cluster Setting | Default | Range | Effect when set > 0 |
|-----------------|:-------:|:-----:|---------------------|
| `plugins.ml_commons.memory.default_session_retention_days` | -1 (off) | -1 to 3650 | Applied as sessions `retention_days` on containers without a policy |
| `plugins.ml_commons.memory.default_session_max_count` | -1 (off) | -1 to 1,000,000 | Applied as sessions `max_count` on containers without a policy |
| `plugins.ml_commons.memory.default_long_term_max_count` | -1 (off) | -1 to 1,000,000 | Applied as long-term `max_count` on containers without a policy |
| `plugins.ml_commons.memory.default_history_max_count` | -1 (off) | -1 to 10,000,000 | Applied as history `max_count` on containers without a policy |

If none of these settings are set to a value greater than zero, containers without an explicit policy are simply skipped by the retention job. No backfill occurs. No data is touched.

**When an admin does configure defaults (any setting > 0)**, auto-application happens in two places:
1. **At container creation time** — `TransportCreateMemoryContainerAction` writes the admin-configured defaults onto the container document if the user did not provide an explicit `retention_policy` in the create request.
2. **At job execution time (backfill)** — For existing containers that have no `retention_policy` (pre-feature containers), the retention job writes the admin-configured defaults onto the container document before processing it.

The backfill only executes if any of the four default settings are greater than zero. If all four are `-1`, no backfill occurs and no containers are modified.

**Important:** The backfill does NOT override an explicit null opt-out (see §6.2.2).

Working memory has no entry in `retention_policy` — it is deleted exclusively via session cascade. When a session expires, all its working memory is deleted with it.

**Note on `disableSession=true` containers:** When sessions are disabled, any session retention_policy is stored but the job finds zero sessions and skips that phase — harmless. Working memory in these containers is cleaned up by the cluster-level `working_memory_ttl_days` setting (default 30 days), not by the container's `retention_policy`. If a customer sets `"sessions"` in retention_policy on a `disableSession=true` container, return a warning in the response: `"warning": "sessions retention_policy has no effect when disableSession=true; working memory TTL is governed by cluster setting plugins.ml_commons.memory.working_memory_ttl_days"`.

#### 6.2.1 The "default" Keyword

> **⚠️ Implementation Status: NOT YET IMPLEMENTED.** This section documents the planned design for the `"default"` keyword. The parser does not currently accept `"default"` as a value — it will return a validation error. Implementation is deferred to a future iteration (see §11 Second Iteration). The design is documented here for completeness and to guide the future implementation.

Users will be able to pass the string `"default"` at any level of the retention policy to reset that level to the current cluster default values:

| Input | Effect |
|-------|--------|
| `"retention_policy": "default"` | Reset the entire retention policy to cluster defaults (sessions, long-term, history all get default values) |
| `"retention_policy": {"sessions": "default"}` | Reset sessions policy only to cluster defaults; other types unchanged |
| `"retention_policy": {"sessions": {"max_count": "default"}}` | Reset `max_count` for sessions to cluster default; `retention_days` unchanged |

**Resolution behavior:** At parse time, `"default"` is resolved to the actual cluster setting value before persisting. The container document always stores concrete integer values (or null), never the string `"default"`. This means:
- If an admin later changes the cluster default, previously-created containers are NOT retroactively updated (they already have the resolved value)
- To pick up new cluster defaults, a user must explicitly send `"default"` again

Example:
```bash
# Reset sessions to whatever the cluster defaults are right now
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "sessions": "default"
    }
  }
}
# If admin has configured default_session_retention_days=90, default_session_max_count=5000:
# Result: "sessions": { "retention_days": 90, "max_count": 5000 }
```

#### 6.2.2 Null Opt-Out

Users can explicitly opt out of retention by setting values to `null`:

| Input | Effect |
|-------|--------|
| `"retention_policy": null` | Explicitly opt out of ALL retention. The container is persisted with an empty retention policy flag. The job will skip this container. |
| `"max_count": null` | Remove the `max_count` rule for that type (disable count-based eviction) |
| `"retention_days": null` | Remove the `retention_days` rule for that type (disable time-based eviction) |

**Critical distinction:** A container with `"retention_policy": null` (explicit opt-out) is different from one that never had a policy set:
- **Never had a policy:** If an admin has configured default retention settings (any value > 0), the backfill mechanism will write those admin-configured defaults onto this container at job execution time. If no admin defaults are configured, the container is simply skipped.
- **Explicit null opt-out:** The backfill mechanism will NOT override this — the customer's explicit decision to disable retention is preserved, regardless of whether admin defaults are configured.

Implementation: An explicit null opt-out is persisted as an empty object `{}` or a dedicated `retention_policy_opted_out: true` flag on the container document, distinguishing it from the absence of the field.

#### 6.2.3 Update Semantics (Field-Level Merge)

When a customer updates a retention policy via the PUT API, **field-level merge** semantics apply at two levels:

1. **Type-level preservation:** Types not mentioned in the update request are left completely unchanged. Sending `{"sessions": {...}}` does not affect `long-term` or `history`.

2. **Field-level merge within a type:** Only fields explicitly present in the request body for a given type are updated. Omitted fields retain their current value.

3. **Explicit removal requires null:** To REMOVE a field, the user must explicitly send `null` for that field.

Example: current policy is `"sessions": {"retention_days": 60, "max_count": 500}`. Customer sends `{"sessions": {"max_count": 350}}`. Result is `"sessions": {"retention_days": 60, "max_count": 350}` — `retention_days` is unchanged because it was not in the request.

To remove `retention_days`: send `{"sessions": {"retention_days": null, "max_count": 350}}`.

**Implementation detail:** When a field is explicitly set to null, `RetentionRule.toXContent()` emits `builder.nullField(fieldName)` so the partial-update merge correctly removes the stored value. Without this, omitting a field from the serialized update document would leave the old value intact (OpenSearch's partial merge preserves unmentioned fields). The `retentionDaysExplicitlySet` and `maxCountExplicitlySet` flags track whether the null was explicit (user intent to remove) vs. simply absent (user didn't mention the field — preserve current value).

Types not included in the update request are left unchanged. The PUT response **always returns the full resulting retention_policy** so the developer can verify the final state.

**Semantics table:**

| Value | Meaning |
|-------|---------|
| `retention_policy` absent from container (never set) | No retention occurs. If an admin has configured default settings (> 0), those will be applied via backfill on the next job run. Otherwise, the container is skipped. |
| `"retention_policy": null` | Explicit opt-out — no retention, backfill will not override |
| `"retention_policy": "default"` | Reset to cluster defaults *(not yet implemented — see §6.2.1)* |
| `retention_policy` explicitly provided with values | Policy is applied as specified |
| Field set to a positive integer | Use that value |
| Field set to `"default"` | Resolve to cluster default value *(not yet implemented — see §6.2.1)* |
| Field set to `null` | Remove/disable that specific constraint |
| Field set to `0`, negative, or non-integer (other than `null`/`"default"`) | Validation error |

**Backward compatibility:** Existing containers created before this feature have no `retention_policy` field. If a cluster admin has configured default retention settings (any value > 0), the backfill mechanism will apply those admin-configured defaults at the next job run. If no admin defaults are configured, these containers are simply skipped — no data is deleted. Containers can explicitly opt out via `"retention_policy": null` or set a custom policy via the update API.

### 6.3 API Surface

#### 6.3.1 Modified Endpoints

| Endpoint | Change | Auth |
|----------|--------|------|
| `POST /_plugins/_ml/memory_containers/_create` | Accepts optional `retention_policy` in configuration body | Same as container create |
| `PUT /_plugins/_ml/memory_containers/{id}` | Accepts `retention_policy` updates | Same as container update |
| `GET /_plugins/_ml/memory_containers/{id}` | Returns `retention_policy` in response | Same as container read |
| `POST /_plugins/_ml/memory_containers/{id}/memories` | Accepts optional `pinned` field (for session, long-term, history only — not working memory) | Same as memory write |
| `PUT /_plugins/_ml/memory_containers/{id}/memories/{type}/{mem_id}` | Accepts `pinned` field update (session, long-term, history only) | Same as memory update |

No new permissions are introduced. Retention policy management uses the same authorization as container update.

#### 6.3.2 Validation Rules

| Rule | Error |
|------|-------|
| `"working"` key in retention_policy | `400: Working memory retention cannot be configured directly. Working memory is deleted when its parent session expires. To control message lifetime, configure retention on "sessions" instead.` |
| `retention_days` set on history type | `400: retention_days is not supported for history memory type` |
| `retention_days` ≤ 0 | `400: retention_days must be a positive integer or null` |
| `max_count` ≤ 0 | `400: max_count must be a positive integer or null` |
| Both fields `null` for a type | Valid — means "no retention policy for this type" (effectively disables retention for that type) |
| Unknown memory type key | `400: unknown memory type: {key}` |

**Note:** Valid keys use `MemoryType.getValue()` strings: `"sessions"`, `"long-term"`, `"history"`. The key `"working"` is the value to reject (not `"working_memory"`).

#### 6.3.3 Example Flow

```bash
# 1. Create container with explicit retention policy (overrides defaults)
POST /_plugins/_ml/memory_containers/_create
{
  "name": "customer-support-agent",
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": 60, "max_count": 500 },
      "long-term": { "max_count": 5000 },
      "history": { "max_count": 50000 }
    }
  }
}

# 2. Pin a critical long-term memory (will never be evicted)
PUT /_plugins/_ml/memory_containers/{id}/memories/long-term/{mem_id}
{ "pinned": true }

# 3. Pin a session to preserve the entire conversation
PUT /_plugins/_ml/memory_containers/{id}/memories/sessions/{session_id}
{ "pinned": true }

# 4. Opt out of time-based retention for sessions
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": null, "max_count": 500 }
    }
  }
}

# 5. Disable all retention for a type (both null = no policy)
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": null, "max_count": null }
    }
  }
}
```

#### 6.3.4 Retention Policy Configuration Combinations & Semantics

##### Do I Need to Configure All Three Memory Types Together?

**No.** Each memory type (`sessions`, `long-term`, `history`) is independently configurable. You can set a policy for one type and leave others unconfigured (no deletion). Omitted types are unaffected.

```bash
# Only configure long-term memory retention — sessions and history are untouched
POST /_plugins/_ml/memory_containers/_create
{
  "name": "minimal-retention-agent",
  "configuration": {
    "retention_policy": {
      "long-term": { "max_count": 1000 }
    }
  }
}
```

```bash
# Only configure sessions — long-term and history grow unbounded
POST /_plugins/_ml/memory_containers/_create
{
  "name": "session-only-agent",
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": 30 }
    }
  }
}
```

```bash
# Configure all three types with different rules
POST /_plugins/_ml/memory_containers/_create
{
  "name": "fully-managed-agent",
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": 90, "max_count": 5000 },
      "long-term": { "max_count": 2000 },
      "history": { "max_count": 100000 }
    }
  }
}
```

##### Updating Policy for a Single Type (Merge Semantics)

Updates use **merge semantics** — only the fields you explicitly include are overwritten. Omitted types retain their current policy.

```bash
# Add retention to history WITHOUT affecting sessions or long-term
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "history": { "max_count": 20000 }
    }
  }
}
# Result: sessions and long-term policies are unchanged
```

```bash
# Change ONLY retention_days for sessions — max_count stays at its current value
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": 90 }
    }
  }
}
# If sessions previously had { "retention_days": 60, "max_count": 5000 },
# it is now { "retention_days": 90, "max_count": 5000 }
```

```bash
# Update max_count for long-term memory
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "long-term": { "max_count": 500 }
    }
  }
}
# long-term max_count is now 500
```

```bash
# Remove entire retention policy (disables all retention for this container)
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": null
  }
}
```

##### Multiple Conditions: OR Logic (Not AND)

When both `retention_days` and `max_count` are set for the same memory type, they are evaluated as **OR conditions** — a memory is deleted if it violates **either** constraint.

| `retention_days` | `max_count` | Deletion behavior |
|:---:|:---:|---|
| set | null | Delete only if older than `retention_days` |
| null | set | Delete only if count exceeds `max_count` (oldest first, FIFO) |
| set | set | Delete if older than `retention_days` **OR** if count exceeds `max_count` — whichever triggers first |
| null | null | No deletion for this type (policy effectively disabled) |

**Example:** `"sessions": { "retention_days": 30, "max_count": 100 }`

- A session older than 30 days is deleted — even if you only have 50 sessions total
- The 101st session triggers eviction of the oldest — even if it's only 2 days old
- A session that is 5 days old with only 50 total sessions → **safe** (neither condition met)

##### What Happens When `max_count` and `retention_days` Trigger Simultaneously?

They **do not conflict**. The job evaluates them sequentially within the same run:

1. **Phase: `retention_days`** — delete all memories older than the threshold (skipping pinned)
2. **Phase: `max_count`** — count remaining memories (skipping pinned); if still over cap, delete the oldest until at `max_count`

Since `retention_days` runs first, it may already reduce the count below `max_count`, making the second phase a no-op. In the worst case, both phases delete documents — the union of both sets is removed. There is **no conflict or race** because:

- Both phases operate on the same index in a single-threaded job pass
- Deletion in phase 1 reduces the count seen by phase 2
- The result is always: retain the most recent `max_count` documents that are younger than `retention_days`

```
Example scenario:
  retention_days: 30, max_count: 100
  Container has 150 memories total, 40 are older than 30 days

  Phase 1 (retention_days): delete 40 expired → 110 remain
  Phase 2 (max_count): 110 > 100 → delete 10 oldest → 100 remain

  Final state: exactly 100 memories, all younger than 30 days
```

```
Edge case: all memories are recent
  retention_days: 30, max_count: 100
  Container has 150 memories, none older than 30 days

  Phase 1 (retention_days): 0 deleted (nothing expired)
  Phase 2 (max_count): 150 > 100 → delete 50 oldest → 100 remain

  Final state: 100 memories (max_count was the binding constraint)
```

```
Edge case: few memories but some are old
  retention_days: 30, max_count: 100
  Container has 50 memories, 10 are older than 30 days

  Phase 1 (retention_days): delete 10 expired → 40 remain
  Phase 2 (max_count): 40 < 100 → no-op

  Final state: 40 memories (retention_days was the binding constraint)
```

##### Complete Configuration Combinations Matrix

| Configuration | Sessions | Long-Term | History | Notes |
|---|---|---|---|---|
| Only `retention_days` | Expire old sessions + cascade WM | Expire old long-term memories | **Not supported** (400 error) | History only supports `max_count` |
| Only `max_count` | Keep N most recent sessions | Keep N most recent long-term memories | Keep N most recent history entries | FIFO — oldest by `created_time` deleted first (all types) |
| Both | OR — delete if either triggers | OR — delete if either triggers | N/A | Most aggressive — combines time + count caps |
| Neither (both null) | No deletion | No deletion | No deletion | Same as omitting the type entirely |
| Type omitted from policy | No deletion | No deletion | No deletion | Equivalent to `{ "retention_days": null, "max_count": null }` |

##### Interaction Between Memory Types

Policies for different memory types are **completely independent** — the job processes each type in isolation. There is no cross-type interaction:

```bash
# This is valid — each type has its own independent rule
PUT /_plugins/_ml/memory_containers/{id}
{
  "configuration": {
    "retention_policy": {
      "sessions": { "retention_days": 30 },
      "long-term": { "max_count": 2000 },
      "history": { "max_count": 10000 }
    }
  }
}
# Sessions: only time-based (no count cap)
# Long-term: only count-based (no time cap)
# History: only count-based (time-based not supported)
# Each evaluated independently — deleting sessions does NOT affect long-term count
```

The one **implicit cross-type interaction** is session cascade: when a session is deleted (by session retention policy), all working memory associated with that session is also deleted. This is cascade behavior, not a retention policy on working memory itself.

### 6.4 Background Deletion Job

#### 6.4.1 Job Implementation

```
Class: MemoryRetentionJobProcessor extends MLJobProcessor
Pattern: singleton (mirrors MLStatsJobProcessor)
Schedule: IntervalSchedule (default 24h, configurable)
Locking: distributed via LockService (inherited from MLJobProcessor.process())
```

**Important lock caveat:** `MLJobProcessor.process()` acquires the lock synchronously, calls `run()`, and releases the lock in a `finally` block. Since OpenSearch Client operations use async `ActionListener` callbacks, `run()` returns immediately while actual work is still in-flight. The lock does NOT protect against concurrent execution for the full duration of the job. **Therefore, all retention logic MUST be idempotent** — if two instances run simultaneously, the results must still be correct. This is acceptable because:
- Age-based deletion (`retention_days`): same query produces same results; deleting already-deleted docs is a no-op
- Count-based deletion (`max_count`): uses deterministic sort (oldest first); overlapping deletes on the same document set are safe

#### 6.4.2 Processing Algorithm

```
ACQUIRE distributed lock (via LockService)

// Step 0: Resolve containers with policies
// Search the .plugins-ml-am-memory-container index for containers where
// retention_policy IS NOT NULL. For each container, resolve per-type index
// names via configuration.getIndexName(MemoryType).

FOR each memory container WHERE retention_policy IS NOT NULL:
    
    // Resolve the actual index names for this container
    sessions_index = container.configuration.getIndexName(MemoryType.SESSIONS)
    long_term_index = container.configuration.getIndexName(MemoryType.LONG_TERM)
    history_index = container.configuration.getIndexName(MemoryType.HISTORY)
    working_index = container.configuration.getIndexName(MemoryType.WORKING)
    
    // CRITICAL: All queries below MUST include memory_container_id = {id}
    // because multiple containers can share the same physical index.
    
    // Phase 1: Identify expired sessions (but don't delete them yet)
    expired_session_ids = []
    
    IF session policy has retention_days:
        SEARCH sessions_index (sort: last_updated_time ASC,
                filter: memory_container_id = {id}
                AND pinned != true 
                AND last_updated_time < now - retention_days)
        APPEND matching IDs to expired_session_ids
    
    IF session policy has max_count:
        count = COUNT in sessions_index WHERE memory_container_id = {id}
                AND pinned != true
        IF count > max_count:
            SEARCH sessions_index (sort: created_time ASC,
                    size: count - max_count,
                    filter: memory_container_id = {id} AND pinned != true)
            APPEND matching IDs to expired_session_ids (deduplicated)
    
    // Phase 2: Delete working memory FIRST (before session documents)
    // This ensures that if we crash after this step, the next run will still
    // find the sessions expired and re-cascade. No orphans possible.
    IF expired_session_ids is not empty:
        _delete_by_query working_index WHERE:
            memory_container_id = {id}
            AND (session_id IN [expired_session_ids]    // top-level keyword
                 OR namespace.session_id IN [expired_session_ids])  // legacy flat_object
        // No pinned check — working memory does not support pinning
        // Uses buildSessionIdMatchQuery(): should(terms(session_id), terms(namespace.session_id)) minimumShouldMatch(1)
    
    // Phase 3: Delete the session documents themselves
    IF expired_session_ids is not empty:
        BULK DELETE from sessions_index by document IDs [expired_session_ids]
    
    // Phase 4: Long-Term Memory
    IF long_term policy has retention_days:
        _delete_by_query long_term_index WHERE:
            memory_container_id = {id}
            AND pinned != true
            AND last_updated_time < (now - retention_days)
    
    IF long_term policy has max_count:
        count = COUNT in long_term_index WHERE memory_container_id = {id}
                AND pinned != true
        IF count > max_count:
            SEARCH long_term_index (sort: created_time ASC,
                    size: count - max_count,
                    filter: memory_container_id = {id} AND pinned != true)
            BULK DELETE by document IDs [result IDs]
    
    // Phase 5: History (max_count only, uses created_time — history has NO last_updated_time)
    IF history policy has max_count:
        count = COUNT in history_index WHERE memory_container_id = {id}
                AND pinned != true
        IF count > max_count:
            SEARCH history_index (sort: created_time ASC,
                    size: count - max_count,
                    filter: memory_container_id = {id} AND pinned != true)
            BULK DELETE by document IDs [result IDs]
    
    // Phase 6: Working memory TTL (only for disableSession=true containers)
    IF container has disableSession = true:
        _delete_by_query working_index WHERE:
            memory_container_id = {id}
            AND created_time < (now - working_memory_ttl_days)
        // working_memory_ttl_days is a cluster setting (default 30d), always present
    
    LOG deletion counts per type (including cascade working memory count)
    IF any deletions occurred in this container:
        PAUSE {throttle_interval} seconds (use threadPool.schedule(), not Thread.sleep())
    ELSE:
        Proceed immediately to next container (no throttle for no-op containers)
    // Then invoke next container via recursive callback (see note below)

// Phase 7: Orphan Sweep (runs once per full job, not per-container)
FOR each memory container WHERE disableSession = false:
    working_index = container.configuration.getIndexName(MemoryType.WORKING)
    sessions_index = container.configuration.getIndexName(MemoryType.SESSIONS)
    
    // Find working memory pointing to non-existent sessions.
    // Uses a paged composite aggregation on the top-level session_id keyword field
    // (namespace is flat_object and cannot be aggregated). Enumeration starts at a
    // random keyspace position each run and wraps around, so the 50K cap truncates
    // a different slice each run for probabilistic full coverage.
    start_key = randomEnumerationStartKey()
    COMPOSITE AGGREGATION on session_id field, starting at start_key, wrap around
        → collect distinct session_id values (up to ORPHAN_SESSION_ID_CAP = 50,000)
    // Legacy fallback: for pre-upgrade docs without top-level session_id:
    SEARCH_AFTER on docs WHERE namespace.session_id EXISTS AND session_id NOT EXISTS
        → extract namespace.session_id values, same random-start wrap-around pattern
    
    CHECK session existence via batched termsQuery("_id", batch) (1000 per request)
    orphan_ids = session IDs that returned NOT FOUND
    IF orphan_ids is not empty:
        _delete_by_query working_index WHERE:
            memory_container_id = {id}
            AND (session_id IN [orphan_ids] OR namespace.session_id IN [orphan_ids])
            AND created_time < (now - orphan_ttl_days)  // age guard: prevents deletion of
            // freshly-written working memory under caller-managed session IDs
    
    // Find working memory with no session_id at all (shouldn't exist, but can
    // from partial writes — grace period allows incomplete writes to be finished)
    _delete_by_query working_index WHERE:
        memory_container_id = {id}
        AND session_id NOT EXISTS
        AND namespace.session_id NOT EXISTS
        AND namespace.user_id NOT EXISTS AND namespace.agent_id NOT EXISTS
        AND created_time < (now - orphan_ttl_days)  // configurable, default 7d
        // 7-day grace: gives partially-written documents time to have session_id
        // set in a subsequent update before being treated as orphans

RELEASE lock (note: due to async execution, lock is actually released before work completes — see §6.4.1)
```

**Implementation note (post-coding):** The orphan sweep uses a paged composite aggregation on the top-level `session_id` keyword field (added to the `ml_memory_working` index mapping) to enumerate distinct session IDs at O(unique sessions) cost. The `namespace` field is `flat_object` and does not support aggregations, which is why the top-level field was denormalized. A legacy fallback enumerates pre-upgrade documents (lacking the top-level field) via `search_after` pagination on `namespace.session_id`. Enumeration starts from a per-run random keyspace position and wraps around so the 50,000 session-ID cap truncates a different slice each run (probabilistic full coverage). Scroll was rejected due to memory pressure at scale — it holds open a search context for the duration.

Session existence checking uses batched `termsQuery("_id", batch)` against the sessions index (NOT MultiGetRequest). This is simpler and uses the same `client.search()` pattern as the rest of the job, avoiding additional API surface imports.

**Note on `max_count` implementation:** `_delete_by_query` does not support "delete the N oldest documents." Instead, the job performs a two-step operation: (1) SEARCH with sort + size to identify the document IDs that exceed the cap, then (2) BULK DELETE those specific IDs. This means a partial failure during step 2 leaves some over-cap documents until the next run (safe — idempotent).

**Note on async container iteration:** Since all OpenSearch operations use async `ActionListener` callbacks, the container loop is NOT a synchronous for-loop. Instead, it uses a recursive continuation-passing pattern: container N's `processContainer` reports whether deletions occurred via `ActionListener<Boolean>`. If `true`, the callback invokes `threadPool.schedule(throttleDelay, () -> processContainerChain(N+1, ...))`. If `false` (no deletions — opt-out, no policy, no expired data, backfill-only), it recurses immediately without scheduling a delay. This conditional throttle ensures the pause only applies where needed while preserving cluster courtesy.

**Note on cascade batching:** If thousands of sessions expire simultaneously (e.g., first-time policy on a container with years of history), the `terms` query in Phase 2 (cascade) must batch `expired_session_ids` into groups of 500–1000 per `_delete_by_query` request to stay within the max clause count (default 1024).

**Note on idempotency with `max_count`:** Because the lock does not protect the full async execution (see §6.4.1), two job instances could theoretically run concurrently. For `max_count`, both instances compute the same "oldest N documents" using a deterministic sort (`created_time ASC` for all types). They produce the same document ID set, so overlapping bulk deletes target the same documents — deleting an already-deleted doc is a no-op. This makes concurrent execution safe without true mutual exclusion.

**Note on `max_count` overshoot:** Between the COUNT query and the BULK DELETE, concurrent writes can push the actual count above `max_count`. This overshoot is proportional to write throughput during job execution and is corrected on the next run. Write-time eviction (§12 Future Work) is the eventual fix for zero-overshoot enforcement.

#### 6.4.3 Processing Order Rationale

**Working memory is deleted BEFORE its parent session.** This ordering prevents orphans:
1. Identify which sessions are expired (Phase 1)
2. Delete their working memory first (Phase 2)
3. Delete the session documents (Phase 3)

If the job crashes between Phase 2 and Phase 3, the next run will re-identify the same expired sessions (they still exist), see they have no working memory left (already deleted), and delete the sessions. No orphans are created.

If the job crashes between Phase 1 and Phase 2 (before any deletes), nothing happened — the next run starts fresh.

The orphan sweep (Phase 7) is a safety net that catches any edge cases where working memory somehow points to a non-existent session (bugs, race conditions, partial writes).

#### 6.4.4 Job Registration

- **At cluster startup:** The job is registered unconditionally via `MLCommonsClusterEventListener` (following the `STATS_COLLECTOR` pattern), not lazily on first container creation. The job no-ops if no containers have retention policies.
- **Idempotent:** If the job document already exists in the ML jobs index, creation is a no-op. Concurrent startup cannot produce duplicate jobs.
- **Persistent:** Once registered, the job runs on every interval regardless of whether containers currently have policies. It is never unregistered.
- **Wiring required:** Add `MEMORY_RETENTION` to `MLJobType` enum, add a dispatch case in `MLJobRunner.runJob()`, instantiate `MemoryRetentionJobProcessor` singleton in the plugin.

#### 6.4.5 Configuration

| Cluster Setting | Default | Range | Description |
|-----------------|---------|-------|-------------|
| `plugins.ml_commons.memory.retention_enabled` | `true` | true/false | Master kill switch — when false, the retention job skips execution entirely. Allows emergency disable without code rollback or policy removal. |
| `plugins.ml_commons.memory.retention_job_interval` | `24h` | 1h–168h | How often the job runs |
| `plugins.ml_commons.memory.retention_job_throttle` | `5s` | 1s–60s | Pause between containers where deletions occurred (no-op containers proceed immediately) |
| `plugins.ml_commons.memory.orphan_ttl_days` | `7` | 1–365 | Age threshold for deleting working memory with no `session_id` |
| `plugins.ml_commons.memory.working_memory_ttl_days` | `30` | 1–365 | TTL for working memory when `disableSession=true` (age-based, using `created_time`) |
| `plugins.ml_commons.memory.default_session_retention_days` | `-1` (off) | -1–3650 | Admin-configured default `retention_days` for sessions. Only applied when > 0. |
| `plugins.ml_commons.memory.default_session_max_count` | `-1` (off) | -1–1000000 | Admin-configured default `max_count` for sessions. Only applied when > 0. |
| `plugins.ml_commons.memory.default_long_term_max_count` | `-1` (off) | -1–1000000 | Admin-configured default `max_count` for long-term. Only applied when > 0. |
| `plugins.ml_commons.memory.default_history_max_count` | `-1` (off) | -1–10000000 | Admin-configured default `max_count` for history. Only applied when > 0. |

#### 6.4.6 Failure Handling

| Failure | Behavior |
|---------|----------|
| Job fails mid-run (node crash, OOM) | Next scheduled run re-evaluates all containers from scratch. Idempotent deletes make this safe. |
| Crash after working memory deleted but before session deleted | Next run re-identifies the same expired sessions (still exist), finds no working memory, deletes sessions. No orphans. |
| Crash before any deletes (during identification) | No state changed. Next run starts fresh. |
| Partial bulk delete (some IDs succeed, some fail) | Remaining docs are caught on next run. Idempotent. |
| Lock cannot be acquired | Job skips this interval. Next interval retries. |
| Container deleted during job run | `_delete_by_query` on non-existent container_id is a no-op. |
| Job overlaps with next interval | Due to async lock release (see §6.4.1), concurrent execution is possible. All operations are idempotent by design, so concurrent runs produce correct results. |
| Orphan sweep finds stale references | Deletes orphaned working memory. Safe — these documents are unreachable by any session. |
| Index does not exist (empty container never used) | All search and `_delete_by_query` operations use `allowNoIndices=true` and `ignoreUnavailable=true`. Job skips that container with a debug log. Orphan sweep skips containers whose session index does not exist. |

#### 6.4.7 Observability

The job logs at INFO level for each container processed:

```
[MemoryRetentionJob] container={id} deleted: sessions={n}, cascade_working_memory={n}, 
  long_term={n}, history={n}, pinned_skipped={n}, elapsed={ms}
```

Total run summary at job completion:

```
[MemoryRetentionJob] run complete: containers_processed={n}, total_deleted={n}, 
  duration={s}, errors={n}
```

### 6.5 Session Cascade Semantics

#### 6.5.1 Core Principle

**Working memory is never deleted independently.** The unit of deletion for working memory is the entire session — when a session is evicted, all its working memory (messages, tool calls, structured data) is deleted as a unit (working memory first, then session document — two operations, not a transaction, but crash-safe by ordering). This preserves conversation integrity: you never end up with a conversation that has random gaps in the middle.

#### 6.5.2 Relationship Model

- `MLWorkingMemory` stores a `session_id` in its `namespace` map (a `flat_object` field in the index mapping) and also in a denormalized top-level `session_id` keyword field (populated on write by `TransportAddMemoriesAction`). Every working memory entry belongs to a session. Queries use a `should` matching both `session_id` (top-level keyword) and `namespace.session_id` (flat_object, for pre-upgrade backward compatibility).
- `MLLongTermMemory` does not structurally depend on sessions. It is distilled knowledge that outlives sessions.
- `MLMemoryHistory` is never cascaded.

#### 6.5.3 Cascade Rules

| When this is deleted... | Also delete | Not deleted |
|-------------------------|-------------|-------------|
| Session | **All** working memory where `namespace.session_id` = deleted session ID | Long-term memory, History |
| Long-term memory | Nothing | — |
| History | Nothing | — |

Working memory is not listed as a source of deletion because it is never independently targeted by the retention job.

#### 6.5.4 Why Working Memory Has No Independent Policy

Working memory entries are individual messages in a conversation. Deleting them independently would leave conversations with gaps — a user asks a question, the answer is deleted, then the next message references a now-invisible response. This is confusing and useless. The correct granularity for deletion is the entire conversation (session + all its messages).

If a customer wants working memory to live shorter than sessions, they should set a shorter `retention_days` on sessions. When the session dies, all its messages die with it.

**Exception — `disableSession=true` containers:** When sessions are disabled on a container, there is no parent session to cascade from. In this case, working memory entries are standalone and receive an age-based TTL using their `created_time` field (configurable via `plugins.ml_commons.memory.working_memory_ttl_days`, default 30 days). This is the only scenario where working memory is deleted independently.

#### 6.5.5 Deletion Order (Working Memory First)

When a session is expired by the retention job, the deletion order is:
1. **Delete working memory** entries where `namespace.session_id` = expired session ID
2. **Delete the session** document itself

This ordering prevents orphans. If the job crashes after step 1 but before step 2, the next run will re-identify the same session as expired (it still exists), see it has no remaining working memory, and delete it cleanly. The reverse order (session first, then working memory) would create orphans that the job could never find again — because the session is gone, the job would never re-cascade.

The orphan sweep (Phase 7) is a safety net for any edge cases where this ordering is violated.

#### 6.5.6 Session ID Safety (UUID Uniqueness)

Session IDs are OpenSearch document `_id` values — generated as UUIDs by the framework. They are never reused. There is no risk of a cascade accidentally deleting working memory belonging to a different session that happens to share an ID. Once a session ID is deleted, no future session will have the same ID.

#### 6.5.7 Why Long-Term Memory Is Not Cascaded

Long-term memory represents the distilled value of conversations — user preferences, extracted facts, summaries. If "user is allergic to peanuts" (extracted during session X) were deleted when session X expires, the agent loses knowledge it was designed to retain. Long-term memory ages out on its own timeline via its own `retention_days`/`max_count` policy.

#### 6.5.8 Edge Cases

| Scenario | Behavior |
|----------|----------|
| Working memory with no `session_id` (sessions enabled) | Cleaned up by orphan sweep after `orphan_ttl_days` (default 7 days). These shouldn't exist but can from partial writes. |
| Working memory pointing to non-existent session | Cleaned up by orphan sweep on next job run, subject to `orphan_ttl_days` age guard (default 7 days). Freshly-written working memory under caller-managed session IDs is not deleted until it exceeds this age. |
| Working memory in `disableSession=true` container | Deleted by age-based TTL using `created_time` (configurable, default 30 days). |
| Pinned session | Session is not evicted → its working memory is preserved |
| Session pinned but past `retention_days` | Not deleted (pinned overrides policy) → working memory safe |

### 6.6 Pinned Memories

#### 6.6.1 Semantics

- `pinned: true` exempts a memory from **all** automated eviction: `retention_days` and `max_count`
- Supported on: sessions, long-term memory, history
- **Not supported on working memory** — to preserve a conversation, pin the session
- Pinning a session protects both the session document and all its working memory (since working memory is only deleted via cascade)
- Pinned documents are excluded from the count when evaluating `max_count` — they don't consume quota
- Pinning is a per-document decision, not per-type or per-container

#### 6.6.2 Pinned Count Exceeds max_count

If pinned memories alone exceed `max_count`, the container grows beyond its limit indefinitely. This is intentional — `pinned` is an explicit customer override saying "I accept growth for these memories."

**Observability (v1):** The retention job logs a WARN when pinned count exceeds `max_count` for any type in a container, alerting operators to potential unbounded growth: `[MemoryRetentionJob] container={id} type={type} pinned_count={n} exceeds max_count={n}. Container will grow unbounded until pins are removed.`

#### 6.6.3 Implementation

The deletion job adds `pinned != true` (or `pinned: false OR pinned: null`) to all deletion queries. Since `pinned` defaults to `false` and existing documents won't have the field, the query must handle the absent-field case:

```json
{
  "bool": {
    "must_not": [
      { "term": { "pinned": true } }
    ]
  }
}
```

This correctly matches documents where `pinned` is `false`, `null`, or absent.

### 6.7 Retention Per Memory Type Summary

| Memory Type | `retention_days` | `max_count` | Deletion Mechanism | Evaluation Field | Pinning |
|-------------|:----------------:|:-----------:|-------------------|------------------|---------|
| **Session** | ✓ | ✓ | Direct (job evaluates policy); working memory deleted first, then session | `retention_days`: `last_updated_time`; `max_count`: `created_time` | ✓ |
| **Working memory** (sessions enabled) | ✗ | ✗ | Cascade only — deleted when parent session expires | N/A | ✗ (pin the session) |
| **Working memory** (`disableSession=true`) | ✓ (via cluster setting) | ✗ | Age-based TTL using `created_time` | `created_time` | ✗ |
| **Long-term memory** | ✓ | ✓ | Direct (job evaluates policy) | `retention_days`: `last_updated_time`; `max_count`: `created_time` | ✓ |
| **History** | ✗ | ✓ | Direct (job evaluates policy) | `created_time` | ✓ |

**Session liveness:** `last_updated_time` on sessions is automatically bumped when working memory is added to the session (see §6.11). Active conversations extend their session's retention lifetime. Only content-field updates bump the timestamp — organizational changes (tagging, pinning) do not (see §6.10).

### 6.8 Evaluation Field Rationale

#### Why `last_updated_time` for `retention_days` on Sessions and Long-Term Memory

The retention job evaluates `retention_days` for sessions and long-term memory against `last_updated_time`, not `created_time`. This means a memory's time-based retention clock resets whenever it is explicitly updated (content changes only — see §6.10).

**Note:** `max_count` eviction uses `created_time` for ALL types (see Decision #23 below). Only `retention_days` uses `last_updated_time`.

**Rationale for `last_updated_time` on `retention_days`:**
- A session created 90 days ago but actively updated yesterday is still "alive" — deleting it based on creation date would destroy active conversations
- Long-term memories that get refined over time (e.g., "user prefers dark mode" updated to "user prefers dark mode with high contrast") should not expire based on when they were first created
- `last_updated_time` captures the most recent human or agent interaction with the document, which is the best proxy for "active use"

**Note:** `last_updated_time` on sessions is now automatically bumped when working memory is added (see §6.11), and only bumped by content-field updates — not organizational changes like tagging or pinning (see §6.10).

#### Why NOT `created_time` (considered alternative)

Using `created_time` as the evaluation field was considered and would have provided the following benefits:

- **Predictable expiration window** — the operator knows exactly when data expires from the moment of creation. A 30-day policy means every document lives for exactly 30 days, no exceptions, making capacity planning straightforward.
- **Immune to accidental lifetime extension from system-triggered updates** — no background process, API call, or metadata enrichment can inadvertently keep data alive beyond its intended retention window.
- **Simpler mental model** — "data lives for exactly N days, period." No need to reason about which operations touch `last_updated_time` and which do not. Operators can predict storage reclamation with certainty.

#### Risks of `last_updated_time`

The following scenarios document specific concerns identified through codebase analysis:

1. **Strategy execution updates an existing long-term memory fact**
   - *Scenario:* Background LLM inference (strategy execution) decides to UPDATE an existing long-term memory fact with new information from a recent conversation.
   - *Consequence:* The long-term memory's retention clock resets fully, potentially keeping it alive indefinitely if conversations keep reinforcing the same fact. A user preference extracted on day 1 that gets slightly refined every week would never expire, even if the user has moved on and the preference is stale.
   - *Likelihood:* High. *Mitigation:* Acceptable trade-off — a fact that is actively being refined by the agent is, by definition, still relevant. If staleness becomes an issue, operators can use `max_count` as a hard cap independent of age.

2. **User pins a session or long-term memory via Update Memory API**
   - *Scenario:* User sets `pinned=true` on a memory via the Update Memory API.
   - *Consequence:* The retention clock resets as a side effect of the unconditional `last_updated_time` bump in `constructNewDoc()`. The user intended to mark importance, but got an unintended full retention reset. If pinned items are already exempt from retention, this double-extension is redundant but harmless; if not, pinning becomes an implicit retention override.
   - *Likelihood:* Medium. *Mitigation:* Since pinned documents are already exempt from eviction (see §6.6), the clock reset is redundant and harmless. No functional impact.

3. **User corrects a typo or modifies tags via Update Memory API**
   - *Scenario:* User corrects a typo in a long-term memory's text field or adds/removes a tag via Update Memory API.
   - *Consequence:* Full retention clock reset for what should be a cosmetic/organizational change. A 29-day-old memory that would expire tomorrow gets a full new retention period from a tag rename.
   - *Likelihood:* Medium. *Mitigation:* Document this behavior clearly in API documentation. For operators who find this unacceptable, a future `created_time`-based eviction mode can be offered as a configuration option (see Final Decision below).

4. **Automated external tooling bulk-updates metadata fields**
   - *Scenario:* Monitoring scripts, admin dashboards, or migration jobs call the Update Memory API to bulk-update metadata fields across many documents.
   - *Consequence:* All touched documents get retention clocks reset simultaneously, causing a retention cliff where nothing expires for a full retention period after the migration, followed by mass expiration. This could cause unpredictable storage spikes and sudden bulk deletions.
   - *Likelihood:* Low. *Mitigation:* Document that bulk metadata updates reset retention clocks. Operators performing migrations should account for this in capacity planning or temporarily increase `max_count`.

5. **Strategy execution ADD creates a near-duplicate instead of UPDATE**
   - *Scenario:* Strategy execution ADD creates a new long-term memory that is essentially a duplicate or near-duplicate of an existing fact (LLM makes a borderline ADD vs UPDATE decision).
   - *Consequence:* Instead of updating the existing fact (which would reset one clock), a new document is created with a fresh `created_time` AND `last_updated_time`. The old fact may eventually expire while the near-duplicate survives, leading to fragmented memory state. This is a `last_updated_time` problem only indirectly: the old fact does NOT get its clock reset because the LLM chose ADD instead of UPDATE.
   - *Likelihood:* Medium. *Mitigation:* This is fundamentally an LLM decision-quality problem, not a retention-field problem. Deduplication logic in the strategy layer is the correct fix. Retention policy will eventually clean up the stale duplicate via normal age-out.

6. **Future system-triggered session metadata updates**
   - *Scenario:* Future code changes add session-level metadata updates triggered by system events (e.g., auto-updating session summary after N messages, or auto-tagging sessions based on content).
   - *Consequence:* Would silently extend session retention for any session that receives automated metadata enrichment, creating an invisible retention extension pathway that operators may not anticipate. The current code does NOT do this, but the unconditional `last_updated_time` pattern in `TransportUpdateMemoryAction` makes it trivial to accidentally introduce.
   - *Likelihood:* Low. *Mitigation:* Code review vigilance. Any future system-triggered update should explicitly document its retention impact. Consider adding a `skip_timestamp_update` internal flag for system-initiated metadata writes that should not affect retention.

#### Final decision

`last_updated_time` was chosen because the cost of prematurely deleting an active conversation (the `created_time` approach) is higher than the cost of a memory living slightly longer than intended (the `last_updated_time` approach). For operators who need strict TTL behavior, `created_time`-based eviction could be added as a future configuration option.

#### Why `created_time` for History

History entries are append-only audit records. They are never updated after creation — there is no `last_updated_time` field on history documents. `created_time` is the only meaningful timestamp.

#### Why `created_time` for Working Memory TTL (disableSession=true)

Working memory in sessionless containers has no update lifecycle. Messages are written once and never modified. `created_time` accurately represents when the document entered the system.

### 6.9 max_count Calculation — Detailed Explanation

`max_count` caps the number of non-pinned documents of a given type within a container. It is evaluated independently per memory type — session count does NOT include working memory documents, and working memory count does NOT include sessions.

#### What max_count counts

| Policy on type | What gets counted | What gets deleted |
|---------------|-------------------|-------------------|
| `sessions.max_count: 200` | Non-pinned session documents only | Oldest sessions (by `created_time`) beyond the cap, plus their working memory via cascade |
| `long-term.max_count: 5000` | Non-pinned long-term memory documents only | Oldest long-term memories (by `created_time`) beyond the cap |
| `history.max_count: 10000` | Non-pinned history documents only | Oldest history entries (by `created_time`) beyond the cap |

**Key clarification:** A session's `max_count` does NOT factor in how many working memory messages that session contains. A session with 2 messages and a session with 200 messages each count as 1 toward the session cap. Working memory quantity is irrelevant to session eviction — when a session is evicted, ALL its working memory is cascade-deleted regardless of count.

#### Algorithm (step by step)

```
1. COUNT non-pinned documents of this type in this container
   Query: { bool: { must: [container_id], must_not: [pinned=true] } }
   
2. IF count <= max_count → DONE (nothing to evict)

3. COMPUTE excess = count - max_count

4. SEARCH for the oldest `excess` documents by created_time
   - Long-term and history: sort by created_time ASC, size = excess (direct collection)
   - Sessions: sort by created_time DESC, skip the newest maxCount, collect the rest
     (equivalent outcome — identifies the same oldest documents — but avoids needing
      the total count as a separate query since it walks from newest first)
   - Fetch only _id (no source needed)

5. DELETE those document IDs via BulkRequest (batched 1000 per request)
   - For sessions: cascade-delete working memory FIRST, then delete sessions

6. LOG deletion count
```

#### Interaction with retention_days (OR logic)

When both `retention_days` and `max_count` are set on the same type, they use **OR logic**: a document is deleted if it violates EITHER constraint.

Example with `{"sessions": {"retention_days": 30, "max_count": 100}}`:
- 150 sessions exist, 20 are older than 30 days
- Time-based identifies: 20 expired sessions
- Count-based identifies: 50 sessions beyond cap (150 - 100 = 50 oldest)
- Union (deduplicated): some sessions appear in both sets
- Final deletion set: all unique sessions from either set

A session is only safe if it passes BOTH constraints (newer than 30 days AND within the top 100 by count).

#### Pinned documents and max_count

Pinned documents are **completely excluded** from the count:
- They are not counted in the total (`must_not: [pinned=true]` in the count query)
- They are never included in the deletion set
- They do not consume quota

If a container has 300 sessions where 50 are pinned, and `max_count: 200`:
- Effective count: 250 (300 - 50 pinned)
- Excess: 50 (250 - 200)
- Result: 50 oldest non-pinned sessions evicted

If ALL non-pinned sessions are under the cap, nothing is evicted — even if pinned + non-pinned together exceed max_count. The container can grow beyond max_count via pinning. This is intentional — pinning is an explicit customer override.

#### Container-wide vs Per-Namespace Enforcement

Retention policies are evaluated **container-wide** in v1 — all documents of a given type share the same cap regardless of which namespace (user) they belong to. Per-namespace enforcement was considered and deferred to v2.

**Why container-wide was chosen for v1:**

- **`namespace` is a `flat_object`** — enumerating distinct `namespace.user_id` values requires a full scroll + client-side dedup (composite aggregation does not work reliably on `flat_object` sub-fields). This is the same expensive pattern used by the orphan sweep, and running it per-container per-type per-job-cycle adds significant cost.
- **Multiplied query count** — per-namespace enforcement turns 3 queries per container (count + search + delete) into `1 + (3 × N)` queries where N is the number of distinct users. For a container with 1000 users, this turns a 3-second operation into a 30+ minute operation.
- **No standardized namespace key** — containers do not declare which namespace field identifies the "user." Some use `namespace.user_id`, others may use `namespace.tenant_id` or `namespace.agent_id`. The retention job would need a configurable "partition key" per container, adding schema complexity.
- **`retention_days` is already per-document** — time-based eviction naturally operates per-document. Each user's old sessions age out independently regardless of what other users are doing. Only `max_count` has the shared-cap problem.
- **v1 scope constraint** — the 11-week internship timeline required a working, tested, shippable feature. Container-wide enforcement delivers the core value (automated lifecycle management) with minimal complexity.

**The trade-off accepted:**

In a multi-user container (like the Olly agent with one shared container and namespace-based user isolation), `max_count` operates as a shared cap:
- User A has 300 sessions, User B has 50 sessions
- `max_count: 200` → total is 350, excess is 150
- The 150 oldest sessions (across BOTH users) are evicted — User A loses more because they have more old sessions

This is acceptable because:
1. `retention_days` (the primary expected constraint) has no fairness issue
2. `max_count` is intended as a storage safety valve at generous thresholds, not a per-user quota
3. Deployments that need strict per-user isolation can use separate containers per user (at the cost of more indices)
4. Memory cleanup is typically configured as a company/platform-level policy (which matches container-level enforcement) rather than a per-user policy — the platform team decides "conversations expire after 90 days," not individual end users

**Why NOT per-namespace (considered alternative):**

Per-namespace enforcement would provide:
- **Fair eviction** — each user's memory is capped independently (User A hitting 500 sessions doesn't affect User B's 10 sessions)
- **Predictable per-user capacity** — operators can guarantee each user gets exactly N memories
- **Better multi-tenant ergonomics** — no need for separate containers per user

But it would require:
- Scroll-based enumeration of all distinct namespace values (expensive on `flat_object`)
- A configurable "partition key" field per container (schema change)
- N× query multiplication per container (scalability concern)
- Handling edge cases: documents with no namespace, documents with multiple namespace fields, namespace values changing over time

**v1 workaround:**
- Use `retention_days` as the primary constraint (naturally per-document, no shared cap)
- Set `max_count` conservatively high (e.g., 5000–10000) as a storage ceiling, not a per-user quota
- Deployments requiring strict per-user caps should use separate containers per user

**v2 roadmap:** Per-namespace `max_count` enforcement, requiring a `partition_key` configuration on the retention policy and optimized namespace enumeration (possibly via a cached namespace registry or a terms aggregation on a dedicated keyword sub-field).

### 6.10 last_updated_time Conditional Bump

`TransportUpdateMemoryAction` only bumps `last_updated_time` when **content** changes. Organizational/metadata-only changes do NOT extend retention lifetime:

| Memory Type | Fields that bump `last_updated_time` | Fields that do NOT bump (accepted but non-content) |
|-------------|--------------------------------------|------------------------|
| **Sessions** | `summary` | `pinned`, `metadata`, `additional_info`, `agents` |
| **Long-term** | `memory` (the core memory field) | `tags`, `pinned` |
| **Working** | `messages`, `binary_data`, `structured_data`, `structured_data_blob` | `metadata`, `tags` (note: `pinned` is rejected with 400 for working memory) |

**Rationale:** Prevents organizational changes (tagging, pinning, adding metadata) from artificially extending a memory's retention lifetime. Only changes that indicate the memory is actively being used for its primary purpose (content storage) should reset the retention clock.

**Implementation:** `TransportUpdateMemoryAction.constructNewDoc()` checks whether any content field is present in the update request. If only non-content fields are being updated, `last_updated_time` is left at its current value (not overwritten with `Instant.now()`).

### 6.11 Session Liveness on Message Add

`TransportAddMemoriesAction` now bumps the parent session's `last_updated_time` when adding working memory to an **existing** session. This means active conversations extend their session's retention lifetime.

**Behavior:**
- When working memory is added to a session that already exists, the session document's `last_updated_time` is updated to `Instant.now().toEpochMilli()`
- Uses `retryOnConflict(3)` to handle concurrent message writes to the same session
- Does NOT bump for newly-created sessions (they already have a fresh timestamp from creation)
- Does NOT bump for `disableSession=true` containers (no meaningful session to update)
- The bump is **best-effort and asynchronous** — it fires after the add-memory response is already returned to the client. A failed bump is logged at DEBUG level and never fails the add-memory request. This avoids add-memory latency impact while still providing session liveness tracking for retention.

**Rationale:** This addresses the known limitation from the original design where a session's `last_updated_time` was only set at creation and not refreshed by conversation activity. Active conversations now naturally extend their session's lifetime, preventing premature eviction of sessions that are still in use.

**Write amplification trade-off:** Each message write now triggers an additional session document update (one extra index operation per message). This was previously deferred to v2 but accepted after team discussion because:
1. The session index is small (one doc per session) — updates are cheap
2. The alternative (premature session eviction during active conversations) is worse
3. `retryOnConflict(3)` handles the common case of concurrent messages without failing

### 6.12 Known Issues Fixed

| Issue | Fix | Impact |
|-------|-----|--------|
| Session `created_time` and `last_updated_time` used `Instant.now().getEpochSecond()` (epoch seconds) | Changed to `Instant.now().toEpochMilli()` (epoch milliseconds) for both fields | The retention job uses `System.currentTimeMillis()` for comparison, which returns milliseconds. Using epoch seconds would make sessions appear to have been created in January 1970 when compared against millisecond timestamps, causing immediate eviction. All timestamps must use consistent millisecond precision. |

---

## 7. Dependencies

| Dependency | Purpose | Status |
|------------|---------|--------|
| OpenSearch Job Scheduler plugin | Background job scheduling + distributed locking | Already used by ml-commons |
| `last_updated_time` field | Retention evaluation for sessions and long-term memory | Already indexed on both types |
| `created_time` field | Retention evaluation for history; working memory TTL (disableSession); orphan sweep | Already present on all types |
| `namespace.session_id` field | Cascade deletion + orphan sweep (links working memory → session). **Note:** `namespace` is a `flat_object` type in the index mapping; query via `termQuery("namespace.session_id", id)` | Already present on `MLWorkingMemory` |
| `MemoryConfiguration` class | Host for the retention policy field | Existing — new field added |
| `MLJobProcessor` base class | Job infrastructure (lock acquisition, scheduling) | Existing — new subclass |

No new external service dependencies. All work stays within the ml-commons Java plugin.

### 7.1 Existing Job Scheduler Wiring

`MachineLearningPlugin` already implements `JobSchedulerExtension`:

| Element | Class / Value | Role |
|---------|---------------|------|
| Extension | `MachineLearningPlugin implements JobSchedulerExtension` | Registers ml-commons as a Job Scheduler provider |
| Job type | `ML_COMMONS_JOBS_TYPE` | Identifies ml-commons jobs |
| Job index | `ML_JOBS_INDEX` | System index for job documents |
| Runner | `MLJobRunner` (ScheduledJobRunner) | Entry point for Job Scheduler |
| Parameter | `MLJobParameter` (ScheduledJobParameter) | Job definition + schedule |
| Base processor | `MLJobProcessor` | Lock acquisition + `run()` dispatch |
| Existing jobs | `MLStatsJobProcessor`, `MLBatchTaskUpdateProcessor` | Patterns to follow |

**Implementation:** Create `MemoryRetentionJobProcessor extends MLJobProcessor` (singleton), implement `run()` with container iteration + `_delete_by_query` logic, register with a daily `IntervalSchedule`.

### 7.2 OpenSearch APIs Used

| API | Used For | Client |
|-----|----------|--------|
| `SearchRequest` + `bool`/`range` query | Identify deletion targets; count documents for `max_count` evaluation | Native Client (via `client.execute()`) |
| `DeleteByQueryAction.INSTANCE` (throttled) | Time-based deletion, cascade (working memory by session_id), orphan cleanup | Native Client (follows `MemoryContainerHelper.deleteDataByQuery()` pattern) |
| `SearchRequest` with `sort` + `size` | `max_count` — identify oldest non-pinned document IDs beyond cap | Native Client |
| `BulkRequest` (delete actions) | Delete specific document IDs for `max_count` enforcement and session removal | Native Client |
| Scroll search (paginated) | Orphan sweep — enumerate distinct `namespace.session_id` values from flat_object field | Native Client |
| `MultiGetRequest` | Orphan sweep — batch-check which session IDs still exist | Native Client |
| Job Scheduler SPI | Background scheduling + distributed locking | — |

**Why native Client, not SdkClient:** `SdkClient` only supports Get, Search, Put, Update, Delete (single doc), and Bulk. It does NOT support `_delete_by_query`, scroll, or `_mget`. The retention job bypasses `SdkClient` entirely and uses the native OpenSearch Client with `ThreadContext.stashContext()` for all operations. This means the retention job **cannot support multi-tenant mode** — if `plugins.ml_commons.multi_tenancy_enabled` is true, the job should log a warning and skip execution.

### 7.3 Scope Limitation: Self-Managed Only

ml-commons routes operations through `SdkClient` — an abstraction that switches between local cluster (self-managed) and remote metadata (serverless/managed). This project targets **self-managed clusters only**:

- Job Scheduler requires persistent nodes — valid for self-managed, not for serverless
- AOSS Serverless is explicitly out of scope (aligns with project proposal)
- Multi-tenancy mode is explicitly unsupported — `SdkClient` lacks `_delete_by_query`/scroll/`_mget`, so the retention job must use the native Client which has no tenant-routing. If `multi_tenancy_enabled = true`, the job skips execution with a warning log.

---

## 8. Non-Functional Requirements

### 8.1 Performance

| Dimension | Impact |
|-----------|--------|
| **Read latency** | Zero — no query-time filtering, no read-path changes |
| **Write latency** | Zero — no write-time eviction, no write-path changes |
| **Background job** | Throttled by design; runs off-peak; configurable interval |
| **Cluster resources** | `_delete_by_query` generates merge load; mitigated by inter-container pause and request throttling |
| **Orphan sweep** | Paged composite aggregation on top-level `session_id` keyword field + batched existence checks per container. Cost scales with unique session IDs (not total documents). A legacy `search_after` fallback handles pre-upgrade documents lacking the top-level field; its cost shrinks to zero as legacy documents are cleaned up. |

### 8.2 Scalability

- Job runtime scales linearly with number of containers that have policies
- With conditional throttling (5 seconds only after containers where deletions occurred), total job runtime is proportional to the number of active-deletion containers, not total containers. A cluster with 10,000 containers where only 50 have expired data takes ~250s of throttle. Expected v1 scale is under 1,000 containers, so the job completes well within one interval.
- If a run cannot complete within one interval, the next scheduled run either waits for the lock or runs concurrently (safe — all operations are idempotent). The job processes all containers from scratch each run; there is no resumption from a partial run.
- No index-per-policy — retention metadata lives on container documents
- `_delete_by_query` is batch-efficient (handles thousands of deletions per sweep)

### 8.3 Availability and Fault Tolerance

| Component | Failure Mode | Recovery |
|-----------|-------------|----------|
| Job fails mid-run | Stale memories persist | Next run cleans up (idempotent) |
| Node hosting job crashes | Lock expires; another node picks up next interval | Automatic |
| Lock contention | Job skips interval | Runs on next interval |
| Memory index unavailable | Job logs error, skips container | Retries next run |

The system degrades gracefully: if the job stops running entirely, the only consequence is storage growth and stale memory visibility. No data corruption, no cascading failures.

### 8.4 Security

- No new permissions introduced — retention policy management uses same authorization as container update
- No new endpoints exposed outside the cluster (all within `_plugins/_ml/` namespace)
- The deletion job operates with plugin-level system index access (same privilege model as existing ml-commons background operations)
- The `pinned` field is user-controlled — no system-level override can forcibly delete a pinned memory

### 8.5 Backward Compatibility

| Scenario | Behavior |
|----------|----------|
| Container created before this feature (no `retention_policy` field) | No retention occurs. If an admin has configured default settings (> 0), backfill applies those at the next job run. Otherwise, the container is skipped. Container can explicitly opt out with `"retention_policy": null`. |
| Container created after this feature without explicit policy | If admin defaults are configured (> 0), they are applied at creation time. Otherwise, no retention occurs (see §6.2). |
| Container with explicit `"retention_policy": null` | Opted out — job skips it. Backfill will not override, even if admin defaults are configured. |
| Cluster upgraded with existing containers | Existing containers have no policy field → no retention occurs unless an admin explicitly configures default settings. |
| Cluster downgraded after retention was used | `retention_policy` field ignored by older code; no deletion runs. Memories persist. |
| New `pinned` field on old documents | Absent field treated as `false` by deletion queries. |

### 8.6 Observability

- **INFO logging:** Per-container aggregate deletion counts (sessions, cascade WM, long-term, history, orphans). Unchanged across operations — suitable for dashboards and alerting.
- **DEBUG logging:** Per-batch document IDs for bulk-delete paths (session deletion, max_count eviction); session-ID batches for cascade/orphan delete-by-query paths; index + cutoff criteria for time-based DBQ paths. No memory content at any level — IDs and criteria only. Operators investigating "what disappeared" can raise the log level and trace specific IDs.
- **WARN logging:** Pinned count exceeds `max_count`; partial delete failures; orphan sweep cap reached.
- **Orphan sweep logging:** Logs orphaned working memory counts per container.
- **Metrics (future):** Total documents deleted per run, job duration, containers processed, errors — exposed via ml-commons stats API

---

## 9. Testing Strategy

### 9.1 Unit Tests

- `RetentionRule` serialization (XContent round-trip, StreamInput/StreamOutput round-trip)
- Validation logic (null handling, history rejects `retention_days`, `"working"` key rejected, negative values rejected)
- Opt-in semantics (new container without retention_policy has no policy unless admin defaults configured; existing container untouched without admin defaults)
- Pinned document query construction (correctly excludes `pinned: true` and absent field)
- `max_count` count calculation (excludes pinned documents from total)

### 9.2 Integration Tests

- End-to-end: create container with session policy → create sessions + messages → run job → verify correct sessions + their working memory deleted
- Cascade ordering: verify working memory is deleted before session document (crash between → next run completes cleanly)
- Cascade integrity: delete session → verify ALL associated working memory deleted (no partial conversations remain)
- Cascade atomicity: session with 100 messages → all 100 deleted, none remain
- Pinned session: create pinned session older than `retention_days` → run job → verify session AND its working memory survive
- Pinned long-term memory: pin a memory older than policy → verify it survives
- Orphan sweep: create working memory pointing to non-existent session → run job → verify orphan deleted
- Orphan sweep (no session_id): create working memory with null namespace.session_id → wait past `orphan_ttl_days` → verify deleted
- `disableSession=true` TTL: create container with disabled sessions → write working memory → wait past TTL → verify deleted by age
- Backward compatibility: container without policy (no admin defaults configured) → run job → verify nothing deleted
- OR logic: session violates `max_count` but not `retention_days` → verify deleted with cascade
- History: verify `retention_days` rejected, `max_count` evaluated against `created_time`
- Opt-out: set both constraints to `null` → verify no deletion for that type
- Idempotency: run job twice → second run is a no-op
- Working memory key rejected: attempt to set `"working"` in retention_policy → verify 400 error

### 9.3 Edge Cases to Cover

- Pinned count exceeds `max_count` (nothing evicted, no error)
- Empty container (job skips cleanly)
- Container deleted while job is running (no-op delete queries)
- Concurrent container creation (job registration is idempotent)
- Job interval shorter than job runtime (lock prevents double-execution)
- Partial bulk delete failure (remaining docs caught on next run)
- Orphan sweep with thousands of distinct session_ids (scroll-based pagination)
- `disableSession=true` container with no working_memory_ttl configured (uses default 30d)

---

## 10. Rollout Plan

There is no feature flag. The retention infrastructure ships as part of the next ml-commons release:

| Phase | What Ships | Behavior |
|-------|-----------|----------|
| **Code merge** | `RetentionRule`, `MemoryRetentionJobProcessor`, `pinned` field, API changes, orphan sweep | Job registered at cluster startup. Does nothing unless users set policies or admins configure defaults. |
| **First container with a policy** | Container persisted with user-specified `retention_policy` | Next job run begins enforcement for that container |
| **Existing clusters upgrade** | Code deployed | No impact on existing containers. No data is deleted unless an admin explicitly configures default retention settings via cluster settings API. |

**Out of the box:** No retention occurs. All `default_*` settings ship as `-1` (disabled). Users must explicitly set policies on their containers, or an admin must configure cluster-level defaults, before any automated deletion happens.

**Opt-out (per container):** Customers who do not want retention (even if admin defaults are configured) can set `"retention_policy": null` on their containers. This explicit opt-out is preserved across job runs.

**Emergency disable (cluster-wide):** Set `plugins.ml_commons.memory.retention_enabled` to `false` via cluster settings API to immediately stop all retention enforcement without removing policies from containers.

**Recommended workflow for enabling retention on existing containers:**
1. Decide on retention values appropriate for your organization
2. Either configure cluster-level defaults (applies to all containers without a policy) or set per-container policies via `PUT /_plugins/_ml/memory_containers/{id}`
3. Monitor the next job run's logs to verify expected deletion counts
4. Tighten the policy gradually as needed

---

## 11. Second Iteration (v2)

Features explicitly deferred from v1 to reduce scope. They build on v1 infrastructure with no redesign needed.

| Feature | Description | Why Deferred |
|---------|-------------|--------------|
| `_retention_preview` endpoint | `POST /_plugins/_ml/memory_containers/{id}/_retention_preview` — shows per-type counts of what would be deleted under a current or proposed policy | Shares query logic with job but adds new REST/transport action pair |
| `_purge` endpoint | `POST /_plugins/_ml/memory_containers/{id}/_purge` — triggers retention for a single container immediately | Same deletion logic as job, packaged on-demand. Not critical for initial launch |
| Retention job metrics via Stats API | Expose `job_last_success_timestamp`, `total_deleted`, `containers_processed`, `job_duration_ms` | Follows `MLStatsJobProcessor` pattern. INFO logs provide basic observability in v1 |
| Cluster-wide retention ceiling | Admin-enforced `max_retention_days_ceiling` and `max_count_ceiling` cluster settings | Important for multi-tenant but adds complexity |
| Retention policy change audit trail | Write history entry on policy create/update/remove | Compliance (SOX, HIPAA); reuses existing history-write infrastructure |
| Separate retention_policy permission | Dedicated permission for changing retention settings | Security hardening; prevents accidental policy weakening |
| Pinned count visibility | Include `pinned_count` per type in GET container response | Ops visibility into unbounded growth from pin abuse |
| `max_pinned_per_container` setting | Cluster setting that rejects new pin requests (returns 400) or logs warnings when pinned count per container per type exceeds a threshold | Prevents silent unbounded growth from pin abuse. WARN log in v1 provides visibility; this setting would add enforcement. |
| `"default"` keyword in retention_policy | Allow `"default"` string at any level to reset to current cluster defaults (see §6.2.1 for full design) | Parser and resolution logic needed; design is complete but implementation is deferred |

## 12. Future Work (Beyond v2)

Longer-term enhancements not planned for immediate iterations:

| Enhancement | Description | Trigger |
|-------------|-------------|---------|
| Query-time filtering | Inject time-based filter on reads for immediate invisibility | If staleness window proves problematic for customers |
| Write-time eviction for `max_count` | Enforce count limit inline on writes (zero staleness) | If 24h overshoot is unacceptable |
| Access-based eviction (Option C) | LRU/LFU scoring — keep frequently-used memories longer | If FIFO eviction deletes memories customers still need |
| Per-namespace `max_count` | Cap per user/session within a container | If multi-tenant fairness is reported as an issue |
| Importance scoring | Weight eviction by semantic importance | Layerable on top of current FIFO |

---

## Appendix A: Decision Log

| # | Decision | Date | Options Considered | Choice | Rationale |
|---|----------|------|--------------------|--------|-----------|
| 1 | Scope target | 2026-06-06 | Self-managed only vs. include AOSS | Self-managed only | Serverless has no persistent Job Scheduler; out of scope per project proposal |
| 2 | Eviction policy (Layer 1) | 2026-06-15 | A (Container Policy), B (Document TTL), C (Access-Based) | Option A | ~3–4 week implementation. Industry standard. Forward-compatible with C. |
| 3 | Deletion mechanism (Layer 2) | 2026-06-15 | X (Job Scheduler), Y (Write-Time), Z (Hybrid) | Option X | Zero write-path risk. Single system. Batch-efficient. Upgradeable to Z. |
| 4 | Query-time filtering | 2026-06-15 | Include (immediate invisibility) vs. exclude (staleness window) | Exclude | Complexity not justified for v1. Staleness acceptable. |
| 5 | History inclusion | 2026-06-15 | Exclude entirely vs. `max_count` only | `max_count` only | Bounds storage without compromising audit integrity |
| 6 | Pinned memories | 2026-06-15 | Day-one vs. stretch goal | Day-one | Customers need per-document eviction exemption |
| 7 | Default behavior for new containers (original) | 2026-06-16 | Defaults applied vs. explicit-only | Explicit-only (opt-in) | Original decision: silent deletion of production data is catastrophic. **Superseded by Decision #24 (2026-07-10).** |
| 8 | Working memory retention | 2026-06-15 | Independent policy vs. cascade-only | Cascade-only | Deleting individual messages leaves conversations with gaps; the session is the correct unit of deletion |
| 9 | Cascade ordering | 2026-06-15 | Session first vs. working memory first | Working memory first | Prevents orphans — if crash occurs between steps, next run can still find the session and re-cascade |
| 10 | Orphan sweep | 2026-06-15 | Ignore orphans vs. active cleanup | Active cleanup | Safety net for edge cases; low cost since it runs once per job |
| 11 | Dry-run preview | 2026-06-15 | Include vs. defer | Defer (not in v1 scope) | Scope reduction; can be added later without design changes |
| 12 | `disableSession=true` working memory | 2026-06-15 | Leave forever vs. age-based TTL | Age-based TTL via `created_time` | No session to cascade from — need independent cleanup |
| 13 | Orphan sweep approach | 2026-06-16 | Composite aggregation vs. scroll search | **Revised: composite aggregation on denormalized top-level `session_id` keyword field** | Original decision was scroll search because `namespace` is `flat_object`. Final implementation adds a top-level `session_id` keyword field to `ml_memory_working.json` (populated on write), enabling efficient composite aggregation. Legacy fallback via `search_after` handles pre-upgrade docs. Randomized start-key ensures 50K cap covers different keyspace slices across runs. |
| 14 | Job registration timing | 2026-06-16 | Lazy (on first container) vs. startup | Startup (via MLCommonsClusterEventListener) | Follows STATS_COLLECTOR pattern. Avoids complex first-use registration logic. Job no-ops if no containers have policies. |
| 15 | Lock and concurrency model | 2026-06-16 | Fix lock duration vs. accept idempotent concurrent execution | Accept idempotent execution | MLJobProcessor releases lock before async work completes. Fixing requires refactoring shared infra. All retention operations are idempotent by design, so concurrent execution is safe. |
| 16 | Native Client vs. SdkClient | 2026-06-16 | Use SdkClient abstraction vs. native Client | Native Client | SdkClient lacks _delete_by_query, scroll, _mget. Follow MemoryContainerHelper.deleteDataByQuery() pattern. Multi-tenancy explicitly unsupported. |
| 17 | Default retention policy (original) | 2026-06-16 | Opt-in (no defaults) vs. opt-out (45-day default) | Opt-in — no defaults | Original decision: silent deletion of production data is catastrophic. **Superseded by Decision #24 (2026-07-10).** |
| 18 | Update semantics | 2026-06-16 | Replace per type vs. merge per field | Merge — only explicit fields overwrite | Replace semantics silently drops constraints when developers update one field. Merge is safer and matches MemoryConfiguration.update() pattern. |
| 19 | Session liveness (original) | 2026-06-16 | Static last_updated_time vs. touch on message write | Keep static (defer touch to second iteration) | Original decision: touching session on every message doubles write operations on the hot path. **Superseded by Decision #25 (2026-07-10).** |
| 20 | `long-term` key naming | 2026-06-16 | Hyphenated `long-term` vs. snake_case `long_term` | Keep `long-term` (match existing MemoryType.getValue()) | Changing the enum value would affect existing index naming and other code that uses MemoryType. Accept the hyphen as-is since the agentic memory framework already uses it. |
| 21 | Dry-run preview | 2026-06-16 | Defer to second iteration vs. include in v1 | Defer to second iteration | Reduces scope for 11-week timeline. Customers can start with conservative values and monitor job logs. |
| 22 | On-demand purge | 2026-06-16 | Defer to second iteration vs. include in v1 | Defer to second iteration | Reduces scope. Customers can adjust job interval setting for faster enforcement. |
| 23 | max_count sort field | 2026-07-10 | last_updated_time vs created_time | created_time | Batch updates make last_updated_time identical; created_time preserves true insertion order for fair FIFO eviction |
| 24 | Default retention policy | 2026-07-16 | Opt-in (no defaults) vs opt-out (auto-apply defaults) | Opt-in — admin-configured defaults, all shipping as `-1` (disabled) | Final decision: no data is deleted without explicit action. Admins can optionally configure cluster-level defaults; without that, containers have no retention. **Supersedes Decision #17.** |
| 25 | Session liveness | 2026-07-10 | Static last_updated_time vs touch on message write | Touch on message write | Active conversations should extend session lifetime. Accepted write amplification tradeoff. **Supersedes Decision #19.** |
| 26 | last_updated_time conditional bump | 2026-07-10 | Unconditional vs content-only | Content-only | Pinning/tagging should not extend retention lifetime — only content changes indicate the memory is actively used |

Full alternatives analysis: `comparisonofideas.md`

---

## Appendix B: Glossary

| Term | Definition |
|------|------------|
| **`retention_days`** | Time-based rule — memory deleted when `last_updated_time` (sessions, long-term) is older than now minus this value. Not supported for history (history has no `last_updated_time`). |
| **`max_count`** | Count-based rule — oldest non-pinned memories beyond this cap are deleted (FIFO) |
| **OR logic** | When both rules set: memory deleted if it violates *either* constraint |
| **FIFO** | First-In-First-Out eviction — oldest by `created_time` deleted first for `max_count` ordering (all types); `retention_days` uses `last_updated_time` for sessions and long-term |
| **Staleness window** | Gap between when a memory violates a policy and when the job physically deletes it (up to one job interval) |
| **Cascade** | Session deletion automatically deletes ALL associated working memory (by `session_id`) — the only mechanism that deletes working memory |
| **Pinned** | Per-document flag exempting a memory from all automated eviction |
| **Orphan sweep** | Job phase that finds and deletes working memory pointing to non-existent sessions |
| **Memory container** | Top-level grouping that holds all four memory types + configuration including retention policy |
| **Job Scheduler** | OpenSearch plugin providing cron-like scheduling for background tasks |

---

## Appendix C: References

| Resource | Link |
|----------|------|
| RFC #4859 (retention lifecycle) | `opensearch-project/ml-commons` issues |
| Alternatives analysis | `comparisonofideas.md` (companion document) |
| Options summary | `retention-options-summary.md` (companion document) |
| Project proposal | `PROJECT_PROPOSAL.md` |
| OpenSearch Job Scheduler | `opensearch-project/job-scheduler` |
| ml-commons plugin | `opensearch-project/ml-commons` |
