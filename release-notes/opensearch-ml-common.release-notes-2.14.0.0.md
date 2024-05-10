## Version 2.14.0.0 Release Notes

Compatible with OpenSearch 2.14.0.0

### Features
* Initiate MLInferencelngestProcessor ([#2205](https://github.com/opensearch-project/ml-commons/pull/2205))
* Add TTL to un-deploy model automatically ([#2365](https://github.com/opensearch-project/ml-commons/pull/2365))
* ML Model Interface ([#2357](https://github.com/opensearch-project/ml-commons/pull/2357))

### Enhancements
* Change httpclient to async ([#1958](https://github.com/opensearch-project/ml-commons/pull/1958))
* Fix user defined preprocess function missing prediction issue ([#2418](https://github.com/opensearch-project/ml-commons/pull/2418))
* Migrate RAG pipeline to async processing. ([#2345](https://github.com/opensearch-project/ml-commons/pull/2345))
* Filtering hidden model info from model profiling for users other than superadmin ([#2332](https://github.com/opensearch-project/ml-commons/pull/2332))
* check model auto deploy  ([#2288](https://github.com/opensearch-project/ml-commons/pull/2288))
* restrict stash context only for stop words system index ([#2283](https://github.com/opensearch-project/ml-commons/pull/2283))
* Add a flag to control auto-deploy behavior ([#2276](https://github.com/opensearch-project/ml-commons/pull/2276))


### Bug Fixes
* fix stopwords npe ([#2311](https://github.com/opensearch-project/ml-commons/pull/2311))
* guardrails npe ([#2304](https://github.com/opensearch-project/ml-commons/pull/2304))
* not sending failure message when model index isn't present ([#2351](https://github.com/opensearch-project/ml-commons/pull/2351))
* fix guardrails mapping ([#2279](https://github.com/opensearch-project/ml-commons/pull/2279))
* fix no model group index issue in connector helper notebook ([#2336](https://github.com/opensearch-project/ml-commons/pull/2336))
* Fixes #2317 predict api not working with asymmetric models ([#2318](https://github.com/opensearch-project/ml-commons/pull/2318))
* fixing isHidden null issue  ([#2337](https://github.com/opensearch-project/ml-commons/pull/2337))
* fix remote register model / circuit breaker 500([#2264](https://github.com/opensearch-project/ml-commons/pull/2264))
* guardrails bug fixes and IT for creating guardrails ([#2269](https://github.com/opensearch-project/ml-commons/pull/2269))
* Added missing result filter to inference ([#2367](https://github.com/opensearch-project/ml-commons/pull/2367))
* making Boolean type for isHidden ([#2341](https://github.com/opensearch-project/ml-commons/pull/2341）)
* Clear planningWorkerNodes when model auto-deploys again after undeploy ([#2396](https://github.com/opensearch-project/ml-commons/pull/2396))
* Avoid race condition in syncup model state refresh ([#2405](https://github.com/opensearch-project/ml-commons/pull/2405))
* Add a flag to distinguish remote model auto deploy and transport deploy ([#2410](https://github.com/opensearch-project/ml-commons/pull/2410))
* Add deploySetting in registering local models ([#2415](https://github.com/opensearch-project/ml-commons/pull/2415))

### Refactoring
* feat: Add search index tool ([#2356](https://github.com/opensearch-project/ml-commons/pull/2356))
* Move visualization tool to ml-commons ([#2363](https://github.com/opensearch-project/ml-commons/pull/2363))


### Documentation
* Add connector blueprint for VertexAI  Embedding endpoint  ([#2268](https://github.com/opensearch-project/ml-commons/pull/2268))


### Infrastructure
* remove checkstyle ([#2312](https://github.com/opensearch-project/ml-commons/pull/2312))
* Increase rounding delta from 0.005% to 0.5% on RestMLInferenceIngestProcessorIT ([#2372](https://github.com/opensearch-project/ml-commons/pull/2372))
* add agent framework security it tests by ([#2266](https://github.com/opensearch-project/ml-commons/pull/2266))
* Add IT for interface ([#2394](https://github.com/opensearch-project/ml-commons/pull/2394))
* Fix local build failure for RestMLInferenceIngestProcessorIT ([#2402](https://github.com/opensearch-project/ml-commons/pull/2402))

### Maintenance
* fix CVE for org.eclipse.core.runtime ([#2378](https://github.com/opensearch-project/ml-commons/pull/2378))

