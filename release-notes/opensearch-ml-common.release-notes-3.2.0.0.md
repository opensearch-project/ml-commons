## Version 3.2.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.2.0

### Features
* Initiate query planning tool ([#4006](https://github.com/opensearch-project/ml-commons/pull/4006))
* Add Execute Tool API ([#4035](https://github.com/opensearch-project/ml-commons/pull/4035))
* Implement create and add memory container API ([#4050](https://github.com/opensearch-project/ml-commons/pull/4050))
* Enable AI-Oriented memory operation on Memory APIs (Add, Search, Update & Delete) ([#4055](https://github.com/opensearch-project/ml-commons/pull/4055))
* Support output filter, unify tool parameter handling and improve SearchIndexTool output parsing ([#4053](https://github.com/opensearch-project/ml-commons/pull/4053))
* Delete memory container API ([#4027](https://github.com/opensearch-project/ml-commons/pull/4027))
* GET memory API ([#4069](https://github.com/opensearch-project/ml-commons/pull/4069))

### Enhancements
* Add Default System Prompt for the query Planner tool ([#4046](https://github.com/opensearch-project/ml-commons/pull/4046))
* Add support for date time injection for agents ([#4008](https://github.com/opensearch-project/ml-commons/pull/4008))
* Expose message history limit for PER Agent ([#4016](https://github.com/opensearch-project/ml-commons/pull/4016))
* [Enhancement] Enhance validation for create connector API ([#3579](https://github.com/opensearch-project/ml-commons/pull/3579))
* [Enhancements] Sparse encoding/tokenize support TOKEN_ID format embedding ([#3963](https://github.com/opensearch-project/ml-commons/pull/3963))
* Add validation for creating uri in connectors ([#3972](https://github.com/opensearch-project/ml-commons/pull/3972))
* Enhance tool input parsing and add agentic rag tutorial ([#4023](https://github.com/opensearch-project/ml-commons/pull/4023))
* Run auto deploy remote model in partially deployed status ([#3423](https://github.com/opensearch-project/ml-commons/pull/3423))
* [ExceptionHandling] Throw proper 400 errors instead of 500 for agent execute and MCP ([#3988](https://github.com/opensearch-project/ml-commons/pull/3988))
* Tuning PER Agent Prompts ([#4059](https://github.com/opensearch-project/ml-commons/pull/4059))
* Add feature flag for agentic search ([#4021](https://github.com/opensearch-project/ml-commons/pull/4021))
* Adding feature flag for agentic memory ([#4067](https://github.com/opensearch-project/ml-commons/pull/4067))
* Add feature flag to delete mem container ([#4072](https://github.com/opensearch-project/ml-commons/pull/4072))

### Bug Fixes
* Fix class cast exception for execute API ([#4010](https://github.com/opensearch-project/ml-commons/pull/4010))
* Fix delete connector/model group exception handling ([#4044](https://github.com/opensearch-project/ml-commons/pull/4044))
* Fix exposed connector URL in error message ([#3953](https://github.com/opensearch-project/ml-commons/pull/3953))
* Fix is_async status of agent execution task ([#3960](https://github.com/opensearch-project/ml-commons/pull/3960))
* Fix update model config invalid error ([#3994](https://github.com/opensearch-project/ml-commons/pull/3994))
* [FIX] allow partial updates to llm and memory fields in MLAgentUpdateInput ([#4040](https://github.com/opensearch-project/ml-commons/pull/4040))
* Fix the error status code and message for empty response ([#3968](https://github.com/opensearch-project/ml-commons/pull/3968))
* [MLSyncUpCron] Change info log to debug log to reduce logging ([#3948](https://github.com/opensearch-project/ml-commons/pull/3948))
* Fixing unit test for user_requested_tenant_access ([#4037](https://github.com/opensearch-project/ml-commons/pull/4037))
* Ensure chat agent returns response when max iterations are reached ([#4031](https://github.com/opensearch-project/ml-commons/pull/4031))

### Maintenance
* Increase mcp code coverage and address comments in PR: #3883 ([#3908](https://github.com/opensearch-project/ml-commons/pull/3908))
* Fix: change log level for sync up job ([#3948](https://github.com/opensearch-project/ml-commons/pull/3948))
* Keep .plugins-ml-config index for Integration test ([#3989](https://github.com/opensearch-project/ml-commons/pull/3989))
* Adding unit tests for create and get memory container functionalities ([#4056](https://github.com/opensearch-project/ml-commons/pull/4056))
* Adding more unit tests and upgrading jacoco ([#4057](https://github.com/opensearch-project/ml-commons/pull/4057))
* CVE fix: beanutils ([#4062](https://github.com/opensearch-project/ml-commons/pull/4062))

### Infrastructure
* Bump gradle to 8.14 and update JDK to 24 ([#3983](https://github.com/opensearch-project/ml-commons/pull/3983))
* Updating gradle version ([#4064](https://github.com/opensearch-project/ml-commons/pull/4064))
* [FIX] Update lombok version for jdk24 ([#4026](https://github.com/opensearch-project/ml-commons/pull/4026))

### Documentation
* Add multi modal tutorial using ml inference processor ([#3576](https://github.com/opensearch-project/ml-commons/pull/3576))
* Add blueprint for semantic highlighter model on AWS Sagemaker ([#3879](https://github.com/opensearch-project/ml-commons/pull/3879))
* Add Documentation for creating Neural Sparse Remote Model ([#3857](https://github.com/opensearch-project/ml-commons/pull/3857))
* Add tutorials for language_identification during ingest ([#3966](https://github.com/opensearch-project/ml-commons/pull/3966))
* Update link to the model in the aleph alpha blueprint ([#3980](https://github.com/opensearch-project/ml-commons/pull/3980))
* Add agentic rag tutorial ([#4045](https://github.com/opensearch-project/ml-commons/pull/4045))
* Notebook for step by step in multi-modal search in ml-inference processor ([#3944](https://github.com/opensearch-project/ml-commons/pull/3944))