## Version 2.14.0.0 Release Notes

Compatible with OpenSearch 2.14.0.0

### Features
* Initiate MLInferencelngestProcessor ([#2205](https://github.com/opensearch-project/ml-commons/pull/2205))
* Add TTL to un-deploy model automatically ([#2365](https://github.com/opensearch-project/ml-commons/pull/2365))
* ML Model Interface ([#2357](https://github.com/opensearch-project/ml-commons/pull/2357))
* Add connector blueprint for VertexAI  Embedding endpoint  ([#2268](https://github.com/opensearch-project/ml-commons/pull/2268))
* feat: Add search index tool ([#2356](https://github.com/opensearch-project/ml-commons/pull/2356))
* Move visualization tool to ml-commons ([#2363](https://github.com/opensearch-project/ml-commons/pull/2363))

### Enhancements
* guardrails npe ([#2304](https://github.com/opensearch-project/ml-commons/pull/2304))
* set only_run_on_ml_node default value to false ([#2294](https://github.com/opensearch-project/ml-commons/pull/2294))
* agent out of the setting only_run_on_ml_node ([#2299](https://github.com/opensearch-project/ml-commons/pull/2299))
* not sending failure message when model index isn't present ([#2351](https://github.com/opensearch-project/ml-commons/pull/2351))
* Change httpclient to async ([#1958](https://github.com/opensearch-project/ml-commons/pull/1958))
* fix stopwords npe ([#2311](https://github.com/opensearch-project/ml-commons/pull/2311))
* Fix connector can use subfield of ARN ([#2320](https://github.com/opensearch-project/ml-commons/pull/2320))
* Revert adding subfield filter in MLCreateConnectorInput ([#2323](https://github.com/opensearch-project/ml-commons/pull/2323))
* Increase rounding delta from 0.005% to 0.5% on RestMLInferenceIngestProcessorIT ([#2372](https://github.com/opensearch-project/ml-commons/pull/2372))
* Added missing result filter to inference ([#2367](https://github.com/opensearch-project/ml-commons/pull/2367))
* Migrate RAG pipeline to async processing. ([#2345](https://github.com/opensearch-project/ml-commons/pull/2345))
* making Boolean type for isHidden ([#2341](https://github.com/opensearch-project/ml-commons/pull/2341）)
* Filtering hidden model info from model profiling for users other than superadmin ([#2332](https://github.com/opensearch-project/ml-commons/pull/2332))
* check model auto deploy  ([#2288](https://github.com/opensearch-project/ml-commons/pull/2288))
* add agent framework security it tests by (https://github.com/opensearch-project/ml-commons/pull/2266)
* fix guardrails mapping (https://github.com/opensearch-project/ml-commons/pull/2279)
* restrict stash context only for stop words system index (https://github.com/opensearch-project/ml-commons/pull/2283)
* Add a flag to control auto-deploy behavior (https://github.com/opensearch-project/ml-commons/pull/2276)


### Bug Fixes

* fix no model group index issue in connector helper notebook ([#2336](https://github.com/opensearch-project/ml-commons/pull/2336))
* Fixes #2317 predict api not working with asymmetric models ([#2318](https://github.com/opensearch-project/ml-commons/pull/2318))
* fixing isHidden null issue  ([#2337](https://github.com/opensearch-project/ml-commons/pull/2337))
* fix remote register model / circuit breaker 500(https://github.com/opensearch-project/ml-commons/pull/2264)
* guardrails bug fixes and IT for creating guardrails (https://github.com/opensearch-project/ml-commons/pull/2269)

### Infrastructure

* remove checkstyle ([#2312](https://github.com/opensearch-project/ml-commons/pull/2312))
* fix CVE for org.eclipse.core.runtime ([#2378](https://github.com/opensearch-project/ml-commons/pull/2378))