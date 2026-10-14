## Version 2.19.7 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 2.19.7

### Enhancements

* Add Eclipse P2 mirror to avoid download.eclipse.org outages during CI ([#4980](https://github.com/opensearch-project/ml-commons/pull/4980))
* Guard eclipse() formatter to spotless tasks and keep P2 mirror on pinned version ([#5005](https://github.com/opensearch-project/ml-commons/pull/5005))

### Infrastructure

* Replace retired OpenAI and Bedrock models in integration tests to fix CI failures ([#5126](https://github.com/opensearch-project/ml-commons/pull/5126))

### Maintenance

* Bump 1password/load-secrets-action to v5.0.1 ([#4984](https://github.com/opensearch-project/ml-commons/pull/4984))
* Bump bc-fips, httpcore5, and httpclient5 to address multiple CVEs ([#5115](https://github.com/opensearch-project/ml-commons/pull/5115))
* Run snapshot publish workflow on Java 21 to fix broken Maven publishing ([#5129](https://github.com/opensearch-project/ml-commons/pull/5129))
