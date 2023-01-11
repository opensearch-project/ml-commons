## Version 2.5.0.0 Release Notes

Compatible with OpenSearch 2.5.0

### Features
* support uploading prebuilt model ([#655](https://github.com/opensearch-project/ml-commons/pull/655))
* disable prebuilt model ([#682](https://github.com/opensearch-project/ml-commons/pull/682))
* Add native memory circuit breaker. ([#689](https://github.com/opensearch-project/ml-commons/pull/689))

### Enhancements
* add more parameters for text embedding model ([#640](https://github.com/opensearch-project/ml-commons/pull/640))
* add more pooling method and refactor ([#672](https://github.com/opensearch-project/ml-commons/pull/672))
* add ML task timeout setting and clean up expired tasks from cache ([#662](https://github.com/opensearch-project/ml-commons/pull/662))
* change only run on ml node setting default value to true ([#686](https://github.com/opensearch-project/ml-commons/pull/686))

### Infrastructure
* unit tests coverage for load/unload/syncup ([#592](https://github.com/opensearch-project/ml-commons/pull/592))
* Add .whitesource configuration file ([#626](https://github.com/opensearch-project/ml-commons/pull/626))
* bump djl to 0.20 and add onnxruntime-gpu dependency ([#644](https://github.com/opensearch-project/ml-commons/pull/644))
* Remove jackson-databind and jackson-annotations dependencies now coming from core ([#652](https://github.com/opensearch-project/ml-commons/pull/652))
* Revert "Remove jackson-databind and jackson-annotations dependencies now coming from core" ([#687](https://github.com/opensearch-project/ml-commons/pull/687))
* Adding backwards compatibility test for ml-commons plugin ([#681](https://github.com/opensearch-project/ml-commons/pull/681))
* Change the inheritance of the BWC test file ([#692](https://github.com/opensearch-project/ml-commons/pull/692))

### Documentation

* Updated MAINTAINERS.md format ([#668](https://github.com/opensearch-project/ml-commons/pull/668))
* Updating maintainers list ([#663](https://github.com/opensearch-project/ml-commons/pull/663))
* add doc about how to setup GPU ML node ([#677](https://github.com/opensearch-project/ml-commons/pull/677))

### Maintenance

* Increment version to 2.5.0-SNAPSHOT ([#513](https://github.com/opensearch-project/ml-commons/pull/513))

### Refactoring

* change task worker node to list; add target worker node to cache ([#656](https://github.com/opensearch-project/ml-commons/pull/656)) 