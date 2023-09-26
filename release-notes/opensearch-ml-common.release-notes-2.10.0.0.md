## Version 2.10.0.0 Release Notes

Compatible with OpenSearch 2.10.0


### Experimental Features
* Conversations and Generative AI in OpenSearch ([#1150](https://github.com/opensearch-project/ml-commons/issues/1150))

### Enhancements
* Add feature flags for remote inference ([#1223](https://github.com/opensearch-project/ml-commons/pull/1223))
* Add eligible node role settings ([#1197](https://github.com/opensearch-project/ml-commons/pull/1197))
* Add more stats: connector count, connector/config index status ([#1180](https://github.com/opensearch-project/ml-commons/pull/1180))

### Infrastructure
* Updates demo certs used in integ tests ([#1291](https://github.com/opensearch-project/ml-commons/pull/1291))
* Add Auto Release Workflow ([#1306](https://github.com/opensearch-project/ml-commons/pull/1306))

### Bug Fixes
* Fixing metrics ([#1194](https://github.com/opensearch-project/ml-commons/pull/1194))
* Fix null pointer exception when input parameter is null. ([#1192](https://github.com/opensearch-project/ml-commons/pull/1192))
* Fix admin with no backend role on AOS unable to create restricted model group ([#1188](https://github.com/opensearch-project/ml-commons/pull/1188))
* Fix parameter parsing bug for create connector input ([#1185](https://github.com/opensearch-project/ml-commons/pull/1185))
* Handle escaping string parameters explicitly ([#1174](https://github.com/opensearch-project/ml-commons/pull/1174))
* Fix model count bug ([#1180](https://github.com/opensearch-project/ml-commons/pull/1180))
* Fix core package name to address compilation errors ([#1157](https://github.com/opensearch-project/ml-commons/pull/1157))
* Fix system index access bug ([#1320](https://github.com/opensearch-project/ml-commons/pull/1320))
* Fix unassigned ml system shard replicas ([#1315](https://github.com/opensearch-project/ml-commons/pull/1315))
* Adjust index replicas settings to keep consistent with AOS 2.9 ([#1325](https://github.com/opensearch-project/ml-commons/pull/1325))
* Fix GetInteractions returned different results in security-enabled and -disabled settings ([#1334](https://github.com/opensearch-project/ml-commons/pull/1334))

### Documentation
* Updating cohere blueprint doc ([#1213](https://github.com/opensearch-project/ml-commons/pull/1213))
* Fixing docs ([#1193](https://github.com/opensearch-project/ml-commons/pull/1193))
* Add model auto redeploy tutorial ([#1175](https://github.com/opensearch-project/ml-commons/pull/1175))
* Add remote inference tutorial ([#1158](https://github.com/opensearch-project/ml-commons/pull/1158))
* Adding blueprint examples for remote inference ([#1155](https://github.com/opensearch-project/ml-commons/pull/1155))
* Updating developer guide for CCI contributors ([#1049](https://github.com/opensearch-project/ml-commons/pull/1049))

### Maintenance
* Bump checkstyle version for CVE fix ([#1216](https://github.com/opensearch-project/ml-commons/pull/1216))
* Correct imports for new location with regard to core refactoring ([#1206](https://github.com/opensearch-project/ml-commons/pull/1206))
* Fix breaking change caused by opensearch core ([#1187](https://github.com/opensearch-project/ml-commons/pull/1187))
* Bump OpenSearch snapshot version to 2.10 ([#1157](https://github.com/opensearch-project/ml-commons/pull/1157))
* Bump aws-encryption-sdk-java to fix CVE-2023-33201 ([#1309](https://github.com/opensearch-project/ml-commons/pull/1309))

### Refactoring
* Renaming metrics ([#1224](https://github.com/opensearch-project/ml-commons/pull/1224))
* Changing messaging for IllegalArgumentException on duplicate model groups ([#1294](https://github.com/opensearch-project/ml-commons/pull/1294))
* Fixing some error message handeling ([#1222](https://github.com/opensearch-project/ml-commons/pull/1222)) 
