## Version 2.15.0.0 Release Notes

Compatible with OpenSearch 2.15.0.0

### Features
* Add connector tool ([#2516](https://github.com/opensearch-project/ml-commons/pull/2516))
* guardrails model support ([#2491](https://github.com/opensearch-project/ml-commons/pull/2491))


### Enhancements
* hanlde the throttling error in the response header ([#2442](https://github.com/opensearch-project/ml-commons/pull/2442))
* Implementing retry for remote connector to mitigate throttling issue ([#2462](https://github.com/opensearch-project/ml-commons/pull/2462))
* ml inference ingest processor support for local models ([#2508](https://github.com/opensearch-project/ml-commons/pull/2508))
* add setting to allow private IP ([#2534](https://github.com/opensearch-project/ml-commons/pull/2534))
* add IMMEDIATE refresh policy ([#2541](https://github.com/opensearch-project/ml-commons/pull/2541))


### Bug Fixes
* fix memory CB bugs and upgrade UTs to compatible with core changes ([#2469](https://github.com/opensearch-project/ml-commons/pull/2469))
* fix error of ML inference processor in foreach processor ([#2474](https://github.com/opensearch-project/ml-commons/pull/2474))
* fix error message with unwrapping the root cause ([#2458](https://github.com/opensearch-project/ml-commons/pull/2458))
* adding immediate refresh to delete model group request ([#2514](https://github.com/opensearch-project/ml-commons/pull/2514))
* Fix model still deployed after calling undeploy API ([#2510](https://github.com/opensearch-project/ml-commons/pull/2510))
* Fix bedrock embedding generation issue ([#2495](https://github.com/opensearch-project/ml-commons/pull/2495))
* Fix init encryption master key ([#2554](https://github.com/opensearch-project/ml-commons/pull/2554))


### Documentation
* add a connector blueprint for Amazon Comprehend APIs ([#2470](https://github.com/opensearch-project/ml-commons/pull/2470))
* add titan embeeding v2 to blueprint ([#2480](https://github.com/opensearch-project/ml-commons/pull/2480))
* tutorial: generate embedding for arrays of object  ([#2477](https://github.com/opensearch-project/ml-commons/pull/2477))
* Small fix in blueprint docs ([#2501](https://github.com/opensearch-project/ml-commons/pull/2501))
* Titan Embedding Connector Blueprint content referenced by users of OpenSearch 2.11 version ([#2519](https://github.com/opensearch-project/ml-commons/pull/2519))


### Infrastructure
* add IT for flow agent with CatIndexTool ([#2425](https://github.com/opensearch-project/ml-commons/pull/2425))
* Remove strict version dependency to compile minimum compatible version ([#2486](https://github.com/opensearch-project/ml-commons/pull/2486))
* add IT flow agent with search index tool ([#2460](https://github.com/opensearch-project/ml-commons/pull/2460))
* fix flaky IT ([#2530](https://github.com/opensearch-project/ml-commons/pull/2530))
* disable jvm memory circuit breaker for IT ([#2540](https://github.com/opensearch-project/ml-commons/pull/2540))
* fix flaky test of PredictionITTests and RestConnectorToolIT ([#2437](https://github.com/opensearch-project/ml-commons/pull/2437))

### Maintenance
* Updating security reachout email ([#2445](https://github.com/opensearch-project/ml-commons/pull/2445))

