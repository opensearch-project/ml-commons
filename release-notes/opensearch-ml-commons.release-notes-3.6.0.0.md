## Version 3.6.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.6.0

### Features

* Add LAST_TOKEN pooling implementation for decoder-only text embedding models (GPT-style, Qwen3, etc.) ([#4711](https://github.com/opensearch-project/ml-commons/pull/4711))
* Add NONE pooling mode to support pre-pooled model outputs without redundant pooling computation ([#4710](https://github.com/opensearch-project/ml-commons/pull/4710))
* Add option to skip SSL certificate validation for LLM connectors via `skip_ssl_verification` parameter ([#4394](https://github.com/opensearch-project/ml-commons/pull/4394))
* Add semantic and hybrid search APIs for long-term agentic memory retrieval ([#4658](https://github.com/opensearch-project/ml-commons/pull/4658))
* Introduce V2 Chat Agent with unified interface, multi-modal support, and simplified registration ([#4732](https://github.com/opensearch-project/ml-commons/pull/4732))
* Add token usage tracking for Chat, AG_UI, and Plan-Execute-Reflect agents ([#4683](https://github.com/opensearch-project/ml-commons/pull/4683))
* Add remote agentic memory feature flag and fix trace filtering in agentic memory queries ([#4597](https://github.com/opensearch-project/ml-commons/pull/4597))
* Add custom fallback query support for QueryPlanningTool in agentic search ([#4729](https://github.com/opensearch-project/ml-commons/pull/4729))
* Support messages array in all memory types and chat history in AG_UI agent ([#4645](https://github.com/opensearch-project/ml-commons/pull/4645))
* Support tool messages in agent revamp for Bedrock and OpenAI model providers ([#4596](https://github.com/opensearch-project/ml-commons/pull/4596))
* Allow overwriting context management during agent execution for inline-created context management ([#4637](https://github.com/opensearch-project/ml-commons/pull/4637))
* Improve EncryptorImpl with asynchronous handling for scalability, removing CountDownLatch ([#3919](https://github.com/opensearch-project/ml-commons/pull/3919))

### Enhancements

* Add helper method for Nova clean request ([#4676](https://github.com/opensearch-project/ml-commons/pull/4676))
* Add more detailed logging to Agent Workflow for debugging and metric collection ([#4681](https://github.com/opensearch-project/ml-commons/pull/4681))
* Add validation to context managers including name restrictions and improved error messages ([#4602](https://github.com/opensearch-project/ml-commons/pull/4602))
* Support aliases and wildcard index patterns in QueryPlanningTool ([#4726](https://github.com/opensearch-project/ml-commons/pull/4726))
* Adapt summarization manager for unified interface format ([#4628](https://github.com/opensearch-project/ml-commons/pull/4628))
* Add post_memory hook with structured message support for context managers ([#4687](https://github.com/opensearch-project/ml-commons/pull/4687))
* Override `ValidatingObjectInputStream.resolveClass()` to support plugin classloader fallback ([#4692](https://github.com/opensearch-project/ml-commons/pull/4692))
* Move SSL configuration to `client_config` as a connector-level setting ([#4616](https://github.com/opensearch-project/ml-commons/pull/4616))
* Escape tool name and description to handle quotation marks properly ([#4747](https://github.com/opensearch-project/ml-commons/pull/4747))

### Bug Fixes

* Fix Tags.addTag() return value not captured after immutable Tags change in MLModel and MLAgent ([#4712](https://github.com/opensearch-project/ml-commons/pull/4712))
* Fix SdkAsyncHttpClient resource leak in connector executors causing connection pool exhaustion ([#4716](https://github.com/opensearch-project/ml-commons/pull/4716))
* Fix `connection_timeout` and `read_timeout` defaults from 30000 to 30 to match seconds unit ([#4759](https://github.com/opensearch-project/ml-commons/pull/4759))
* Fix early exit in stats collector job when fetching connector for model details ([#4560](https://github.com/opensearch-project/ml-commons/pull/4560))
* Fix `agent_id` parameter conflict by renaming to `agent_id_log` for logging to prevent infinite loop in AgentTool ([#4762](https://github.com/opensearch-project/ml-commons/pull/4762))
* Fix MCP connector setting not being respected for Agent V2 ([#4739](https://github.com/opensearch-project/ml-commons/pull/4739))
* Fix thread context restoration in MLAgentExecutor to resolve memory access failures ([#4608](https://github.com/opensearch-project/ml-commons/pull/4608))
* Fix numeric type preservation in ML inference query template substitution ([#4656](https://github.com/opensearch-project/ml-commons/pull/4656))
* Fix deserialization failing for models with built-in connector ([#4627](https://github.com/opensearch-project/ml-commons/pull/4627))
* Fix error code for delete context management template API to return 404 instead of 500 ([#4701](https://github.com/opensearch-project/ml-commons/pull/4701))
* Fix unsupported operation issue when putting agent ID into immutable map ([#4733](https://github.com/opensearch-project/ml-commons/pull/4733))
* Fix thread context restoration in agent-related classes for agentic memory ([#4621](https://github.com/opensearch-project/ml-commons/pull/4621))
* Fix incorrect HTTP status codes when deleting memory containers and improve error handling ([#4723](https://github.com/opensearch-project/ml-commons/pull/4723))
* Broaden error handling from `OpenSearchStatusException` to `OpenSearchException` to preserve 4XX client errors ([#4725](https://github.com/opensearch-project/ml-commons/pull/4725))
* Fix context bug where user information was missing due to incorrect context restoration ([#4730](https://github.com/opensearch-project/ml-commons/pull/4730))
* Restore AG_UI context for legacy interface agent ([#4720](https://github.com/opensearch-project/ml-commons/pull/4720))
* Add overload constructor to unblock skills plugin ([#4626](https://github.com/opensearch-project/ml-commons/pull/4626))
* Fix RestChatAgentIT teardown failure when AWS credentials are absent ([#4772](https://github.com/opensearch-project/ml-commons/pull/4772))

### Infrastructure

* Fix ML build to adapt to Gradle shadow plugin v9 upgrade and make FIPS build param aware ([#4654](https://github.com/opensearch-project/ml-commons/pull/4654))
* Enable FIPS flag by default on build and run ([#4719](https://github.com/opensearch-project/ml-commons/pull/4719))
* Quote FIPS crypto standard parameter in CI workflow files ([#4659](https://github.com/opensearch-project/ml-commons/pull/4659))
* Onboard code diff analyzer and reviewer for ml-commons ([#4666](https://github.com/opensearch-project/ml-commons/pull/4666))
* Optimize integration test setup to eliminate redundant per-test work, reducing execution time by ~50% ([#4667](https://github.com/opensearch-project/ml-commons/pull/4667))
* Improve CI test stability by skipping unreachable OpenAI tests and fixing flaky IndexUtilsTests ([#4668](https://github.com/opensearch-project/ml-commons/pull/4668))
* Prevent SearchModelGroupITTests timeout by disabling dedicated masters and fix connection pool exhaustion ([#4665](https://github.com/opensearch-project/ml-commons/pull/4665))
* Increase Cohere integration test timeouts to 120s and add service reachability checks ([#4767](https://github.com/opensearch-project/ml-commons/pull/4767))
* Upgrade Bedrock Claude models in integration tests for higher rate limits ([#4742](https://github.com/opensearch-project/ml-commons/pull/4742))
* Rename `resource-action-groups.yml` to `resource-access-levels.yml` for security checks ([#4737](https://github.com/opensearch-project/ml-commons/pull/4737))

### Documentation

* Add 3.5.0 release notes ([#4577](https://github.com/opensearch-project/ml-commons/pull/4577))
* Add `rithin-pullela-aws` as maintainer ([#4660](https://github.com/opensearch-project/ml-commons/pull/4660))
