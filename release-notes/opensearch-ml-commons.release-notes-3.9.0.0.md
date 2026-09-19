## Version 3.9.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.9.0

### Features

* Add CRUD API for agentic search templates ([#4945](https://github.com/opensearch-project/ml-commons/pull/4945))
* Add agentic search template param-schema derivation from Mustache template bodies ([#4944](https://github.com/opensearch-project/ml-commons/pull/4944))
* Add optional user-specified custom IDs for ML resources (models, connectors, agents, model groups, memory containers) ([#4974](https://github.com/opensearch-project/ml-commons/pull/4974))
* Add server-side cross-request batch inference queue for the online predict path ([#4996](https://github.com/opensearch-project/ml-commons/pull/4996))
* Add server-side size-based batch inference splitting for ingest predict requests ([#4898](https://github.com/opensearch-project/ml-commons/pull/4898))
* Add structural enrichment of agentic search template param-schemas with descriptions and enums at register time ([#4982](https://github.com/opensearch-project/ml-commons/pull/4982))
* Add client certificate (mutual TLS) authentication support for ML connectors ([#4731](https://github.com/opensearch-project/ml-commons/pull/4731))
* Add GCP Vertex AI connector with automatic OAuth2 token management via `google_cloud` auth strategy ([#4921](https://github.com/opensearch-project/ml-commons/pull/4921))
* Add on-demand memory retention execution and support for self-hosted multi-tenancy ([#4973](https://github.com/opensearch-project/ml-commons/pull/4973))
* Add dynamic retention job interval updates and dry-run API for memory retention ([#4952](https://github.com/opensearch-project/ml-commons/pull/4952))
* Enable unified agent API by default ([#4951](https://github.com/opensearch-project/ml-commons/pull/4951))

### Bug Fixes

* Fix TEXT_SIMILARITY ONNX inference failure for cross-encoder models without `token_type_ids` input ([#4946](https://github.com/opensearch-project/ml-commons/pull/4946))
* Fix deletion and management of remote models stored without `additional_config` ([#5000](https://github.com/opensearch-project/ml-commons/pull/5000))
* Fix false-positive ReDoS rejection of safe connector endpoint regex patterns ([#4972](https://github.com/opensearch-project/ml-commons/pull/4972))
* Fix models stuck in DEPLOYING state when a single model document fails to parse during sync-up ([#5012](https://github.com/opensearch-project/ml-commons/pull/5012))
* Fix V1 conversational agent with native function calling leaving unresolved `tool_descriptions`/`tool_names` placeholders ([#4931](https://github.com/opensearch-project/ml-commons/pull/4931))
* Defer `.plugins-ml-jobs` index creation until rolling upgrade completes and gate retention job on `retention_enabled` ([#4937](https://github.com/opensearch-project/ml-commons/pull/4937))
* Use exact-match queries for id-based lookups so removing one MCP tool no longer deletes tools with similar names and connectors with hyphenated ids remain deletable and updatable ([#5034](https://github.com/opensearch-project/ml-commons/pull/5034))
* Require a non-blank name when registering an MCP tool ([#5034](https://github.com/opensearch-project/ml-commons/pull/5034))
* Fix HTTP 500 on model register/deploy against security 3.9+ clusters by avoiding a Jackson 3 self-reference cycle in `isSuperAdminUser` ([#4994](https://github.com/opensearch-project/ml-commons/pull/4994))
* Fail batch inference when a sub-batch result count does not match its item count, and tighten `batch_queue` setting and `batch_inference_config` validation ([#5040](https://github.com/opensearch-project/ml-commons/pull/5040))
* Pin `google_cloud` connector `token_uri` to the default HTTPS port so a signed JWT cannot be sent to a non-default port ([#5041](https://github.com/opensearch-project/ml-commons/pull/5041))
* Reject counted repetition of a quantified group in trusted-endpoint regex validation ([#5042](https://github.com/opensearch-project/ml-commons/pull/5042))
* Delete the auto-created model when unified agent registration fails, instead of leaving an orphaned model holding the request credentials ([#5043](https://github.com/opensearch-project/ml-commons/pull/5043))
* Read the stored script and target index mapping as the calling user when registering an agentic search template ([#5044](https://github.com/opensearch-project/ml-commons/pull/5044))
* Validate connector protocol, opt-in protocol settings, and `mutual_tls_enabled` on connector update, model register with inline connector, and model update — not only on connector create ([#5045](https://github.com/opensearch-project/ml-commons/pull/5045))

### Infrastructure

* Ignore flaky `RestChatAgentWithMcpConnectorIT.testChatAgentWithMcpStreamableHttpConnector` test ([#4943](https://github.com/opensearch-project/ml-commons/pull/4943))
* Use the admin client for the retention job index in `RestMemoryRetentionJobIntervalIT` ([#5024](https://github.com/opensearch-project/ml-commons/pull/5024))
* Skip the gRPC integration test when test files are not present ([#4970](https://github.com/opensearch-project/ml-commons/pull/4970))
* Onboard the issue dedupe GitHub workflow ([#4804](https://github.com/opensearch-project/ml-commons/pull/4804))
* Bump `1password/load-secrets-action` to v5.0.1 ([#4984](https://github.com/opensearch-project/ml-commons/pull/4984))

### Maintenance

* Force awssdk STS version to resolve build conflict from DynamoDB client transitive dependency ([#4998](https://github.com/opensearch-project/ml-commons/pull/4998))
* Force snakeyaml_engine version alignment in ml-commons ([#5007](https://github.com/opensearch-project/ml-commons/pull/5007))
* Update ml-commons build.sh to include `publishPluginZipPublicationToMavenLocal` for neural-search ([#5004](https://github.com/opensearch-project/ml-commons/pull/5004))
* Use Jackson 3.x for JSON processing ([#4981](https://github.com/opensearch-project/ml-commons/pull/4981))
* Rename resource sharing feature flag to the non-experimental key ([#5013](https://github.com/opensearch-project/ml-commons/pull/5013))
* Force jspecify to 1.0.1 to fix yamlRestTest dependency conflict ([#5023](https://github.com/opensearch-project/ml-commons/pull/5023))
* Add Eclipse P2 mirror to avoid `download.eclipse.org` outages ([#4980](https://github.com/opensearch-project/ml-commons/pull/4980))
* Guard `eclipse()` to spotless tasks and keep the P2 mirror on the pinned version ([#5001](https://github.com/opensearch-project/ml-commons/pull/5001))
* Add `ci.opensearch.org/m2/` mirror for plugin resolution ([#4949](https://github.com/opensearch-project/ml-commons/pull/4949))
