## Version 2.19.0.0 Release Notes

Compatible with OpenSearch 2.19.0

### Enhancements

* adding multi-modal pre-processor for cohere (#3219)[https://github.com/opensearch-project/ml-commons/pull/3219]
* [Enhancement] Fetch system index mappings from json file instead of string constants (#3153)[https://github.com/opensearch-project/ml-commons/pull/3153]
* Retrieve remote model id from registration response in IT to avoid flaky (#3244)[https://github.com/opensearch-project/ml-commons/pull/3244]
* [Refactor] Remove bloat due to unnecessary setup in test and add retry for potential flaky behavior due to timeout (#3259)[https://github.com/opensearch-project/ml-commons/pull/3259/files]
* [Enhancement] Enhance validation for create connector API (#3260)[https://github.com/opensearch-project/ml-commons/pull/3260]
* Add application_type to ConversationMeta; update tests (#3282)[https://github.com/opensearch-project/ml-commons/pull/3282]
* Enhance Message and Memory API Validation and storage (#3283)[https://github.com/opensearch-project/ml-commons/pull/3283]
* Use Adagrad optimiser for Linear regression by default (#3291)[https://github.com/opensearch-project/ml-commons/pull/3291/files]
* [Enhancement] Add schema validation and placeholders to index mappings (#3240)[https://github.com/opensearch-project/ml-commons/pull/3240]
* add action input as parameters for tool execution in conversational agent (#3200)[https://github.com/opensearch-project/ml-commons/pull/3200]
* Remove ignore decorator for testCohereClassifyModel (#3324)[https://github.com/opensearch-project/ml-commons/pull/3324]
* refactor: modifying log levels and adding more logs to display error details (#3337)[https://github.com/opensearch-project/ml-commons/pull/3337]
* Primary setup for Multi-tenancy (#3307)[https://github.com/opensearch-project/ml-commons/pull/3307]
* apply multi-tenancy and sdk client in Connector (Create + Get + Delete) (#3382)[https://github.com/opensearch-project/ml-commons/pull/3382]
* adding multi-tenancy + sdk client related changes to model, model group and connector update (#3399)[https://github.com/opensearch-project/ml-commons/pull/3399]
* applying multi-tenancy to task apis, deploy, predict apis (#3416)[https://github.com/opensearch-project/ml-commons/pull/3416]
* adding tenantID to the request + undeploy request (#3425)[https://github.com/opensearch-project/ml-commons/pull/3425]
* Check before delete (#3209)[https://github.com/opensearch-project/ml-commons/pull/3209]
* multi-tenancy + sdk client related changes in agents (#3432)[https://github.com/opensearch-project/ml-commons/pull/3432]
* Introduce Ml Inference Search Request Extension (#3284)[https://github.com/opensearch-project/ml-commons/pull/3284]
* Cherry-pick BWC fix for system prompt and user instructions (#3437)[https://github.com/opensearch-project/ml-commons/pull/3437]
* Add deepseek as a trusted endpoint. (#3440)[https://github.com/opensearch-project/ml-commons/pull/3440]
* applying multi-tenancy in search [model, model group, agent, connector] (#3433)[https://github.com/opensearch-project/ml-commons/pull/3433]
* Added amazon rekognition as a trust endpoint (#3419)[https://github.com/opensearch-project/ml-commons/pull/3419]
* adding multi-tenancy to config api and master key related changes (#3439)[https://github.com/opensearch-project/ml-commons/pull/3439]
* Undeploy models with no WorkerNodes (#3380)[https://github.com/opensearch-project/ml-commons/pull/3380]
* support batch task management by periodically bolling the remote task via a cron job (#3421)[https://github.com/opensearch-project/ml-commons/pull/3421]
* Add pre and post process functions for Bedrock Rerank API #3254 (#3339)[https://github.com/opensearch-project/ml-commons/pull/3339]
* [Backport 2.19] [BACKPORT 2.x] applying multi-tenancy in search [model, model group, agent, connector] (#3433) (#3469)[https://github.com/opensearch-project/ml-commons/pull/3469]
* [Backport 2.19] fixing connector validation (#3471)[https://github.com/opensearch-project/ml-commons/pull/3471]
* [BACKPORT 2.x] applying multi-tenancy in search [model, model group, agent, connector] (#3433) (#3443) (#3469)[https://github.com/opensearch-project/ml-commons/pull/3469]


### Bug Fixes

* getFirst is not allowed in java 17 (#3226)[https://github.com/opensearch-project/ml-commons/pull/3226]
* Fix: ml/engine/utils/FileUtils casts long file length to int incorrectly (#3198)[https://github.com/opensearch-project/ml-commons/pull/3198]
* fix for sync up job not working in 2.17 when upgraded from previous versions (#3241)[https://github.com/opensearch-project/ml-commons/pull/3241]
* Fix remote model with embedding input issue (#3289)[https://github.com/opensearch-project/ml-commons/pull/3289]
* Adds preset contentRegistry for IngestProcessors (#3281)[https://github.com/opensearch-project/ml-commons/pull/3281]
* Revert "Add application_type to ConversationMeta; update tests (#3282)" (#3315)[https://github.com/opensearch-project/ml-commons/pull/3315]
* Revert "Filter out remote model auto redeployment (#2976)" and related commits (#3104, #3214) (#3368)[https://github.com/opensearch-project/ml-commons/pull/3368]
* Fix JsonGenerationException error in Local Sample Calculator and Anomaly Localization Execution Response (#3434)[https://github.com/opensearch-project/ml-commons/pull/3434]
* [Backport 2.19] Fix guardrail it for 2.19 (#3468)[https://github.com/opensearch-project/ml-commons/pull/3468]
* addressing client changes due to adding tenantId in the apis (#3474) (#3480)[https://github.com/opensearch-project/ml-commons/pull/3480]


### Maintenance

* Bump guava version to 32.1.3 (#3300)[https://github.com/opensearch-project/ml-commons/pull/3300]
* Update Gradle to 8.11.1 (#3139)[https://github.com/opensearch-project/ml-commons/pull/3139/files]
* Force version 3.29.0 of org.eclipse.core.runtime to mitigate CVE vulnerabilities (#3313)[https://github.com/opensearch-project/ml-commons/pull/3313]
* Upgraded software.amazon.awssdk from 2.25.40 to 2.29.0 to address CVE… (#3320)[https://github.com/opensearch-project/ml-commons/pull/3320]
* Adding back Mingshi as Maintainer. (#3367)[https://github.com/opensearch-project/ml-commons/pull/3367]
* updating sdk client version (#3392)[https://github.com/opensearch-project/ml-commons/pull/3392]
* downgrading codecov action (#3409) (#3410)[https://github.com/opensearch-project/ml-commons/pull/3410]
* fix CVE from ai.djl dependency (#3478) (#3482)[https://github.com/opensearch-project/ml-commons/pull/3482]


### Infrastructure

* Enable custom start commands and options to resolve GHA issues (#3223)[https://github.com/opensearch-project/ml-commons/pull/3223]
* Add Spotless Check to maintain consistency (#3386)[https://github.com/opensearch-project/ml-commons/pull/3386]
* Add runs-on field to Spotless Check step in CI (#3400)[https://github.com/opensearch-project/ml-commons/pull/3400]
* Checkout code from pull request head for spotless (#3422)[https://github.com/opensearch-project/ml-commons/pull/3422]
* Fixes spotless on Java 11 (#3449)[https://github.com/opensearch-project/ml-commons/pull/3449]
* add spotless to all build.gradle files (#3453)[https://github.com/opensearch-project/ml-commons/pull/3453]
* Fixes Two Flaky IT classes RestMLGuardrailsIT & ToolIntegrationWithLLMTest (#3253)[https://github.com/opensearch-project/ml-commons/pull/3253]
* Improve test coverage for RemoteModel.java (#3205)[https://github.com/opensearch-project/ml-commons/pull/3205]
* Revert Text Block changes from "Enhance validation for create connector API" #3260 (#3329)[https://github.com/opensearch-project/ml-commons/pull/3329]


### Documentation

* Fix: typo in MLAlgoParams (#3195)[https://github.com/opensearch-project/ml-commons/pull/3195/files]
* Add tutorial for bge-reranker-m3-v2, multilingual cross-encoder model on SageMaker (#2848)[https://github.com/opensearch-project/ml-commons/commit/https://github.com/opensearch-project/ml-commons/pull/2848]
* [Backport main] adding blue print doc for cohere multi-modal model (#3232)[https://github.com/opensearch-project/ml-commons/pull/3232]
* Tutorial for using Asymmetric models (#3258)[https://github.com/opensearch-project/ml-commons/pull/3258]
* add tutorials for cross encoder models on Amazon Bedrock (#3278)[https://github.com/opensearch-project/ml-commons/pull/3278]
* fix typo (#3234)[https://github.com/opensearch-project/ml-commons/pull/3234]
* Tutorial for ml inference with cohere rerank model (#3398)[https://github.com/opensearch-project/ml-commons/pull/3398]
* Add DeepSeek connector blueprint (#3436)[https://github.com/opensearch-project/ml-commons/pull/3436]
* fix post_process_function on rerank_pipeline_with_bge-rerank-m3-v2_model_deployed_on_Sagemaker.md (#3296)[https://github.com/opensearch-project/ml-commons/pull/3296]
