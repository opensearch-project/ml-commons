## Version 3.4.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.4.0

### Features
* Nova mme support ([#4360](https://github.com/opensearch-project/ml-commons/pull/4360))

### Enhancements
* Declare credential and *.Authorization as sensitive param in create connector API ([#4308](https://github.com/opensearch-project/ml-commons/pull/4308))
* Pass resourceType instead of resourceIndex to resourceSharingClient ([#4333](https://github.com/opensearch-project/ml-commons/pull/4333))
* allow higher maximum number of batch inference job tasks ([#4474](https://github.com/opensearch-project/ml-commons/pull/4474))

### Bug Fixes
* Fix agent type update ([#4341](https://github.com/opensearch-project/ml-commons/pull/4341))
* Handle edge case of empty values of tool configs ([#4479](https://github.com/opensearch-project/ml-commons/pull/4479))
* Fix OpenAI RAG integration tests: Replace Wikimedia image URL with Unsplash ([#4472](https://github.com/opensearch-project/ml-commons/pull/4472))
* Remove the error log on request body ([#4450](https://github.com/opensearch-project/ml-commons/pull/4450))
* [Agentic Search] Fix model id parsing for QueryPlanningTool ([#4458](https://github.com/opensearch-project/ml-commons/pull/4458))
* Fix several bugs on agentic memory ([#4476](https://github.com/opensearch-project/ml-commons/pull/4476))

### Infrastructure
* Update JDK to 25 and Gradle to 9.2 ([#4465](https://github.com/opensearch-project/ml-commons/pull/4465))
* Fix dependency conflict and jar hell ([#4405](https://github.com/opensearch-project/ml-commons/pull/4405))
* Decrease Disk Circuit Breaker Free Space Threshold to unblock CI ([#4413](https://github.com/opensearch-project/ml-commons/pull/4413))
* Revert #4487 #4489 as it is resolved in build get image scripts ([#4498](https://github.com/opensearch-project/ml-commons/pull/4498))
