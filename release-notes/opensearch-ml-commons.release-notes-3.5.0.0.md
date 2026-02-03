## Version 3.5.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.5.0

### Features

* AG-UI support in Agent Framework ([#4549](https://github.com/opensearch-project/ml-commons/pull/4549))
* Introduce hook and context management to OpenSearch Agents ([#4388](https://github.com/opensearch-project/ml-commons/pull/4388))
* Support Gemini function calling and simplified API ([#4570](https://github.com/opensearch-project/ml-commons/pull/4570))
* Enhancement: Enable remote conversational agentic memory through REST HTTP call ([#4564](https://github.com/opensearch-project/ml-commons/pull/4564))
* FEATURE: Summarize the steps when max steps limit by @PauiC ([#4575](https://github.com/opensearch-project/ml-commons/pull/4575))

### Enhancements

* Accept passthrough headers in agent execute ([#4544](https://github.com/opensearch-project/ml-commons/pull/4544))
* Add JSON validation in StringUtils fromJson/toJson ([#4520](https://github.com/opensearch-project/ml-commons/pull/4520))
* Add Nova MME blueprint ([#4452](https://github.com/opensearch-project/ml-commons/pull/4452))
* Add metrics correlation changes ([#4517](https://github.com/opensearch-project/ml-commons/pull/4517))
* Agents: Filter out multi tool calls ([#4523](https://github.com/opensearch-project/ml-commons/pull/4523))
* Enabling custom named connector actions and support PUT/DELETE action ([#4538](https://github.com/opensearch-project/ml-commons/pull/4538))
* Monitoring: Accurately capture predict failures ([#4525](https://github.com/opensearch-project/ml-commons/pull/4525))
* Sanitize error messages to prevent user input reflection ([#4315](https://github.com/opensearch-project/ml-commons/pull/4315))
* Support unified pre/post processing for Nova MME model ([#4425](https://github.com/opensearch-project/ml-commons/pull/4425))
* Support guardrail in streaming ([#4550](https://github.com/opensearch-project/ml-commons/pull/4550))

### Bug Fixes

* Fix stream threadpool ([#4574](https://github.com/opensearch-project/ml-commons/pull/4574))
* [BUG FIX] Add expiry time to master key cache to prevent stale encryption key usage ([#4543](https://github.com/opensearch-project/ml-commons/pull/4543))
* Converting illegalstate exception to opensearch status exception with 400 status ([#4484](https://github.com/opensearch-project/ml-commons/pull/4484))
* Fix jackson annotations version ([#4521](https://github.com/opensearch-project/ml-commons/pull/4521))
* Fix miss npe for index insight ([#4562](https://github.com/opensearch-project/ml-commons/pull/4562))
* Fix validation inputStream ([#4471](https://github.com/opensearch-project/ml-commons/pull/4471))
* Fix: Modify the order of listener registration ([#4398](https://github.com/opensearch-project/ml-commons/pull/4398))
* Make credential optional in mcp connectors ([#4524](https://github.com/opensearch-project/ml-commons/pull/4524))

### Maintenance

* Bump netty version to resolve CVE-2025-58057 ([#4576](https://github.com/opensearch-project/ml-commons/pull/4576))
* Remove QPT model ID autofill logic in Agent Registration ([#4473](https://github.com/opensearch-project/ml-commons/pull/4473))
* [Setting] Add setting for enabling/disabling simplified agent registration ([#4559](https://github.com/opensearch-project/ml-commons/pull/4559))

### Refactoring

* Simplify Agent Interface and standardize agent execution ([#4546](https://github.com/opensearch-project/ml-commons/pull/4546))
* Rename response to context_management_name ([#4566](https://github.com/opensearch-project/ml-commons/pull/4566))
* Rename to unified agent interface ([#4561](https://github.com/opensearch-project/ml-commons/pull/4561))