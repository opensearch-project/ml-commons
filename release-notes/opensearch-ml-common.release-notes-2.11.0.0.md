## Version 2.11.0.0 Release Notes

Compatible with OpenSearch 2.11.0


### Experimental Features
* Update Connector API ([#1227](https://github.com/opensearch-project/ml-commons/pull/1227))

### Enhancements
* Add neural search default processor for non OpenAI/Cohere scenario ([#1274](https://github.com/opensearch-project/ml-commons/pull/1274))
* Add tokenizer and sparse encoding ([#1301](https://github.com/opensearch-project/ml-commons/pull/1301))
* allow input null for text docs input ([#1402](https://github.com/opensearch-project/ml-commons/pull/1402))
* Add support for context_size and include 'interaction_id' in SearchRequest ([#1385](https://github.com/opensearch-project/ml-commons/pull/1385))
* adding model level metric in node level ([#1330](https://github.com/opensearch-project/ml-commons/pull/1330))
* add status code to model tensor ([#1443](https://github.com/opensearch-project/ml-commons/pull/1443))
* add bedrockURL to trusted connector regex list ([#1461](https://github.com/opensearch-project/ml-commons/pull/1461))
* Performance enhacement for predict action by caching model info ([#1472](https://github.com/opensearch-project/ml-commons/pull/1472))


### Infrastructure


### Bug Fixes
* fix parameter name in preprocess function ([#1362](https://github.com/opensearch-project/ml-commons/pull/1362))
* fix spelling in Readme.md ([#1363](https://github.com/opensearch-project/ml-commons/pull/1363))
* Fix error message in TransportDeplpoyModelAction class ([#1368](https://github.com/opensearch-project/ml-commons/pull/1368))
* fix null exception in text docs data set ([#1403](https://github.com/opensearch-project/ml-commons/pull/1403))
* fix text docs input unescaped error; enable deploy remote model ([#1407](https://github.com/opensearch-project/ml-commons/pull/1407))
* restore thread context before running action listener ([#1418](https://github.com/opensearch-project/ml-commons/pull/1418))
* fix more places where thread context not restored ([#1421](https://github.com/opensearch-project/ml-commons/pull/1421))
* Fix BWC test suite ([#1426](https://github.com/opensearch-project/ml-commons/pull/1426))
* support bwc for process function ([#1427](https://github.com/opensearch-project/ml-commons/pull/1427))
* fix model group auto-deletion when last version is deleted ([#1444](https://github.com/opensearch-project/ml-commons/pull/1444))
* fixing metrics correlation algorithm ([#1448](https://github.com/opensearch-project/ml-commons/pull/1448))
* throw exception if remote model doesn't return 2xx status code; fix predict runner ([#1477](https://github.com/opensearch-project/ml-commons/pull/1477))
* fix no worker node exception for remote embedding model ([#1482](https://github.com/opensearch-project/ml-commons/pull/1482))
* fix for delete model group API throwing incorrect error when model index not created ([#1485](https://github.com/opensearch-project/ml-commons/pull/1485))
* fix no worker node error on multi-node cluster ([#1487](https://github.com/opensearch-project/ml-commons/pull/1487))
* Fix prompt passing for Bedrock by passing a single string prompt for Bedrock models. ([#1490](https://github.com/opensearch-project/ml-commons/pull/1490))


### Documentation


### Maintenance

* Ignoring Redeploy test on MacOS due to known failures ([#1414](https://github.com/opensearch-project/ml-commons/pull/1414))
* throw exception when model group not found during update request ([#1447](https://github.com/opensearch-project/ml-commons/pull/1447))
* Add a setting to control the update connector API ([#1274](https://github.com/opensearch-project/ml-commons/pull/1274))


### Refactoring

* register new versions to a model group based on the name provided ([#1452](https://github.com/opensearch-project/ml-commons/pull/1452))
* if model version fails to register, update model group accordingly ([#1463](https://github.com/opensearch-project/ml-commons/pull/1463))



