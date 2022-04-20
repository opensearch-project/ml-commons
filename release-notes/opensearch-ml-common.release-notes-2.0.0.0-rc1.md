## Version 2.0.0.0-rc1 Release Notes

Compatible with OpenSearch 2.0.0-rc1

### Enhancements

* Add circuit breaker trigger count stat.([#274](https://github.com/opensearch-project/ml-commons/pull/274))

### Bug Fixes

* support dispatching execute task; don't dispatch ML task again ([#279](https://github.com/opensearch-project/ml-commons/pull/279))
* Fix NPE in anomaly localization ([#280](https://github.com/opensearch-project/ml-commons/pull/280))
* create model/task index with correct mapping ([#284](https://github.com/opensearch-project/ml-commons/pull/284))

### Infrastructure

* drop support for JDK 14 ([#267](https://github.com/opensearch-project/ml-commons/pull/267))
* Add UT/IT Coverage for action/models and action/tasks. ([#268](https://github.com/opensearch-project/ml-commons/pull/268))
* Default qualifier to alpha1 and fix workflows ([#269](https://github.com/opensearch-project/ml-commons/pull/269))
* Remove additional vars in build.gradle that are not used ([#271](https://github.com/opensearch-project/ml-commons/pull/271))
* Add UT for Search transport action. ([#272](https://github.com/opensearch-project/ml-commons/pull/272))
* updated issue templates for bugs and features. ([#273](https://github.com/opensearch-project/ml-commons/pull/273))
* Add more test to improve coverage of abstract search action([#275](https://github.com/opensearch-project/ml-commons/pull/275))
* Add UT for RestMLExecuteAction, and remove it out from the jacoco exclusive list. ([#278](https://github.com/opensearch-project/ml-commons/pull/278))
* add coverage badges ([#281](https://github.com/opensearch-project/ml-commons/pull/281))
* Re-enable docker image tests for 2.0. ([#288](https://github.com/opensearch-project/ml-commons/pull/288))

### Maintenance

* Change 2.0-alpha1 to 2.0-rc1. ([#282](https://github.com/opensearch-project/ml-commons/pull/282))
* bump RCF version to 3.0-rc2.1 ([#289](https://github.com/opensearch-project/ml-commons/pull/289))

### Refactoring

* Removed RCF jars and updated to fetch RCF 3.0-rc2 from maven ([#277](https://github.com/opensearch-project/ml-commons/pull/277))

