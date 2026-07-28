## Version 3.8.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.8.0

### Features

* Support MCP for Flow and Conversational Flow agents ([#4776](https://github.com/opensearch-project/ml-commons/pull/4776))
* Add REST API to list all tools available in an MCP server ([#4705](https://github.com/opensearch-project/ml-commons/pull/4705))
* Introduce gRPC streaming support for ML prediction and agent execution ([#4790](https://github.com/opensearch-project/ml-commons/pull/4790))
* Add memory retention policy data model, pinned field, and API wiring (1/2) ([#4914](https://github.com/opensearch-project/ml-commons/pull/4914))
* Add memory retention job, cluster defaults, and hardening (2/2) ([#4918](https://github.com/opensearch-project/ml-commons/pull/4918))

### Enhancements

* Support tool description override in MCP connector ([#4766](https://github.com/opensearch-project/ml-commons/pull/4766))
* Refine planner agent prompt ([#4822](https://github.com/opensearch-project/ml-commons/pull/4822))
* Add JSON logs to agent flow ([#4740](https://github.com/opensearch-project/ml-commons/pull/4740))
* Serve `/_plugins/_ml/mcp` on demand instead of a per-node cache ([#4907](https://github.com/opensearch-project/ml-commons/pull/4907))

### Bug Fixes

* Fix MCP agent execution hang after MCP SDK 1.1.1 bump ([#4816](https://github.com/opensearch-project/ml-commons/pull/4816))
* Return HTTP 429 instead of 500 on connection pool acquire timeout ([#4852](https://github.com/opensearch-project/ml-commons/pull/4852))
* Fix `system_prompt` not reaching Bedrock Converse ([#4871](https://github.com/opensearch-project/ml-commons/pull/4871))
* Fix SearchIndexTool frequently failing ([#4530](https://github.com/opensearch-project/ml-commons/pull/4530))
* Retry transient HTTP errors from any remote service ([#4882](https://github.com/opensearch-project/ml-commons/pull/4882))
* Fix ModelGuardrail and LocalRegexGuardrail fail-open bugs (now fail-closed) ([#4904](https://github.com/opensearch-project/ml-commons/pull/4904))
* Fix gRPC stream double-termination ([#4927](https://github.com/opensearch-project/ml-commons/pull/4927))
* Validate model archives to block arbitrary code execution ([#4929](https://github.com/opensearch-project/ml-commons/pull/4929))
* Return correct HTTP status on memory-container failures instead of 500 ([#4930](https://github.com/opensearch-project/ml-commons/pull/4930))

### Infrastructure

* Pin GitHub Actions to commit SHAs ([#4828](https://github.com/opensearch-project/ml-commons/pull/4828))
* Onboard new reusable backport-pr GitHub workflow ([#4873](https://github.com/opensearch-project/ml-commons/pull/4873))
* Update maven2 mirror repository URL order ([#4887](https://github.com/opensearch-project/ml-commons/pull/4887))
* Fix flaky IT `testChatAgentWithMcpStreamableHttpConnector` and add timeout ([#4851](https://github.com/opensearch-project/ml-commons/pull/4851))
* Retry integration tests on transient remote-service 5xx errors ([#4896](https://github.com/opensearch-project/ml-commons/pull/4896))
* Disable fail-fast on CI matrix so Java 21/25 jobs run independently ([#4899](https://github.com/opensearch-project/ml-commons/pull/4899))
* Fix flaky CI: httpbin dependency, MCP tool sync race, and SearchModelGroupITTests hang ([#4903](https://github.com/opensearch-project/ml-commons/pull/4903))
* Add `jiapingzeng` as maintainer ([#4910](https://github.com/opensearch-project/ml-commons/pull/4910))
* Bump bc-fips to 2.1.3 in plugin test classpath to fix CVE-2026-8149 ([#4923](https://github.com/opensearch-project/ml-commons/pull/4923))

### Documentation

* Pass Gemini API key via header instead of URL in connector blueprint ([#4874](https://github.com/opensearch-project/ml-commons/pull/4874))
* Add LLM judgment connector blueprints ([#4878](https://github.com/opensearch-project/ml-commons/pull/4878))
* Use http instead of https for Ollama connector endpoint blueprint ([#4885](https://github.com/opensearch-project/ml-commons/pull/4885))
