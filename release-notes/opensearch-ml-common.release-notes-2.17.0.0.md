## Version 2.17.0.0 Release Notes

Compatible with OpenSearch 2.17.0

### Features
* Offline batch ingestion API actions and data ingesters (#2844)[https://github.com/opensearch-project/ml-commons/pull/2844]
* Support get batch transform job status in get task API (#2825)[https://github.com/opensearch-project/ml-commons/pull/2825]

### Enhancements
* Adding additional info for memory metadata (#2750)[https://github.com/opensearch-project/ml-commons/pull/2750]
* Support list in response body (#2811)[https://github.com/opensearch-project/ml-commons/pull/2811]
* Support skip_validating_missing_parameters in connector (#2830)[https://github.com/opensearch-project/ml-commons/pull/2830]
* Support one_to_one in ML Inference Search Response Processor (#2801)[https://github.com/opensearch-project/ml-commons/pull/2801]
* Expose ML Config API (#2850)[https://github.com/opensearch-project/ml-commons/pull/2850]

### Bug Fixes
* Fix delete local model twice quickly get 500 response issue (#2806)[https://github.com/opensearch-project/ml-commons/pull/2806]
* Fix cohere model input interface cannot validate cohere input issue (#2847)[https://github.com/opensearch-project/ml-commons/pull/2847]
* Add processed function for remote inference input dataset parameters to convert it back to its original datatype (#2852)[https://github.com/opensearch-project/ml-commons/pull/2852]
* Use local_regex as default type for guardrails (#2853)[https://github.com/opensearch-project/ml-commons/pull/2853]
* Agent execution error in json format (#2858)[https://github.com/opensearch-project/ml-commons/pull/2858]
* Fix custom prompt substitute with List issue in ml inference search response processor (#2871)[https://github.com/opensearch-project/ml-commons/pull/2871]
* Fix breaking changes in config index fields (#2882)[https://github.com/opensearch-project/ml-commons/pull/2882]
* Output only old fields in get config API (#2892)[https://github.com/opensearch-project/ml-commons/pull/2892]
* Fix http dependency in CancelBatchJobTransportAction (#2898)[https://github.com/opensearch-project/ml-commons/pull/2898]

### Maintenance
* Applying spotless to common module (#2815)[https://github.com/opensearch-project/ml-commons/pull/2815]
* Fix Cohere test (#2831)[https://github.com/opensearch-project/ml-commons/pull/2831]

### Infrastructure
* Test: recover search index tool it in multi node cluster (#2407)[https://github.com/opensearch-project/ml-commons/pull/2407]

### Documentation
* Add tutorial for Bedrock Guardrails (#2695)[https://github.com/opensearch-project/ml-commons/pull/2695]

### Refactoring
* Code refactor not to occur nullpointer exception (#2816)[https://github.com/opensearch-project/ml-commons/pull/2816]