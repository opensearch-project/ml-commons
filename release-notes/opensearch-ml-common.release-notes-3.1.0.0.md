## Version 3.1.0.0 Release Notes

Compatible with OpenSearch 3.1.0.0

### Enhancements
* Support persisting MCP tools in system index (#3874)[https://github.com/opensearch-project/ml-commons/pull/3874]
* [Agent] PlanExecuteReflect: Return memory early to track progress (#3884)[https://github.com/opensearch-project/ml-commons/pull/3884]
* Add space type mapping for pre-trained embedding models, add new additional_config field and BaseModelConfig class (#3786)[https://github.com/opensearch-project/ml-commons/pull/3786]
* support customized message endpoint and addressing comments (#3810)[https://github.com/opensearch-project/ml-commons/pull/3810]
* Add custom SSE endpoint for the MCP Client (#3891)[https://github.com/opensearch-project/ml-commons/pull/3891]
* Expose Update Agent API (#3820)[https://github.com/opensearch-project/ml-commons/pull/3902]
* Use function calling for existing LLM interfaces (#3888)[https://github.com/opensearch-project/ml-commons/pull/3888]
* Add error handling for plan&execute agent (#3845)[https://github.com/opensearch-project/ml-commons/pull/3845]
* Metrics framework integration with ml-commons (#3661)[https://github.com/opensearch-project/ml-commons/pull/3661]

### Bug Fixes
* Fix connector private IP validation when executing agent without remote model (#3862)[https://github.com/opensearch-project/ml-commons/pull/3862]
* for inline model connector name isn't required (#3882)[https://github.com/opensearch-project/ml-commons/pull/3882]
* fix the tutorial in AIConnectorHelper when fetching domain_url (#3852)[https://github.com/opensearch-project/ml-commons/pull/3852]
* Adds Json Parsing to nested object during update Query step in ML Inference Request processor (#3856)[https://github.com/opensearch-project/ml-commons/pull/3856]
* adding / as a valid character (#3854)[https://github.com/opensearch-project/ml-commons/pull/3854]
* quick fix for guava noclass issue (#3844)[https://github.com/opensearch-project/ml-commons/pull/3844]
* Fix python client not able to connect to MCP server issue (#3822)[https://github.com/opensearch-project/ml-commons/pull/3822]
* excluding circuit breaker for Agent (#3814)[https://github.com/opensearch-project/ml-commons/pull/3814]
* adding tenantId to the connector executor when this is inline connector (#3837)[https://github.com/opensearch-project/ml-commons/pull/3837]
* add validation for name and description for model model group and connector resources (#3805)[https://github.com/opensearch-project/ml-commons/pull/3805]
* Don't convert schema-defined strings to other types during validation (#3761)
* Fixed NPE for connector retrying policy (#3909)[https://github.com/opensearch-project/ml-commons/pull/3909]
* Fix tool not found in MCP memory issue (#3931)[https://github.com/opensearch-project/ml-commons/pull/3931]
* Fix: Ensure proper format for Bedrock deepseek tool result (#3933)[https://github.com/opensearch-project/ml-commons/pull/3933]

### Maintenance
* [Code Quality] Adding test cases for PlanExecuteReflect Agent (#3778)[https://github.com/opensearch-project/ml-commons/pull/3778]
* Add Unit Tests for MCP feature (#3787)[https://github.com/opensearch-project/ml-commons/pull/3787]
* exclude trusted connector check for hidden model (#3838)[https://github.com/opensearch-project/ml-commons/pull/3838]
* add more logging to deploy/undeploy flows for better debugging (#3825)[https://github.com/opensearch-project/ml-commons/pull/3825]
* remove libs folder (#3824)[https://github.com/opensearch-project/ml-commons/pull/3824]
* Downgrade MCP version to 0.9 (#3821)[https://github.com/opensearch-project/ml-commons/pull/3821]
* upgrade http client to version align with core (#3809)[https://github.com/opensearch-project/ml-commons/pull/3809]
* Use stream optional enum set from core in MLStatsInput (#3648)[https://github.com/opensearch-project/ml-commons/pull/3648]
* change SearchIndexTool arguments parsing logic (#3883)[https://github.com/opensearch-project/ml-commons/pull/3883]
* force runtime class path commons-beanutils:commons-beanutils:1.11.0 to avoid transitive dependency (#3935)[https://github.com/opensearch-project/ml-commons/pull/3935]

### Infrastructure
* change release note (#3811)[https://github.com/opensearch-project/ml-commons/pull/3811]
* Update the maven snapshot publish endpoint and credential (#3929)[https://github.com/opensearch-project/ml-commons/pull/3929]

### Documentation
* Replace the usage of elasticsearch with OpenSearch in README (#3876)[https://github.com/opensearch-project/ml-commons/pull/3876]
* added blueprint for Bedrock Claude v4 (#3871)[https://github.com/opensearch-project/ml-commons/pull/3871]
