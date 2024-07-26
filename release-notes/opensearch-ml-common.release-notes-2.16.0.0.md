## Version 2.16.0.0 Release Notes

Compatible with OpenSearch 2.16.0

### Features
* Add initial MLInferenceSearchResponseProcessor (#2688)[https://github.com/opensearch-project/ml-commons/pull/2688]
* Add initial search request inference processor (#2731)[https://github.com/opensearch-project/ml-commons/pull/2731]
* Add Batch Prediction Mode in the Connector Framework for batch inference (#2661)[https://github.com/opensearch-project/ml-commons/pull/2661]

### Enhancements
* Automated model interface generation on aws llms (#2689)[https://github.com/opensearch-project/ml-commons/pull/2689]
* Increase execute thread pool size (#2691)[https://github.com/opensearch-project/ml-commons/pull/2691]
* Add multi modal default preprocess function (#2500)[https://github.com/opensearch-project/ml-commons/pull/2500]
* Add model input validation for local models in ml processor (#2610)[https://github.com/opensearch-project/ml-commons/pull/2610]
* Removing experimental from the Conversation memory feature(#2592)[https://github.com/opensearch-project/ml-commons/pull/2592]
* Pass all parameters including chat_history to run tools (#2714)[https://github.com/opensearch-project/ml-commons/pull/2714]
* Feat: add bedrock runtime agent for knowledge base (#2651)[https://github.com/opensearch-project/ml-commons/pull/2651]
* change disk circuit breaker to cluster settings (#2634)[https://github.com/opensearch-project/ml-commons/pull/2634]

### Bug Fixes
* Add stashcontext to connector getter (#2742)[https://github.com/opensearch-project/ml-commons/pull/2742]
* Excluding remote models from max node per node setting (#2732)[https://github.com/opensearch-project/ml-commons/pull/2732]
* Add logging for throttling and guardrail in connector (#2725)[https://github.com/opensearch-project/ml-commons/pull/2725]
* Add acknowledge check for index creation in missing places (#2715)[https://github.com/opensearch-project/ml-commons/pull/2715]
* Update config index mappings to use correct field types (#2710)[https://github.com/opensearch-project/ml-commons/pull/2710]
* Fix yaml test issue (#2700)[https://github.com/opensearch-project/ml-commons/pull/2700]
* Fix MLModelTool returns null if the response of LLM is a pure json object (#2675)[https://github.com/opensearch-project/ml-commons/pull/2675]
* Bump ml config index schema version (#2656)[https://github.com/opensearch-project/ml-commons/pull/2656]
* Wrap CreateIndexRequest mappings in _doc key in ml-commons (#2759)[https://github.com/opensearch-project/ml-commons/pull/2759]

### Maintenance
* Upgrade djl version to 0.28.0 (#2578)[https://github.com/opensearch-project/ml-commons/pull/2578]
* Register system index descriptors through SystemIndexPlugin.getSystemIndexDescriptors (#2586)[https://github.com/opensearch-project/ml-commons/pull/2586]

### Infrastructure
* Enable tests with mockStatic in MLEngineTest (#2582)[https://github.com/opensearch-project/ml-commons/pull/2582]
* Fix GA workflow that publishes Apache Maven artifacts (#2625)[https://github.com/opensearch-project/ml-commons/pull/2625]
* Temp use of older nodejs version before moving to Almalinux8 (#2628)[https://github.com/opensearch-project/ml-commons/pull/2628]

### Documentation
* Add amazon textract blueprint (#2562)[https://github.com/opensearch-project/ml-commons/pull/2562]
* Make all Bedrock model blueprints in a tidier format (#2642)[https://github.com/opensearch-project/ml-commons/pull/2642]
* Fix remote inference blueprints (#2692)[https://github.com/opensearch-project/ml-commons/pull/2692]
* Add connector blueprint for cohere embedding models in bedrock (#2667)[https://github.com/opensearch-project/ml-commons/pull/2667]
* Update tutorials for caching secrets for non-aws models (#2637)[https://github.com/opensearch-project/ml-commons/pull/2637]
* Add tutuorial for cross-encoder model on sagemaker (#2607)[https://github.com/opensearch-project/ml-commons/pull/2607]

### Refactoring
* Change multimodal connector name to bedrock multimodal connector (#2672)[https://github.com/opensearch-project/ml-commons/pull/2672]