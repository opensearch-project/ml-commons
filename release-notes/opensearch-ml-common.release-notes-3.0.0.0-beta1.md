## Version 3.0.0.0-beta1 Release Notes

Compatible with OpenSearch 3.0.0.0-beta1

### Breaking Changes
* Deprecate the restful API of batch ingestion (#3688)[https://github.com/opensearch-project/ml-commons/pull/3688]

### Enhancements
* Add parser for ModelTensorOutput and ModelTensors (#3658)[https://github.com/opensearch-project/ml-commons/pull/3658]
* Function calling for openai v1, bedrock claude and deepseek (#3712)[https://github.com/opensearch-project/ml-commons/pull/3712]
* Update highlighting model translator to adapt new model (#3699)[https://github.com/opensearch-project/ml-commons/pull/3699]
* Plan, Execute and Reflect Agent Type (#3716)[https://github.com/opensearch-project/ml-commons/pull/3716]
* Implement async mode in agent execution (#3714)[https://github.com/opensearch-project/ml-commons/pull/3714]

### Bug Fixes
* Fixing the circuit breaker issue for remote model (#3652)[https://github.com/opensearch-project/ml-commons/pull/3652]
* Fix compilation error (#3667)[https://github.com/opensearch-project/ml-commons/pull/3667]
* Revert CI workflow changes (#3674)[https://github.com/opensearch-project/ml-commons/pull/3674]
* Fix config index masterkey pull up for multi-tenancy (#3700)[https://github.com/opensearch-project/ml-commons/pull/3700]

### Maintenance
* Remove forcing log4j version to 2.24.2 (#3647)[https://github.com/opensearch-project/ml-commons/pull/3647]
* Improve test coverage for MLHttpClientFactory.java (#3644)[https://github.com/opensearch-project/ml-commons/pull/3644]
* Improve test coverage for MLEngineClassLoader class (#3679)[https://github.com/opensearch-project/ml-commons/pull/3679]
* Typo: MLTaskDispatcher _cluster/settings api (#3694)[https://github.com/opensearch-project/ml-commons/pull/3694]
* Add more logs to toubleshot flaky test (#3543)[https://github.com/opensearch-project/ml-commons/pull/3543]
* Add package for security test (#3698)[https://github.com/opensearch-project/ml-commons/pull/3698]
* Add sdk implementation to the connector search (#3704)[https://github.com/opensearch-project/ml-commons/pull/3704]
* Sdk client implementation for search connector, model group and task (#3707)[https://github.com/opensearch-project/ml-commons/pull/3707]

### Documentation
* Add standard blueprint for vector search (#3659)[https://github.com/opensearch-project/ml-commons/pull/3659]
* Add blueprint for Claude 3.7 on Bedrock (#3584)[https://github.com/opensearch-project/ml-commons/pull/3584]


