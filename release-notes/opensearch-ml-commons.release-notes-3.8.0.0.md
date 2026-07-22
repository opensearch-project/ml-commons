## Version 3.8.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.8.0

### Features

* Add JSON logging to the Agent execution flow ([#4740](https://github.com/opensearch-project/ml-commons/pull/4740))
* Introduce gRPC streaming support for ML prediction and agent execution ([#4790](https://github.com/opensearch-project/ml-commons/pull/4790))
* Add REST API to list all tools available in an external MCP server ([#4705](https://github.com/opensearch-project/ml-commons/pull/4705))
* Support MCP for Flow and Conversational Flow agents ([#4776](https://github.com/opensearch-project/ml-commons/pull/4776))
* Add memory retention job infrastructure with cluster defaults and hardening ([#4918](https://github.com/opensearch-project/ml-commons/pull/4918))
* Add retention policy data model, pinned field, and API wiring for memory retention lifecycle ([#4914](https://github.com/opensearch-project/ml-commons/pull/4914))

### Enhancements

* Return 429 instead of 500 on connection pool acquire timeout for remote connector requests ([#4852](https://github.com/opensearch-project/ml-commons/pull/4852))
* Retry transient HTTP errors (429, 5xx) from any remote service, not just AWS ([#4882](https://github.com/opensearch-project/ml-commons/pull/4882))
* Refactor planner agent prompts to separate common and investigation-specific system prompts ([#4822](https://github.com/opensearch-project/ml-commons/pull/4822))
* Serve MCP endpoint on demand from index instead of per-node cache, removing registration race ([#4905](https://github.com/opensearch-project/ml-commons/pull/4905))

### Bug Fixes

* Fix SearchIndexTool JSON parsing to handle malformed LLM-generated queries without retries ([#4530](https://github.com/opensearch-project/ml-commons/pull/4530))
* Fix ModelGuardrail and LocalRegexGuardrail to fail-closed instead of fail-open on timeout or error ([#4904](https://github.com/opensearch-project/ml-commons/pull/4904))
* Fix system_prompt not reaching Bedrock Converse API from RAG response processor ([#4871](https://github.com/opensearch-project/ml-commons/pull/4871))
* Fix Gemini connector blueprint to pass API key via header instead of URL ([#4874](https://github.com/opensearch-project/ml-commons/pull/4874))
* Use http instead of https for Ollama connector endpoint in blueprint ([#4885](https://github.com/opensearch-project/ml-commons/pull/4885))

### Infrastructure

* Disable fail-fast on CI matrix so Java 21 and 25 jobs run independently ([#4899](https://github.com/opensearch-project/ml-commons/pull/4899))
* Fix flaky CI: replace httpbin dependency, fix MCP tool sync race, and SearchModelGroupITTests hang ([#4903](https://github.com/opensearch-project/ml-commons/pull/4903))
* Fix flaky IT testChatAgentWithMcpStreamableHttpConnector by polling MCP tool registration and adding timeout ([#4851](https://github.com/opensearch-project/ml-commons/pull/4851))
* Pin GitHub Actions to commit SHAs for supply chain security ([#4828](https://github.com/opensearch-project/ml-commons/pull/4828))
* Onboard new backport-pr reusable GitHub workflow ([#4873](https://github.com/opensearch-project/ml-commons/pull/4873))
* Retry integration tests on transient remote-service 5xx errors instead of failing CI ([#4896](https://github.com/opensearch-project/ml-commons/pull/4896))
* Update maven2 mirror repository URL order ([#4887](https://github.com/opensearch-project/ml-commons/pull/4887))
* Bump bc-fips to 2.1.3 in plugin test classpath to fix CVE-2026-8149 ([#4923](https://github.com/opensearch-project/ml-commons/pull/4923))

### Documentation

* Add LLM judgment connector blueprints for Search Relevance Workbench ([#4878](https://github.com/opensearch-project/ml-commons/pull/4878))
* Add jiapingzeng as maintainer ([#4910](https://github.com/opensearch-project/ml-commons/pull/4910))

### Maintenance

* Version bump to 3.8.0 ([#4854](https://github.com/opensearch-project/ml-commons/pull/4854))
