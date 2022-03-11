## Version 1.3.0.0 Release Notes

Compatible with OpenSearch 1.3.0

### Features

* add anomaly localization implementation ([#103](https://github.com/opensearch-project/ml-commons/pull/103))
* refactor ML task data model; add create ML task index method ([#116](https://github.com/opensearch-project/ml-commons/pull/116))
* integration step 1 and 2 for anomaly localization ([#113](https://github.com/opensearch-project/ml-commons/pull/113))
* anomaly localization integration step 3 ([#114](https://github.com/opensearch-project/ml-commons/pull/114))
* support train ML model in either sync or async way ([#124](https://github.com/opensearch-project/ml-commons/pull/124))
* anomaly localization integration step 4 and 5 ([#125](https://github.com/opensearch-project/ml-commons/pull/125))
* add train and predict API ([#126](https://github.com/opensearch-project/ml-commons/pull/126))
* add ML Model get API ([#117](https://github.com/opensearch-project/ml-commons/pull/117))
* integrate tribuo anomaly detection based on libSVM ([#96](https://github.com/opensearch-project/ml-commons/pull/96))
* add ML Delete model API ([#136](https://github.com/opensearch-project/ml-commons/pull/136))
* add fixed in time rcf ([#138](https://github.com/opensearch-project/ml-commons/pull/138))
* Add ML Model Search API ([#140](https://github.com/opensearch-project/ml-commons/pull/140))
* add circuit breaker ([#142](https://github.com/opensearch-project/ml-commons/pull/142))
* add batch RCF for non-time-series data ([#145](https://github.com/opensearch-project/ml-commons/pull/145))
* Add ML Task GET/Delete API ([#146](https://github.com/opensearch-project/ml-commons/pull/146))
* Add Search Task API and Refactor search actions and handlers ([#149](https://github.com/opensearch-project/ml-commons/pull/149))
* add minimum top contributor candidate queue size ([#151](https://github.com/opensearch-project/ml-commons/pull/151))
* add more stats: request/failure/model count on algo/action level ([#159](https://github.com/opensearch-project/ml-commons/pull/159))
* add tasks API in Client ([#200](https://github.com/opensearch-project/ml-commons/pull/200))

### Enhancements

* support float type in data frame ([#129](https://github.com/opensearch-project/ml-commons/pull/129))
* support short and long type in data frame ([#131](https://github.com/opensearch-project/ml-commons/pull/131))
* use threadpool in execute task runner ([#156](https://github.com/opensearch-project/ml-commons/pull/156))
* do not return model and task id in response ([#171](https://github.com/opensearch-project/ml-commons/pull/171))
* more strict check on input parameters by applying non-coerce mode ([#173](https://github.com/opensearch-project/ml-commons/pull/173))
* move anomaly localization to the last position to avoid BWC issue ([#189](https://github.com/opensearch-project/ml-commons/pull/189))

### Bug Fixes

* use latest version of tribuo to fix modify thread group permission issue ([#112](https://github.com/opensearch-project/ml-commons/pull/112))
* fix jarhell error from SQL build ([#137](https://github.com/opensearch-project/ml-commons/pull/137))
* fix EpochMilli parse error in MLTask ([#147](https://github.com/opensearch-project/ml-commons/pull/147))
* fix permission when accessing ML system indices ([#148](https://github.com/opensearch-project/ml-commons/pull/148))
* fix system index permission issue in train/predict runner ([#150](https://github.com/opensearch-project/ml-commons/pull/150))
* cleanup task cache once task done ([#152](https://github.com/opensearch-project/ml-commons/pull/152))
* fix update task semaphore; don't return task id for sync request ([#153](https://github.com/opensearch-project/ml-commons/pull/153))
* restore context after accessing system index to check user permission on non-system index ([#154](https://github.com/opensearch-project/ml-commons/pull/154))
* fix verbose error message thrown by invalid enum ([#167](https://github.com/opensearch-project/ml-commons/pull/167))
* fix no permission to create model/task index bug;add security IT for train/predict API ([#177](https://github.com/opensearch-project/ml-commons/pull/177))

### Infrastructure

* add git ignore file ([#92](https://github.com/opensearch-project/ml-commons/pull/92))
* change common utils to 1.2 snapshot;add more test ([#94](https://github.com/opensearch-project/ml-commons/pull/94))
* Remove jcenter dependency ([#121](https://github.com/opensearch-project/ml-commons/pull/121))
* add integration test for train and predict API ([#157](https://github.com/opensearch-project/ml-commons/pull/157))
* fix build/CI and add backport workflow ([#161](https://github.com/opensearch-project/ml-commons/pull/161))
* publish ml client to maven ([#165](https://github.com/opensearch-project/ml-commons/pull/165))
* Add integ tests for model APIs ([#166](https://github.com/opensearch-project/ml-commons/pull/166))
* add security IT ([#168](https://github.com/opensearch-project/ml-commons/pull/168))
* fix maven group ([#170](https://github.com/opensearch-project/ml-commons/pull/170))
* add more UT for ml-algorithms ([#182](https://github.com/opensearch-project/ml-commons/pull/182))
* add java 8 to CI workflow ([#194](https://github.com/opensearch-project/ml-commons/pull/194))
* add more UT and IT for rest actions ([#192](https://github.com/opensearch-project/ml-commons/pull/192))
* add more UT to client module ([#203](https://github.com/opensearch-project/ml-commons/pull/203))
* add more UT for task manager/runner ([#206](https://github.com/opensearch-project/ml-commons/pull/206))
* create config and workflow files for release note ([#209](https://github.com/opensearch-project/ml-commons/pull/209))
* use 1.3.0 docker to run CI ([#212](https://github.com/opensearch-project/ml-commons/pull/212))

### Documentation

* Add support for codeowners to repo ([#91](https://github.com/opensearch-project/ml-commons/pull/91))
* add how to develop new function doc to readme ([#95](https://github.com/opensearch-project/ml-commons/pull/95))
* update license header ([#134](https://github.com/opensearch-project/ml-commons/pull/134))

### Maintenance

* Bump version to 1.2 ([#90](https://github.com/opensearch-project/ml-commons/pull/90))
* bump to 1.2.3 ([#110](https://github.com/opensearch-project/ml-commons/pull/110))
* bump to 1.3.0 ([#115](https://github.com/opensearch-project/ml-commons/pull/115))

### Refactoring

* Merge develop branch into main branch ([#87](https://github.com/opensearch-project/ml-commons/pull/87))
* refactor API input/output/URL; add execute API for non-model based algorithm ([#93](https://github.com/opensearch-project/ml-commons/pull/93))
* cleanup code and refactor ([#106](https://github.com/opensearch-project/ml-commons/pull/106))
* support registering ML objects; refactor ML engine interface ([#108](https://github.com/opensearch-project/ml-commons/pull/108))
* refactor persisting ML model ([#109](https://github.com/opensearch-project/ml-commons/pull/109))
* refactor transport APIs;fix class cast exception ([#127](https://github.com/opensearch-project/ml-commons/pull/127))
* add ML custom exceptions ([#133](https://github.com/opensearch-project/ml-commons/pull/133))
* rename tribuo AD algorithm name ([#144](https://github.com/opensearch-project/ml-commons/pull/144))

