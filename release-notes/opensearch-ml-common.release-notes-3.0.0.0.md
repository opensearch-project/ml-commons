## Version 3.0.0.0 Release Notes

Compatible with OpenSearch 3.0.0.0

### Breaking Changes
* Use _list/indices API instead of _cat/index API in CatIndexTool ([#3243](https://github.com/opensearch-project/ml-commons/pull/3243))
* Deprecate the restful API of batch ingestion ([#3688](https://github.com/opensearch-project/ml-commons/pull/3688))

### Features
* Onboard MCP ([#3721](https://github.com/opensearch-project/ml-commons/pull/3721))
* Plan, Execute and Reflect Agent Type ([#3716](https://github.com/opensearch-project/ml-commons/pull/3716))
* Support custom prompts from user ([#3731](https://github.com/opensearch-project/ml-commons/pull/3731))
* Support MCP server in OpenSearch ([#3781](https://github.com/opensearch-project/ml-commons/pull/3781))

### Enhancements
* Support sentence highlighting QA model ([#3600](https://github.com/opensearch-project/ml-commons/pull/3600))
* Add parser for ModelTensorOutput and ModelTensors ([#3658](https://github.com/opensearch-project/ml-commons/pull/3658))
* Function calling for openai v1, bedrock claude and deepseek ([#3712](https://github.com/opensearch-project/ml-commons/pull/3712))
* Update highlighting model translator to adapt new model ([#3699](https://github.com/opensearch-project/ml-commons/pull/3699))
* Implement async mode in agent execution ([#3714](https://github.com/opensearch-project/ml-commons/pull/3714))

### Bug Fixes
* Fix building error due to a breaking change from core ([#3617](https://github.com/opensearch-project/ml-commons/pull/3617))
* Fixing the circuit breaker issue for remote model ([#3652](https://github.com/opensearch-project/ml-commons/pull/3652))
* Fix compilation error ([#3667](https://github.com/opensearch-project/ml-commons/pull/3667))
* Revert CI workflow changes ([#3674](https://github.com/opensearch-project/ml-commons/pull/3674))
* Fix config index masterkey pull up for multi-tenancy ([#3700](vhttps://github.com/opensearch-project/ml-commons/pull/3700))
* Fix error message when input map and output map length not match ([#3730](https://github.com/opensearch-project/ml-commons/pull/3730))
* Agent Framework: Handle model response when toolUse is not accompanied by text ([#3755](https://github.com/opensearch-project/ml-commons/pull/3755))
* Allow user to control react agent max_interations value to prevent empty response ([#3756](https://github.com/opensearch-project/ml-commons/pull/3756))
* Agent framework: Fix SearchIndexTool to parse special floating point values and NaN ([#3754](https://github.com/opensearch-project/ml-commons/pull/3754))
* Directly return Response objects from metadata client responses ([#3768](https://github.com/opensearch-project/ml-commons/pull/3768))
* Remove opensearch-ml-2.4.0.0.zip file that was added by random mistake ([#3763](https://github.com/opensearch-project/ml-commons/pull/3763))
* Replace null GetResponse with valid response and not exists ([#3759](https://github.com/opensearch-project/ml-commons/pull/3759))
* Fix ListIndexTool and SearchIndexTool ([#3720](https://github.com/opensearch-project/ml-commons/pull/3720))
* Support MCP session management ([#3803](https://github.com/opensearch-project/ml-commons/pull/3803))
* Support customized message endpoint and addressing comments ([#3810](https://github.com/opensearch-project/ml-commons/pull/3810))
* Excluding circuit breaker for Agent ([#3814](https://github.com/opensearch-project/ml-commons/pull/3814))

### Maintenance
* Update CB setting to 100 to bypass memory check ([#3627](https://github.com/opensearch-project/ml-commons/pull/3627))
* Use model type to check local or remote model ([#3597](https://github.com/opensearch-project/ml-commons/pull/3597))
* Fixing security integ test ([#3646](https://github.com/opensearch-project/ml-commons/pull/3646))
* Remove forcing log4j version to 2.24.2 ([#3647](https://github.com/opensearch-project/ml-commons/pull/3647))
* Improve test coverage for MLHttpClientFactory.java ([#3644](https://github.com/opensearch-project/ml-commons/pull/3644))
* Improve test coverage for MLEngineClassLoader class ([#3679](https://github.com/opensearch-project/ml-commons/pull/3679))
* Typo: MLTaskDispatcher _cluster/settings api ([#3694](https://github.com/opensearch-project/ml-commons/pull/3694))
* Add more logs to troubleshoot flaky test ([#3543](https://github.com/opensearch-project/ml-commons/pull/3543))
* Add package for security test ([#3698](https://github.com/opensearch-project/ml-commons/pull/3698))
* Add sdk implementation to the connector search ([#3704](https://github.com/opensearch-project/ml-commons/pull/3704))
* Sdk client implementation for search connector, model group and task ([#3707](https://github.com/opensearch-project/ml-commons/pull/3707))
* Add Feature Flag for MCP connectors Feature ([(#3738](https://github.com/opensearch-project/ml-commons/pull/3738))
* Support phasing off SecurityManager usage in favor of Java Agent ([#3729](https://github.com/opensearch-project/ml-commons/pull/3729))
* Add java-agent gradle plugin ([#3727](https://github.com/opensearch-project/ml-commons/pull/3727))

### Documentation
* Add tutorial for RAG of openai and bedrock ([#2975](https://github.com/opensearch-project/ml-commons/pull/2975))
* Fix template query link ([#3612](https://github.com/opensearch-project/ml-commons/pull/3612))
* Add standard blueprint for vector search ([#3659](https://github.com/opensearch-project/ml-commons/pull/3659))
* Add blueprint for Claude 3.7 on Bedrock ([#3584](https://github.com/opensearch-project/ml-commons/pull/3584))
* Add standard blueprint for azure embedding ada2 ([#3725](https://github.com/opensearch-project/ml-commons/pull/3725))
