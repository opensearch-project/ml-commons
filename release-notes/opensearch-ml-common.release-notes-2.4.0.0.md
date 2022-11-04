## Version 2.4.0.0 Release Notes

Compatible with OpenSearch 2.4.0


### Features

* add profile APIs for model and task ([#446](https://github.com/opensearch-project/ml-commons/pull/446))
* tune ML model to support custom model ([#471](https://github.com/opensearch-project/ml-commons/pull/471))
* add input/output for custom model ([#473](https://github.com/opensearch-project/ml-commons/pull/473))
* refactor ML algorithm package for supporting custom model ([#474](https://github.com/opensearch-project/ml-commons/pull/474))
* add text embedding model ([#477](https://github.com/opensearch-project/ml-commons/pull/477))
* add custom model transport action, request/response to common package ([#479](https://github.com/opensearch-project/ml-commons/pull/479))
* add custom model public APIs: upload/load/unload ([#488](https://github.com/opensearch-project/ml-commons/pull/488))
* tune interface to support predicting loaded built-in algorithms; remove stale task in cache ([#491](https://github.com/opensearch-project/ml-commons/pull/491))
* Support the generic ml ppl command. ([#484](https://github.com/opensearch-project/ml-commons/pull/484))
* refactor model cache and thread pool ([#496](https://github.com/opensearch-project/ml-commons/pull/496))
* add custom model size limit ([#503](https://github.com/opensearch-project/ml-commons/pull/503))
* tune thread pool size; add more unit test ([#506](https://github.com/opensearch-project/ml-commons/pull/503))
* changes to custom model chunk upload api ([#495](https://github.com/opensearch-project/ml-commons/pull/495))
* support multi-gpu; fix inference duration queue bug ([#515](https://github.com/opensearch-project/ml-commons/pull/515))
* add trusted URL regex ([#518](https://github.com/opensearch-project/ml-commons/pull/518))
* add description field when upload model; tune log level ([#531](https://github.com/opensearch-project/ml-commons/pull/531))

### Enhancements

* do not return model content by default ([#458](https://github.com/opensearch-project/ml-commons/pull/458))
* update delete model TransportAction to support custom model ([#497](https://github.com/opensearch-project/ml-commons/pull/497))
* add disk circuit breaker, update deleteModel message format ([#498](https://github.com/opensearch-project/ml-commons/pull/498)))
* return circuit breaker name in error messages ([#507](https://github.com/opensearch-project/ml-commons/pull/507))
* change the max_ml_task_per_node into dynamic settings ([#530](https://github.com/opensearch-project/ml-commons/pull/530))

### Bug Fixes

* Bug fix: filter _source field in search model api ([#445](https://github.com/opensearch-project/ml-commons/pull/445))
* fix profile bug ([#463](https://github.com/opensearch-project/ml-commons/pull/463))

### Infrastructure

* add groupId to pluginzip publication ([#468](https://github.com/opensearch-project/ml-commons/pull/468))
* Add UT for TransportLoadModelAction ([#490](https://github.com/opensearch-project/ml-commons/pull/490))
* add integ tests for new APIs: upload/load/unload ([#500](https://github.com/opensearch-project/ml-commons/pull/500))
* test windows build ([#504](https://github.com/opensearch-project/ml-commons/pull/504))
* update new small torchscript model for integ test ([#508](https://github.com/opensearch-project/ml-commons/pull/508))
* add test coverage to transportUploadModelAction ([#511](https://github.com/opensearch-project/ml-commons/pull/511))
* add more test coverage to ModelHelper and FileUtils ([#510](https://github.com/opensearch-project/ml-commons/pull/510))
* use small model to run integ test ([#509](https://github.com/opensearch-project/ml-commons/pull/509))
* Add more unit test coverage to output.model and input.parameter in co… ([#517](https://github.com/opensearch-project/ml-commons/pull/517))
* Add test coverage to common package ([#514](https://github.com/opensearch-project/ml-commons/pull/514))
* add unit tests to improve test coverage in plugin package ([#516](https://github.com/opensearch-project/ml-commons/pull/516))
* Adds security IT for new upload and load APIs ([#529](https://github.com/opensearch-project/ml-commons/pull/529))
* test coverage ratio changes for build.gradlew in plugin package ([#536](https://github.com/opensearch-project/ml-commons/pull/536))

### Documentation

* fix readme: remove experimental words ([#431](https://github.com/opensearch-project/ml-commons/pull/431))

### Maintenance

* Increment version to 2.4.0-SNAPSHOT ([#422](https://github.com/opensearch-project/ml-commons/pull/422))
* Address CVE-2022-42889 by updating commons-text ([#487](https://github.com/opensearch-project/ml-commons/pull/487))


