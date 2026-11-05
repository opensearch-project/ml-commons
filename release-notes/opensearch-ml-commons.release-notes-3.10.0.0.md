## Version 3.10.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.10.0

### Features

* Add client certificate authentication support for MCP connectors ([#5003](https://github.com/opensearch-project/ml-commons/pull/5003))
* Add SearchTemplateFillTool for agentic search templates ([#5106](https://github.com/opensearch-project/ml-commons/pull/5106))
* Expose index name in QueryPlanningTool default user prompt so the LLM knows which index it is planning a query for ([#5127](https://github.com/opensearch-project/ml-commons/pull/5127))

### Enhancements

* Fill list-type params as JSON arrays in SearchTemplateFillTool ([#5163](https://github.com/opensearch-project/ml-commons/pull/5163))
* Derive sortable-aware field enums from all target index mappings for agentic search templates ([#5122](https://github.com/opensearch-project/ml-commons/pull/5122))
* Only describe a should clause as scoring-only when its enclosing bool makes it optional in agentic search templates ([#5164](https://github.com/opensearch-project/ml-commons/pull/5164))
* Correct derived parameter and template descriptions for agentic search templates ([#5123](https://github.com/opensearch-project/ml-commons/pull/5123))
* Return 409 Conflict when re-registering a model group with the same custom ID ([#5155](https://github.com/opensearch-project/ml-commons/pull/5155))

### Bug Fixes

* Clear cached master keys when the ML config index is replaced, preventing decryption failures after snapshot restore ([#5166](https://github.com/opensearch-project/ml-commons/pull/5166))
* Fix left-padded LAST_TOKEN pooling selecting a padding token instead of the true last real token ([#4744](https://github.com/opensearch-project/ml-commons/pull/4744))
* Keep deployed models when the deploy task's coordinating node leaves the cluster during deployment ([#5143](https://github.com/opensearch-project/ml-commons/pull/5143))
* Serve predictions from deployed nodes when a local model is partially deployed instead of rejecting all requests ([#5142](https://github.com/opensearch-project/ml-commons/pull/5142))
* Return the proper not-found error body on metadata-only update of a missing agentic search template ([#5120](https://github.com/opensearch-project/ml-commons/pull/5120))
* Fix param-schema derivation for quoted triples, comments, empty renders, and list iteration in agentic search templates ([#5121](https://github.com/opensearch-project/ml-commons/pull/5121))
* Fix typo in input/output map example within ml-inference processor documentation ([#3556](https://github.com/opensearch-project/ml-commons/pull/3556))

### Infrastructure

* Skip RestSearchTemplateFillToolIT on the containerized cluster CI leg ([#5165](https://github.com/opensearch-project/ml-commons/pull/5165))
