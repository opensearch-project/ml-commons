## Version 3.6.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.6.0

### Features
* Introduce V2 Chat Agent with unified interface, multi-modal support, and simplified registration ([#4732](https://github.com/opensearch-project/ml-commons/pull/4732))
* Add semantic and hybrid search APIs for long-term memory retrieval in agentic memory ([#4658](https://github.com/opensearch-project/ml-commons/pull/4658))
* Add token usage tracking for Conversational, AG_UI, and Plan-Execute-Reflect agents ([#4683](https://github.com/opensearch-project/ml-commons/pull/4683))
* Add LAST_TOKEN pooling implementation for text embedding models used by decoder-only models ([#4711](https://github.com/opensearch-project/ml-commons/pull/4711))
* Add NONE pooling mode to support pre-pooled model outputs without redundant pooling computation ([#4710](https://github.com/opensearch-project/ml-commons/pull/4710))
* Add support for custom fallback query in QueryPlanningTool for agentic search ([#4729](https://github.com/opensearch-project/ml-commons/pull/4729))
* Support messages array in all memory types and chat history in AGUI agent ([#4645](https://github.com/opensearch-project/ml-commons/pull/4645))
* Add post-memory hook with structured message support for context managers ([#4687](https://github.com/opensearch-project/ml-commons/pull/4687))
* Improve EncryptorImpl with asynchronous handling for scalability and fix duplicate master key generation ([#3919](https://github.com/opensearch-project/ml-commons/pull/3919))
* Support aliases and wildcard index patterns in QueryPlanningTool ([#4726](https://github.com/opensearch-project/ml-commons/pull/4726))

### Enhancements
* Add more detailed logging to Agent Workflow to enable debugging and metric collection ([#4681](https://github.com/opensearch-project/ml-commons/pull/4681))
* Allow overwrite during execute for inline create context management during agent register ([#4637](https://github.com/opensearch-project/ml-commons/pull/4637))
* Add helper method for Nova clean request ([#4676](https://github.com/opensearch-project/ml-commons/pull/4676))
* Override ValidatingObjectInputStream.resolveClass() to support plugin classloader fallback ([#4692](https://github.com/opensearch-project/ml-commons/pull/4692))
* Restore AGUI context for legacy interface agent ([#4720](https://github.com/opensearch-project/ml-commons/pull/4720))
* Escape tool name and description to handle quotation marks properly ([#4747](https://github.com/opensearch-project/ml-commons/pull/4747))

### Bug Fixes
* Fix SdkAsyncHttpClient resource leak in connector executors causing connection pool exhaustion ([#4716](https://github.com/opensearch-project/ml-commons/pull/4716))
* Fix Tags.addTag() return value not captured after immutable Tags change, causing tags to be silently dropped ([#4712](https://github.com/opensearch-project/ml-commons/pull/4712))
* Fix connection_timeout and read_timeout defaults from 30000 to 30 to match seconds unit ([#4759](https://github.com/opensearch-project/ml-commons/pull/4759))
* Fix numeric type preservation in ML inference query template substitution ([#4656](https://github.com/opensearch-project/ml-commons/pull/4656))
* Fix early exit in stats collector job when a connector is fetched for model details ([#4560](https://github.com/opensearch-project/ml-commons/pull/4560))
* Fix RestChatAgentIT teardown failure when AWS credentials are absent ([#4772](https://github.com/opensearch-project/ml-commons/pull/4772))
* Fix agent_id parameter conflict by renaming to agent_id_log for logging to prevent infinite loop in AgentTool ([#4762](https://github.com/opensearch-project/ml-commons/pull/4762))
* Fix MCP connector setting not being respected for Agent V2 ([#4739](https://github.com/opensearch-project/ml-commons/pull/4739))
* Fix incorrect error codes when deleting memory containers and context management templates ([#4723](https://github.com/opensearch-project/ml-commons/pull/4723))
* Fix error handling to use OpenSearchException instead of OpenSearchStatusException for broader 4XX client error coverage ([#4725](https://github.com/opensearch-project/ml-commons/pull/4725))
* Fix error code for delete context management template API to return 404 instead of 500 ([#4701](https://github.com/opensearch-project/ml-commons/pull/4701))
* Fix context restoration bug where user information was missing ([#4730](https://github.com/opensearch-project/ml-commons/pull/4730))
* Fix unsupported operation issue when putting agent ID into immutable map ([#4733](https://github.com/opensearch-project/ml-commons/pull/4733))
* Fix Cohere integration test timeouts by increasing timeout to 120s and adding reachability checks ([#4767](https://github.com/opensearch-project/ml-commons/pull/4767))

### Infrastructure
* Fix ML build to adapt to Gradle shadow plugin v9 upgrade and make ml-commons FIPS build param aware ([#4654](https://github.com/opensearch-project/ml-commons/pull/4654))
* Enable FIPS flag by default on build/run ([#4719](https://github.com/opensearch-project/ml-commons/pull/4719))
* Onboard code diff analyzer and reviewer for ml-commons ([#4666](https://github.com/opensearch-project/ml-commons/pull/4666))
* Optimize integration test setup to eliminate redundant per-test work, reducing execution time by ~50% ([#4667](https://github.com/opensearch-project/ml-commons/pull/4667))
* Quote FIPS crypto standard parameter in CI workflow files for consistency ([#4659](https://github.com/opensearch-project/ml-commons/pull/4659))
* Improve CI test stability by skipping unreachable OpenAI tests and fixing flaky IndexUtilsTests ([#4668](https://github.com/opensearch-project/ml-commons/pull/4668))
* Prevent SearchModelGroupITTests timeout by disabling dedicated masters and fix Bedrock connection pool exhaustion ([#4665](https://github.com/opensearch-project/ml-commons/pull/4665))
* Upgrade Bedrock Claude models in integration tests for higher rate limits ([#4742](https://github.com/opensearch-project/ml-commons/pull/4742))
* Rename resource-action-groups.yml to resource-access-levels.yml to fix security checks ([#4737](https://github.com/opensearch-project/ml-commons/pull/4737))
