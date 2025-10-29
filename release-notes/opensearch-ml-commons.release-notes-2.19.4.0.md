## Version 2.19.4 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 2.19.4

### Bug Fixes
* Fix CVE-2025-55163, CVE-2025-48924, CVE-2025-58057 ([#4339](https://github.com/opensearch-project/ml-commons/pull/4339))

### Infrastructure
* Onboard to s3 snapshots ([#4320](https://github.com/opensearch-project/ml-commons/pull/4320))
* Pass isMultiTenancyEnabled across classes to early return index search ([#4113](https://github.com/opensearch-project/ml-commons/pull/4113))

### Refactoring
* Refactors undeploy models client with sdkClient bulk op ([#4077](https://github.com/opensearch-project/ml-commons/pull/4077))