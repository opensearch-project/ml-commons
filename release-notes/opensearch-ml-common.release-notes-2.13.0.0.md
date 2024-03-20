## Version 2.13.0.0 Release Notes

Compatible with OpenSearch 2.13.0


### Features
* hidden agent ([#2204](https://github.com/opensearch-project/ml-commons/pull/2204))
* auto deployment for remote models ([#2206](https://github.com/opensearch-project/ml-commons/pull/2206))
* support question answering model ([#2208](https://github.com/opensearch-project/ml-commons/pull/2208))
* Guardrails for remote model input and output ([#2209](https://github.com/opensearch-project/ml-commons/pull/2209))

### Enhancements
* Add verification to rate limiter number field ([#2113](https://github.com/opensearch-project/ml-commons/pull/2113))
* asymmetric embeddings ([#2123](https://github.com/opensearch-project/ml-commons/pull/2123))
* Set the number of ml system index primary shards to 1 ([#2137](https://github.com/opensearch-project/ml-commons/pull/2137))
* prevent exposing internal ip when an agent gets an internal OpenSearch exception ([#2154](https://github.com/opensearch-project/ml-commons/pull/2154))
* add remote predict thread pool ([#2207](https://github.com/opensearch-project/ml-commons/pull/2207))

### Infrastructure


### Bug Fixes
* fix error code when executing agent ([#2120](https://github.com/opensearch-project/ml-commons/pull/2120))
* fix npe when executing agent with empty parameter ([#2145](https://github.com/opensearch-project/ml-commons/pull/2145))
* fix delete model cache on macOS causing model deploy fail with model ([#2180](https://github.com/opensearch-project/ml-commons/pull/2180))
* adding BWC for connector config field ([#2184](https://github.com/opensearch-project/ml-commons/pull/2184))
* Fix onnx dep ([#2198](https://github.com/opensearch-project/ml-commons/pull/2198))
* update the response code to 404 when deleting a memory ([#2212](https://github.com/opensearch-project/ml-commons/pull/2212))
* Fix model enable flag not loading ([#2221](https://github.com/opensearch-project/ml-commons/pull/2221))
* fix json error ([#2234](https://github.com/opensearch-project/ml-commons/pull/2234))
* update remote model auto deploy tests in predict runner ([#2237](https://github.com/opensearch-project/ml-commons/pull/2237))

### Documentation
* add tutorial for semantic search with byte quantized vector and Cohere embedding model ([#2127](https://github.com/opensearch-project/ml-commons/pull/2127))
* add tutorial for rerank pipeline with Cohere rerank model ([#2134](https://github.com/opensearch-project/ml-commons/pull/2134))
* add tutorial for chatbot with rag ([#2141](https://github.com/opensearch-project/ml-commons/pull/2141))
* add tutorial for CFN template integration ([#2161](https://github.com/opensearch-project/ml-commons/pull/2161))
* fix cohere chat blueprint ([#2167](https://github.com/opensearch-project/ml-commons/pull/2167))
* add demo notebook for creating connector ([#2192](https://github.com/opensearch-project/ml-commons/pull/2192))
* enhance connector helper notebook to support 2.9 ([#2202](https://github.com/opensearch-project/ml-commons/pull/2202))


### Maintenance
* Bump common-compress package to fix CVE ([#2186](https://github.com/opensearch-project/ml-commons/pull/2186))
* Suppress removal AccessController in java.security has been deprecated and marked for removal ([#2195](https://github.com/opensearch-project/ml-commons/pull/2195))

### Refactoring
* refactor memory logs ([#2121](https://github.com/opensearch-project/ml-commons/pull/2121))
* parse tool input to map ([#2131](https://github.com/opensearch-project/ml-commons/pull/2131))




