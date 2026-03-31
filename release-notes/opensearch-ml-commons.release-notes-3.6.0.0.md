## Version 3.6.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.6.0

### Features
* Add NONE pooling mode to support pre-pooled model outputs ([#4710](https://github.com/opensearch-project/ml-commons/pull/4710))
* Add LAST_TOKEN pooling implementation for text embedding models ([#4711](https://github.com/opensearch-project/ml-commons/pull/4711))
* Introduce V2 Chat Agent ([#4732](https://github.com/opensearch-project/ml-commons/pull/4732))
* [FEATURE] Semantic and Hybrid Search APIs for Long-Term Memory Retrieval ([#4658](https://github.com/opensearch-project/ml-commons/pull/4658))
* [FEATURE] Improve EncryptorImpl with Asynchronous Handling for Scalability ([#3919](https://github.com/opensearch-project/ml-commons/pull/3919))

### Enhancements
* Support aliases and wildcard index patterns in QueryPlanningTool ([#4726](https://github.com/opensearch-project/ml-commons/pull/4726))
* Adding support for custom fallback query for agentic search ([#4729](https://github.com/opensearch-project/ml-commons/pull/4729))
* Add post_memory hook with structured message ([#4687](https://github.com/opensearch-project/ml-commons/pull/4687))
* Add token usage tracking for Chat and PER agents ([#4683](https://github.com/opensearch-project/ml-commons/pull/4683))
* Add more detailed logging to Agent Workflow to enable debugging and metric collection ([#4681](https://github.com/opensearch-project/ml-commons/pull/4681))
* Add helper method for nova clean request ([#4676](https://github.com/opensearch-project/ml-commons/pull/4676))
* Support messages array in all memory types + chat history in AGUI agent ([#4645](https://github.com/opensearch-project/ml-commons/pull/4645))
* Renamed resource-action-groups.yml to resource-access-levels.yml ([#4737](https://github.com/opensearch-project/ml-commons/pull/4737))
* Override resolveClass ([#4692](https://github.com/opensearch-project/ml-commons/pull/4692))

### Bug Fixes
* Fix SdkAsyncHttpClient resource leak in connector executors ([#4716](https://github.com/opensearch-project/ml-commons/pull/4716))
* fix unsupported operation ([#4733](https://github.com/opensearch-project/ml-commons/pull/4733))
* fix context bug ([#4730](https://github.com/opensearch-project/ml-commons/pull/4730))
* restore AGUI context for legacy interface agent ([#4720](https://github.com/opensearch-project/ml-commons/pull/4720))
* Fix Tags.addTag() return value not captured after immutable Tags change ([#4712](https://github.com/opensearch-project/ml-commons/pull/4712))
* fix error code for delete api-s ([#4701](https://github.com/opensearch-project/ml-commons/pull/4701))
* Fix: Early exit in stats collector job ([#4560](https://github.com/opensearch-project/ml-commons/pull/4560))
* Fix numeric type preservation in ML inference query template substitution ([#4656](https://github.com/opensearch-project/ml-commons/pull/4656))

### Infrastructure
* Enable FIPS flag by default on build/run ([#4719](https://github.com/opensearch-project/ml-commons/pull/4719))
* Optimize IT setup, remove redundant per-test work ([#4667](https://github.com/opensearch-project/ml-commons/pull/4667))
* fix: improve CI test stability - skip unreachable OpenAI tests and fix flaky IndexUtilsTests ([#4668](https://github.com/opensearch-project/ml-commons/pull/4668))
* fix: prevent SearchModelGroupITTests timeout by disabling dedicated masters ([#4665](https://github.com/opensearch-project/ml-commons/pull/4665))
* Onboard code diff analyzer and reviewer (ml-commons) ([#4666](https://github.com/opensearch-project/ml-commons/pull/4666))
* ci: quote FIPS crypto standard parameter in workflow files ([#4659](https://github.com/opensearch-project/ml-commons/pull/4659))
* Fix ML build with 1) adapt to gradle shadow plugin v9 upgrade and 2) make ml-common fips build param aware ([#4654](https://github.com/opensearch-project/ml-commons/pull/4654))

### Maintenance
* Add maintainer ([#4660](https://github.com/opensearch-project/ml-commons/pull/4660))
