## Version 3.2.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.2.0

### Enhancements
* [Enhancement] Enhance validation for create connector API ([#3579](https://github.com/opensearch-project/ml-commons/pull/3579))
* [Enhancements] Sparse encoding/tokenize support TOKEN_ID format embedding ([#3963](https://github.com/opensearch-project/ml-commons/pull/3963))
* Add validation for creating uri in connectors ([#3972](https://github.com/opensearch-project/ml-commons/pull/3972))
* Run auto deploy remote model in partially deployed status ([#3423](https://github.com/opensearch-project/ml-commons/pull/3423))

### Bug Fixes
* Fix class cast exception for execute API ([#4010](https://github.com/opensearch-project/ml-commons/pull/4010))
* Fix exposed connector URL in error message ([#3953](https://github.com/opensearch-project/ml-commons/pull/3953))
* Fix is_async status of agent execution task ([#3960](https://github.com/opensearch-project/ml-commons/pull/3960))
* Fix update model config invalid error ([#3994](https://github.com/opensearch-project/ml-commons/pull/3994))
* Fix the error status code and message for empty response ([#3968](https://github.com/opensearch-project/ml-commons/pull/3968))
* [MLSyncUpCron] Change info log to debug log to reduce logging ([#3948](https://github.com/opensearch-project/ml-commons/pull/3948))

### Infrastructure
* [AUTO] Increment version to 3.2.0-SNAPSHOT ([#3942](https://github.com/opensearch-project/ml-commons/pull/3942))
* Keep .plugins-ml-config index for Integration test ([#3989](https://github.com/opensearch-project/ml-commons/pull/3989))
* Increase mcp code coverage and address comments in PR: #3883 ([#3908](https://github.com/opensearch-project/ml-commons/pull/3908))

### Documentation
* Add Documentation for creating Neural Sparse Remote Model ([#3857](https://github.com/opensearch-project/ml-commons/pull/3857))
* Add tutorials for language_identification during ingest ([#3966](https://github.com/opensearch-project/ml-commons/pull/3966))
* Add multi modal tutorial using ml inference processor ([#3576](https://github.com/opensearch-project/ml-commons/pull/3576))