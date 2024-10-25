## Version 2.18.0.0 Release Notes

Compatible with OpenSearch 2.18.0

### Enhancements

* Filter out remote model auto redeployment (#2976)[https://github.com/opensearch-project/ml-commons/pull/2976]
* Allow llmQuestion to be optional when llmMessages is used. (#3072)[https://github.com/opensearch-project/ml-commons/pull/3072]
* Enhance batch job task management by adding default action types (#3080)[https://github.com/opensearch-project/ml-commons/pull/3080]
* use connector credential in offline batch ingestion (#2989)[https://github.com/opensearch-project/ml-commons/pull/2989]
* change to model group access for batch job task APIs (#3098)[https://github.com/opensearch-project/ml-commons/pull/3098]
* add rate limiting for offline batch jobs, set default bulk size to 500 (#3116)[https://github.com/opensearch-project/ml-commons/pull/3116]
* Support ML Inference Search Processor Writing to Search Extension (#3061)[https://github.com/opensearch-project/ml-commons/pull/3061]
* Enable pass query string to input_map in ml inference search response processor (#2899)[https://github.com/opensearch-project/ml-commons/pull/2899]
* add config field in MLToolSpec for static parameters (#2977)[https://github.com/opensearch-project/ml-commons/pull/2977]
* Add textract and comprehend url to trusted enpoints (#3154)[https://github.com/opensearch-project/ml-commons/pull/3154]


### Bug Fixes

* Fix ml inference ingest processor always return list using JsonPath (#2985)[https://github.com/opensearch-project/ml-commons/pull/2985]
* populate time fields for connectors on return (#2922)[https://github.com/opensearch-project/ml-commons/pull/2922]
* Fix for rag processor throwing NPE when optional parameters are not provided (#3057)[https://github.com/opensearch-project/ml-commons/pull/3057]
* Fix PR #2976 bug due to missing adding function_name and algorithm in querying models (#3104)[https://github.com/opensearch-project/ml-commons/pull/3104]
* Gracefully handle error when generative_qa_parameters is not provided (#3100)[https://github.com/opensearch-project/ml-commons/pull/3100]
* Fix error log to show the right agent type (#2809)[https://github.com/opensearch-project/ml-commons/pull/2809]
* fix model stuck in deploying state during node crash/cluster restart (#3137)[https://github.com/opensearch-project/ml-commons/pull/3137]

### Maintenance

* Bump protobuf version to 3.25.5 to patch potential DOS (#3083)[https://github.com/opensearch-project/ml-commons/pull/3083]
* removing api keys from the integ test log (#3112)[https://github.com/opensearch-project/ml-commons/pull/3112]
* Bump actions/download-artifact from 3 to 4.1.7 in /.github/workflows (#2881)[https://github.com/opensearch-project/ml-commons/pull/2881]
* allowing backport prs to skip approval (#3132)[https://github.com/opensearch-project/ml-commons/pull/3132]
* updating the approval requirement (#3148)[https://github.com/opensearch-project/ml-commons/pull/3148]
* unblocking the integ test pipeline for release (#3159)[https://github.com/opensearch-project/ml-commons/pull/3159]

### Infrastructure

* Support index.auto_expand_replicas 0-all for .plugins-ml-config (#3017)[https://github.com/opensearch-project/ml-commons/pull/3017]
* Add Test Env Require Approval Action (#3005)[https://github.com/opensearch-project/ml-commons/pull/3005]
* upgrading upload artifact to v4 (#3162)[https://github.com/opensearch-project/ml-commons/pull/3162]

### Documentation

* add tutorial for cross-account model invocation on amazon managed cluster (#3064)[https://github.com/opensearch-project/ml-commons/pull/3064]
* support role temporary credential in connector tutorial (#3058)[https://github.com/opensearch-project/ml-commons/pull/3058]
* connector blueprint for amazon bedrock converse (#2960)[https://github.com/opensearch-project/ml-commons/pull/2960]
* Updates dev guide to inform the workflow approval step (#3062)[https://github.com/opensearch-project/ml-commons/pull/3062]
* tune titan embedding model blueprint for v2 (#3094)[https://github.com/opensearch-project/ml-commons/pull/3094]
* Add bedrock multimodal build-in function usage example in doc (#3073)[https://github.com/opensearch-project/ml-commons/pull/3073]


