## Version 2.4.1.0 Release Notes

Compatible with OpenSearch 2.4.1


### Bug Fixes

* wait for upload task to complete in security tests ([#551](https://github.com/opensearch-project/ml-commons/pull/551))
* fix running task when reload loaded model on single node cluster ([#561](https://github.com/opensearch-project/ml-commons/pull/561))
* change model state to UPLOADED when all chunks uploaded ([#573](https://github.com/opensearch-project/ml-commons/pull/573))
* set model state as unloaded when call unload model API ([#580](https://github.com/opensearch-project/ml-commons/pull/580))


### Maintenance

* Increment version to 2.4.1-SNAPSHOT ([#560](https://github.com/opensearch-project/ml-commons/pull/560))
* force protobuf-java version as 3.21.9 ([#588](https://github.com/opensearch-project/ml-commons/pull/588))
* fix junit version ([#597](https://github.com/opensearch-project/ml-commons/pull/597))


