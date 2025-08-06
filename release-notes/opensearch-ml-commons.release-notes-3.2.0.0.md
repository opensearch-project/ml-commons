## Version 3.2.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.2.0

### Features
* Initiate query planning tool ([#4006](https://github.com/opensearch-project/ml-commons/pull/4006))
* Add Execute Tool API ([#4035](https://github.com/opensearch-project/ml-commons/pull/4035))
* Implement create and add memory container API ([#4050](https://github.com/opensearch-project/ml-commons/pull/4050))

### Enhancements
* Add Default System Prompt for the query Planner tool ([#4046](https://github.com/opensearch-project/ml-commons/pull/4046))
* Add support for date time injection for agents ([#4008](https://github.com/opensearch-project/ml-commons/pull/4008))
* Expose message history limit for PER Agent ([#4016](https://github.com/opensearch-project/ml-commons/pull/4016))
* [Enhancement] Enhance validation for create connector API ([#3579](https://github.com/opensearch-project/ml-commons/pull/3579))
* [Enhancements] Sparse encoding/tokenize support TOKEN_ID format embedding ([#3963](https://github.com/opensearch-project/ml-commons/pull/3963))
* Add validation for creating uri in connectors ([#3972](https://github.com/opensearch-project/ml-commons/pull/3972))
* Enhance tool input parsing and add agentic rag tutorial ([#4023](https://github.com/opensearch-project/ml-commons/pull/4023))
* Run auto deploy remote model in partially deployed status ([#3423](https://github.com/opensearch-project/ml-commons/pull/3423))

### Bug Fixes
* Fix class cast exception for execute API ([#4010](https://github.com/opensearch-project/ml-commons/pull/4010))
* Fix delete connector/model group exception handling ([#4044](https://github.com/opensearch-project/ml-commons/pull/4044))
* Fix exposed connector URL in error message ([#3953](https://github.com/opensearch-project/ml-commons/pull/3953))
* Fix is_async status of agent execution task ([#3960](https://github.com/opensearch-project/ml-commons/pull/3960))
* Fix update model config invalid error ([#3994](https://github.com/opensearch-project/ml-commons/pull/3994))
* [ExceptionHandling] Throw proper 400 errors instead of 500 for agent execute and MCP ([#3988](https://github.com/opensearch-project/ml-commons/pull/3988))
* [FIX] allow partial updates to llm and memory fields in MLAgentUpdateInput ([#4040](https://github.com/opensearch-project/ml-commons/pull/4040))
* Fix the error status code and message for empty response ([#3968](https://github.com/opensearch-project/ml-commons/pull/3968))
* [MLSyncUpCron] Change info log to debug log to reduce logging ([#3948](https://github.com/opensearch-project/ml-commons/pull/3948))
* [FIX] Update lombok version for jdk24 ([#4026](https://github.com/opensearch-project/ml-commons/pull/4026))
* Fixing unit test ([#4037](https://github.com/opensearch-project/ml-commons/pull/4037))
* Ensure chat agent returns response when max iterations are reached ([#4031](https://github.com/opensearch-project/ml-commons/pull/4031))

### Infrastructure
* Bump gradle to 8.14 and update JDK to 24 ([#3983](https://github.com/opensearch-project/ml-commons/pull/3983))
* Adding more unit tests and upgrading jacoco ([#4057](https://github.com/opensearch-project/ml-commons/pull/4057))
* Keep .plugins-ml-config index for Integration test ([#3989](https://github.com/opensearch-project/ml-commons/pull/3989))
* Increase mcp code coverage and address comments in PR: #3883 ([#3908](https://github.com/opensearch-project/ml-commons/pull/3908))
* Adding unit tests for create and get memory container functionalities ([#4056](https://github.com/opensearch-project/ml-commons/pull/4056))
* Updating gradle version ([#4064](https://github.com/opensearch-project/ml-commons/pull/4064))

### Documentation
* Add Documentation for creating Neural Sparse Remote Model ([#3857](https://github.com/opensearch-project/ml-commons/pull/3857))
* Add tutorials for language_identification during ingest ([#3966](https://github.com/opensearch-project/ml-commons/pull/3966))
* Update link to the model in the aleph alpha blueprint ([#3980](https://github.com/opensearch-project/ml-commons/pull/3980))
* Add agentic rag tutorial ([#4045](https://github.com/opensearch-project/ml-commons/pull/4045))
* Notebook for step by step in multi-modal search in ml-inference processor ([#3944](https://github.com/opensearch-project/ml-commons/pull/3944))