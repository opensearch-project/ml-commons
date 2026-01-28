## Version 3.5.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.5.0

### Features
* FEATURE: Summarize the steps when max steps limit by @PauiC ([#4575](https://github.com/opensearch-project/ml-commons/pull/4575))
* Support Gemini function calling and simplified API ([#4570](https://github.com/opensearch-project/ml-commons/pull/4570))
* AG-UI support in Agent Framework ([#4549](https://github.com/opensearch-project/ml-commons/pull/4549))
* Simplify Agent Interface and standardize agent execution ([#4546](https://github.com/opensearch-project/ml-commons/pull/4546))
* [FEATURE] Add an option to turn on and off the certificate validation of llm connectors ([#4394](https://github.com/opensearch-project/ml-commons/pull/4394))
* Introduce hook and context management to OpenSearch Agents ([#4388](https://github.com/opensearch-project/ml-commons/pull/4388))
* Add Nova MME blueprint ([#4452](https://github.com/opensearch-project/ml-commons/pull/4452))
* Accept passthrough headers in agent execute ([#4544](https://github.com/opensearch-project/ml-commons/pull/4544))

### Enhancements
* Enhancement: Enable remote conversational agentic memory through REST HTTP call ([#4564](https://github.com/opensearch-project/ml-commons/pull/4564))
* Enabling custom named connector actions and support PUT/DELETE action ([#4538](https://github.com/opensearch-project/ml-commons/pull/4538))
* Support unified pre/post processing for Nova MME model ([#4425](https://github.com/opensearch-project/ml-commons/pull/4425))
* Sanitize error messages to prevent user input reflection ([#4315](https://github.com/opensearch-project/ml-commons/pull/4315))
* [Setting] Add setting for enabling/disabling simplified agent registration ([#4559](https://github.com/opensearch-project/ml-commons/pull/4559))
* Remove QPT model ID autofill logic in Agent Registration ([#4473](https://github.com/opensearch-project/ml-commons/pull/4473))
* Agents: Filter out multi tool calls ([#4523](https://github.com/opensearch-project/ml-commons/pull/4523))
* Monitoring: Accurately capture predict failures ([#4525](https://github.com/opensearch-project/ml-commons/pull/4525))
* Support guardrail in streaming ([#4550](https://github.com/opensearch-project/ml-commons/pull/4550))
* Make credential optional in mcp connectors ([#4524](https://github.com/opensearch-project/ml-commons/pull/4524))
* Add JSON validation in StringUtils fromJson/toJson ([#4520](https://github.com/opensearch-project/ml-commons/pull/4520))
* Add metrics correlation changes ([#4517](https://github.com/opensearch-project/ml-commons/pull/4517))
* Allow higher maximum number of batch inference job tasks ([#4474](https://github.com/opensearch-project/ml-commons/pull/4474))

### Bug Fixes
* Bump netty version to resolve CVE-2025-58057 ([#4576](https://github.com/opensearch-project/ml-commons/pull/4576))
* Fix stream threadpool ([#4574](https://github.com/opensearch-project/ml-commons/pull/4574))
* fix miss npe for index insight ([#4562](https://github.com/opensearch-project/ml-commons/pull/4562))
* [BUG FIX] Add expiry time to master key cache to prevent stale encryption key usage ([#4543](https://github.com/opensearch-project/ml-commons/pull/4543))
* Rename response to context_management_name ([#4566](https://github.com/opensearch-project/ml-commons/pull/4566))
* Rename to unified agent interface ([#4561](https://github.com/opensearch-project/ml-commons/pull/4561))
* Fix: Modify the order of listener registration ([#4398](https://github.com/opensearch-project/ml-commons/pull/4398))
* fix jackson annotations version ([#4521](https://github.com/opensearch-project/ml-commons/pull/4521))
* Fix validation inputStream ([#4471](https://github.com/opensearch-project/ml-commons/pull/4471))
* converting illegalstate exception to opensearch status exception with 400 status ([#4484](https://github.com/opensearch-project/ml-commons/pull/4484))
* Fix tool used error message not proper escaped in MLChatAgentRunner ([#4410](https://github.com/opensearch-project/ml-commons/pull/4410))
* Fix several bugs on agentic memory ([#4476](https://github.com/opensearch-project/ml-commons/pull/4476))