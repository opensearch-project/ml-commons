# Stored-document compatibility fixtures (OpenSearch)

Raw `_source` of the ML system indices, captured from stock `opensearchproject/opensearch:<version>` clusters with the
ML Commons plugin defaults. One directory per version. Used by `MLStoredDocumentCompatibilityTests`, which replays each
document against the current code: parse, decrypt, toXContent round-trip, endpoint validation, and for remote models a
deploy + predict through the real executor with the HTTP client faked, comparing the outgoing request to
`expected-requests.json`.

| File | Content |
|---|---|
| `plugins-ml-connector.json`, `plugins-ml-model.json`, `plugins-ml-agent.json` | documents the test checks |
| `plugins-ml-model-group.json`, `ml-settings.json` | captured for provenance; not exercised yet |
| `manifest.json` | version, build hash, capture time, and the master key the cluster encrypted credentials with |
| `expected-requests.json` | the recorded outgoing request per remote model |

Credentials are dummy values only (`sk-fixture-dummy`, dummy AWS keys). The master key belongs to a throwaway capture
cluster and protects nothing else. Never add documents from a real or shared cluster.

`known-failures.txt` lists expected failures. It is enforced both ways: an unlisted failure, a changed message, or a
listed failure that now passes all fail the test.

## Adding a version

1. Start a stock `opensearchproject/opensearch:<version>` container, create the same connectors, models and agent as
   the existing sets through the REST API, and dump the system indices into a new `<version>/` directory with a
   `manifest.json`.
2. Record the expected requests:
   `./gradlew :opensearch-ml-algorithms:test --tests '*MLStoredDocumentCompatibilityTests' -PmlCompatRecord`
3. Check the new `expected-requests.json` by hand before committing it.
