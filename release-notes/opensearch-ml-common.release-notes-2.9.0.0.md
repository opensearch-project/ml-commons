## Version 2.9.0.0 Release Notes

Compatible with OpenSearch 2.9.0


### Features

* remote inference: add connector; fine tune ML model and tensor class ([#1051](https://github.com/opensearch-project/ml-commons/pull/1051))
* remote inference: add connector executor ([#1052](https://github.com/opensearch-project/ml-commons/pull/1052))
* connector transport actions, requests and responses ([#1053](https://github.com/opensearch-project/ml-commons/pull/1053))
* refactor predictable: add method to check if model is ready ([#1057](https://github.com/opensearch-project/ml-commons/pull/1057))
* Add basic connector access control classes ([#1055](https://github.com/opensearch-project/ml-commons/pull/1055))
* connector transport actions and disable native memory CB [#1056](https://github.com/opensearch-project/ml-commons/pull/1056))
* Change connector access control creation allow empty list ([#1069](https://github.com/opensearch-project/ml-commons/pull/1069))

### Enhancements

* create model group automatically with first model version ([#1063](https://github.com/opensearch-project/ml-commons/pull/1063))
* init master key automatically ([#1075](https://github.com/opensearch-project/ml-commons/pull/1075)))

### Infrastructure

* Adding an integration test for redeploying a model ([#1016](https://github.com/opensearch-project/ml-commons/pull/1016))
* add unit test for connector class in commons ([#1058](https://github.com/opensearch-project/ml-commons/pull/1058))
* remote inference: add unit test for model and register model input ([#1059](https://github.com/opensearch-project/ml-commons/pull/1059))
* remote inference: add unit test for StringUtils and remote inference input ([#1061](https://github.com/opensearch-project/ml-commons/pull/1061))
* restful connector actions and UT ([#1065](https://github.com/opensearch-project/ml-commons/pull/1065))
* more UT for rest and trasport actions ([#1066](https://github.com/opensearch-project/ml-commons/pull/1066))
* remote inference: add unit test for create connector request/response ([#1067](https://github.com/opensearch-project/ml-commons/pull/1067))
* Add more UT for remote inference classes ([#1077](https://github.com/opensearch-project/ml-commons/pull/1077))
* IT Security Tests for model access control ([#1095](https://github.com/opensearch-project/ml-commons/pull/1095))

### Bug Fixes

* Add missing codes from pen test fix ([#1060](https://github.com/opensearch-project/ml-commons/pull/1060))
* fix cannot specify model access control parameters error ([#1068](https://github.com/opensearch-project/ml-commons/pull/1068))
* fix memory circuit breaker ([#1072](https://github.com/opensearch-project/ml-commons/pull/1072))
* PenTest fixes: error codes and update model group fix ([#1074](https://github.com/opensearch-project/ml-commons/pull/1074))
* Fix rare private ip address bypass SSRF issue ([#1070](https://github.com/opensearch-project/ml-commons/pull/1070))
* leftover in the 404 Not Found return error ([#1079](https://github.com/opensearch-project/ml-commons/pull/1079))
* modify error message when model group not unique is provided ([#1078](https://github.com/opensearch-project/ml-commons/pull/1078))
* stash context before accessing ml config index ([#1092](https://github.com/opensearch-project/ml-commons/pull/1092))
* fix init master key bug ([#1094](https://github.com/opensearch-project/ml-commons/pull/1094))

### Documentation

* model access control documentation ([#966](https://github.com/opensearch-project/ml-commons/pull/966))
* updating docs for model group id ([#980](https://github.com/opensearch-project/ml-commons/pull/980))

### Maintenance

* Increment version to 2.9.0-SNAPSHOT ([#955](https://github.com/opensearch-project/ml-commons/pull/955))
* Manual CVE backport ([#1008](https://github.com/opensearch-project/ml-commons/pull/1008))
* Fix build. ([#1018](https://github.com/opensearch-project/ml-commons/pull/1018))
* Fix the refactor change brought by core backport ([#1047](https://github.com/opensearch-project/ml-commons/pull/1047))
* change to compileOnly to avoid jarhell ([#1062](https://github.com/opensearch-project/ml-commons/pull/1062))

