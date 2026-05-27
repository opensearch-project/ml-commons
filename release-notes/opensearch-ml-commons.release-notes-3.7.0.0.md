## Version 3.7.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.7.0

### Features

* Add `provisioned_by` field to ML agents, connectors, and models for adoption metrics attribution ([#4754](https://github.com/opensearch-project/ml-commons/pull/4754))
* Enable dynamic header support in ML connectors using `${parameters.*}` placeholders for per-request values ([#4817](https://github.com/opensearch-project/ml-commons/pull/4817))

### Enhancements

* Add private IP security controls and ReDoS protection for ML connectors ([#4818](https://github.com/opensearch-project/ml-commons/pull/4818))
* Add structured output (constrained decoding) support for agentic memory fact extraction ([#4824](https://github.com/opensearch-project/ml-commons/pull/4824))
* Strengthen enforcement of `trusted_connector_endpoints_regex` across all outbound connector request paths ([#4826](https://github.com/opensearch-project/ml-commons/pull/4826))
* Simplify USER_PREFERENCE extraction prompt to produce plain sentences, fixing silent JSON parse failures with smaller LLMs ([#4798](https://github.com/opensearch-project/ml-commons/pull/4798))

### Bug Fixes

* Fix Jackson exception handling due to 3.x release line migration ([#4784](https://github.com/opensearch-project/ml-commons/pull/4784))
* Fix flaky `RestMLInferenceSearchResponseProcessorIT` connection pool timeout by optimizing test setup ([#4781](https://github.com/opensearch-project/ml-commons/pull/4781))
* Fix tool output JSON escape issue when replacing placeholders in connector request bodies ([#4794](https://github.com/opensearch-project/ml-commons/pull/4794))
* Propagate stream errors properly to callers instead of swallowing them ([#4792](https://github.com/opensearch-project/ml-commons/pull/4792))
* Fix `model_parameters` being silently ignored in inferenceConfig and incorrect SourceType error message in V2 agents ([#4833](https://github.com/opensearch-project/ml-commons/pull/4833))

### Infrastructure

* Add issues write permission to untriaged label workflow ([#4827](https://github.com/opensearch-project/ml-commons/pull/4827))
* Update Gradle to 9.4.1 and Jacoco to 0.8.14 to fix CI build failures ([#4811](https://github.com/opensearch-project/ml-commons/pull/4811))
* Add `akolarkunnu` as maintainer ([#4788](https://github.com/opensearch-project/ml-commons/pull/4788))
* Update GitHub username reference from `rithin-pullela-aws` to `rithinpullela` ([#4825](https://github.com/opensearch-project/ml-commons/pull/4825))

### Documentation

* Add Yandex Cloud AI Studio standard embedding connector blueprint ([#4810](https://github.com/opensearch-project/ml-commons/pull/4810))
* Add Yandex Cloud AI Studio embeddings connector blueprint ([#4469](https://github.com/opensearch-project/ml-commons/pull/4469))

### Maintenance

* Support Jackson 3.x release line and update MCP SDK to 1.1.1 and json-schema-validation to 3.0.4 ([#4795](https://github.com/opensearch-project/ml-commons/pull/4795))