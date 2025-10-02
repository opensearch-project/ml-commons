## Version 3.3.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.3.0

### Features
* Add Get Agent to ML Client ([#4180](https://github.com/opensearch-project/ml-commons/pull/4180))
* Add WriteToScratchPad and ReadFromScratchPad tools ([#4192](https://github.com/opensearch-project/ml-commons/pull/4192))
* Add colpali blueprint ([#4130](https://github.com/opensearch-project/ml-commons/pull/4130))
* Add global resource support ([#4003](https://github.com/opensearch-project/ml-commons/pull/4003))
* Add ml-commons passthrough post process function ([#4111](https://github.com/opensearch-project/ml-commons/pull/4111))
* Add output transformation support with mean pooling for ML inference processors ([#4236](https://github.com/opensearch-project/ml-commons/pull/4236))
* Add tutorial for agentic search ([#4127](https://github.com/opensearch-project/ml-commons/pull/4127))
* Adding query planning tool search template validation and integration tests ([#4177](https://github.com/opensearch-project/ml-commons/pull/4177))
* Ollama connector blueprint ([#4160](https://github.com/opensearch-project/ml-commons/pull/4160))
* Onboards to centralized resource access control mechanism for ml-model-group ([#3715](https://github.com/opensearch-project/ml-commons/pull/3715))
* Parameter Passing for Predict via Remote Connector ([#4121](https://github.com/opensearch-project/ml-commons/pull/4121))
* Refactor Agentic Memory ([#4218](https://github.com/opensearch-project/ml-commons/pull/4218))
* Search Template Support for QueryPlanningTool ([#4154](https://github.com/opensearch-project/ml-commons/pull/4154))
* Support multi-tenancy for LocalRegexGuardrail ([#4120](https://github.com/opensearch-project/ml-commons/pull/4120))
* Tutorial on agentic memory with strands agents ([#4125](https://github.com/opensearch-project/ml-commons/pull/4125))
* [Agentic Search] Support Query Planner Tool with Conversational Agent ([#4203](https://github.com/opensearch-project/ml-commons/pull/4203))
* [FEATURE] Add Index Insight Feature ([#4088](https://github.com/opensearch-project/ml-commons/pull/4088))
* [FEATURE] Agent Execute Stream ([#4212](https://github.com/opensearch-project/ml-commons/pull/4212))
* [FEATURE] Predict Stream ([#4187](https://github.com/opensearch-project/ml-commons/pull/4187))
* [MCP Connector] MCP Connectors for streamable HTTP ([#4169](https://github.com/opensearch-project/ml-commons/pull/4169))
* [MCP Server] Support Streamable HTTP and deprecate SSE in MCP server ([#4162](https://github.com/opensearch-project/ml-commons/pull/4162))
* [Memory] Add updated time to message ([#4201](https://github.com/opensearch-project/ml-commons/pull/4201))
* [Metrics] Introduce agent metrics & Add is_hidden tag for model metrics ([#4221](https://github.com/opensearch-project/ml-commons/pull/4221))
* Add processor chain and add support for model and tool ([#4093](https://github.com/opensearch-project/ml-commons/pull/4093))

### Enhancements
* Update interaction with failure message on agent execution failure ([#4198](https://github.com/opensearch-project/ml-commons/pull/4198))
* Add PlainNumberAdapter and corresponding tests for Gson in SearchIndexTool ([#4133](https://github.com/opensearch-project/ml-commons/pull/4133))
* Move HttpClientFactory to common to expose to other components ([#4175](https://github.com/opensearch-project/ml-commons/pull/4175))
* Change the setting name to same naming convention with others ([#4215](https://github.com/opensearch-project/ml-commons/pull/4215))
* Enabling agentic memory feature by default as we are going GA ([#4240](https://github.com/opensearch-project/ml-commons/pull/4240))

### Bug Fixes
* Fix NPE when execute flow agent with mutli tenancy is off ([#4189](https://github.com/opensearch-project/ml-commons/pull/4189))
* Fix claude model it ([#4167](https://github.com/opensearch-project/ml-commons/pull/4167))
* Fix error_prone_annotations jar hell ([#4214](https://github.com/opensearch-project/ml-commons/pull/4214))
* Fix failing UTs and increment version to 3.3.0-SNAPSHOT ([#4132](https://github.com/opensearch-project/ml-commons/pull/4132))
* Fix jdt formatter error ([#4151](https://github.com/opensearch-project/ml-commons/pull/4151))
* Fix missing RAG response from generative_qa_parameters ([#4118](https://github.com/opensearch-project/ml-commons/pull/4118))
* Fix model deploy issue and address other comments in #4003 ([#4207](https://github.com/opensearch-project/ml-commons/pull/4207))
* Fix: refactor memory delete by query API to avoid anti pattern ([#4234](https://github.com/opensearch-project/ml-commons/pull/4234))
* Fix MLTaskState enum serialization errors ([#4158](https://github.com/opensearch-project/ml-commons/pull/4158))
* Fix connector tool IT ([#4233](https://github.com/opensearch-project/ml-commons/pull/4233))
* Agent/Tool Parsing Fixes ([#4138](https://github.com/opensearch-project/ml-commons/pull/4138))
* [Metrics Framework] Fix version checking logic for starting the stats collector job ([#4220](https://github.com/opensearch-project/ml-commons/pull/4220))
* Fixing build issue in ml-commons ([#4210](https://github.com/opensearch-project/ml-commons/pull/4210))
* Fixing metrics correlation algorithm ([#4200](https://github.com/opensearch-project/ml-commons/pull/4200))
* Fixing validate access for multi-tenancy ([#4196](https://github.com/opensearch-project/ml-commons/pull/4196))
* Make MLSdkAsyncHttpResponseHandler return IllegalArgumentException ([#4182](https://github.com/opensearch-project/ml-commons/pull/4182))
* Fix Cohere IT ([#4174](https://github.com/opensearch-project/ml-commons/pull/4174))
* Skip the model interface validation for batch predict ([#4219](https://github.com/opensearch-project/ml-commons/pull/4219))

### Infrastructure
* Update maintainer list ([#4139](https://github.com/opensearch-project/ml-commons/pull/4139))

### Documentation
* Change suggested instance type in tutorial ([#4145](https://github.com/opensearch-project/ml-commons/pull/4145))

### Maintenance
* Adding more unit tests ([#4124](https://github.com/opensearch-project/ml-commons/pull/4124))
* Adding more unit tests ([#4126](https://github.com/opensearch-project/ml-commons/pull/4126))
* Move common string ([#4173](https://github.com/opensearch-project/ml-commons/pull/4173))
* Updating gson version to resolve conflict coming from core ([#4176](https://github.com/opensearch-project/ml-commons/pull/4176))