## Version 3.10.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.10.0

### Features

* Add client certificate authentication (mTLS) support for MCP connectors ([#5003](https://github.com/opensearch-project/ml-commons/pull/5003))
* Add `SearchTemplateFillTool` for agentic search template parameter filling ([#5106](https://github.com/opensearch-project/ml-commons/pull/5106))
* Expose `index_name` in `QueryPlanningTool` default user prompt so the LLM knows which index it is planning a query for ([#5127](https://github.com/opensearch-project/ml-commons/pull/5127))
* Include aggregation results in `SearchIndexTool` output when `return_raw_response` is false ([#5194](https://github.com/opensearch-project/ml-commons/pull/5194))
* Fill list-type parameters as JSON arrays in `SearchTemplateFillTool` ([#5163](https://github.com/opensearch-project/ml-commons/pull/5163))
* Return 409 when re-registering a model group with the same custom id ([#5155](https://github.com/opensearch-project/ml-commons/pull/5155))
* Validate ML search request fields against an allowlist and reject unsupported search features ([#5205](https://github.com/opensearch-project/ml-commons/pull/5205))

### Enhancements

* Derive sortable-aware field enums from all target index mappings for agentic search templates ([#5122](https://github.com/opensearch-project/ml-commons/pull/5122))
* Correct derived parameter and template descriptions for agentic search templates ([#5123](https://github.com/opensearch-project/ml-commons/pull/5123))
* Only describe a `should` clause as scoring-only when its enclosing `bool` makes it optional in agentic search templates ([#5164](https://github.com/opensearch-project/ml-commons/pull/5164))
* Parse connector endpoint URLs before matching the allowlist and tighten default AWS patterns ([#5192](https://github.com/opensearch-project/ml-commons/pull/5192))
* Address review feedback on connector endpoint validation for placeholder and logging handling ([#5196](https://github.com/opensearch-project/ml-commons/pull/5196))
* Apply the connector address policy to OpenAI- and Gemini-style HTTP streaming requests ([#5207](https://github.com/opensearch-project/ml-commons/pull/5207))
* Classify IPv6 unique-local, link-local, and NAT64 addresses in the connector private-IP check ([#5208](https://github.com/opensearch-project/ml-commons/pull/5208))
* Validate Vertex AI `batch_predict` URL at connector-create time to prevent orphaned billing jobs ([#5188](https://github.com/opensearch-project/ml-commons/pull/5188))
* Omit Bedrock `toolConfig` when the agent has no tools to avoid Converse API rejection ([#5215](https://github.com/opensearch-project/ml-commons/pull/5215))
* Keep model cache paths inside the cache root directory to prevent path traversal ([#5198](https://github.com/opensearch-project/ml-commons/pull/5198))
* Serve predictions from deployed nodes when a local model is partially deployed instead of rejecting all requests ([#5142](https://github.com/opensearch-project/ml-commons/pull/5142))
* Keep deployed models when the deploy task's coordinating node leaves or nodes join during a deploy ([#5143](https://github.com/opensearch-project/ml-commons/pull/5143))

### Bug Fixes

* Clear cached master keys when the ML config index is replaced to prevent decryption failures after snapshot restore ([#5166](https://github.com/opensearch-project/ml-commons/pull/5166))
* Fix races in master key cache invalidation after config index replacement ([#5199](https://github.com/opensearch-project/ml-commons/pull/5199))
* Clear node model cache on remote deploy failure without evicting concurrent deploys ([#5193](https://github.com/opensearch-project/ml-commons/pull/5193))
* Fix deploy tasks and model state getting stuck after nodes leave or cluster manager changes ([#5209](https://github.com/opensearch-project/ml-commons/pull/5209))
* Fix model auto redeploy during node join bursts to prevent retry exhaustion and permanent exclusion ([#5217](https://github.com/opensearch-project/ml-commons/pull/5217))
* Fix NPE when registering a `plan_execute_and_reflect` agent without a `parameters` block ([#5216](https://github.com/opensearch-project/ml-commons/pull/5216))
* Fix left-padded `LAST_TOKEN` pooling for decoder-only models and coerce attention mask dtype ([#4744](https://github.com/opensearch-project/ml-commons/pull/4744))
* Fix register task failure handling and legacy ML config mapping conflict ([#5200](https://github.com/opensearch-project/ml-commons/pull/5200))
* Pass the calling user into model group delete access validation and return proper 403/404 status codes ([#5219](https://github.com/opensearch-project/ml-commons/pull/5219))
* Apply access mode consistently for model groups and connectors without an owner ([#5197](https://github.com/opensearch-project/ml-commons/pull/5197))
* Serialize non-String tool output in MCP server `tools/call` to fix `ClassCastException` ([#5214](https://github.com/opensearch-project/ml-commons/pull/5214))
* Return the not-found error body on metadata-only update of a missing agentic search template ([#5120](https://github.com/opensearch-project/ml-commons/pull/5120))

### Infrastructure

* Raise the REST integration test task wait cap to 60 seconds for slow CI runners ([#5195](https://github.com/opensearch-project/ml-commons/pull/5195))
* Skip `RestSearchTemplateFillToolIT` on the containerized cluster CI leg ([#5165](https://github.com/opensearch-project/ml-commons/pull/5165))
* Increment version to 3.10.0-SNAPSHOT ([#5100](https://github.com/opensearch-project/ml-commons/pull/5100))

### Documentation

* Fix typo in input/output map within ml-inference processor documentation ([#3556](https://github.com/opensearch-project/ml-commons/pull/3556))
