# LLM model setup for Search Relevance Workbench

Search Relevance Workbench (SRW) generates LLM judgments by calling models registered in the [ml-commons plugin](https://github.com/opensearch-project/ml-commons).

SRW is **provider-agnostic**: on every prediction it sends a neutral parameter set and reads the model's text back through a tolerant parser. It does **not** need to know which provider you use. Each provider's request and response differences are absorbed by the ml-commons **connector blueprint** — specifically its `request_body` template and a `post_process_function` that returns the model text in the neutral `response` field. This is the same mechanism OpenSearch neural search uses to support many embedding providers without provider-specific plugin code.

This directory contains connector blueprints in the same Markdown format as [ml-commons `remote_inference_blueprints`](https://github.com/opensearch-project/ml-commons/tree/main/docs/remote_inference_blueprints): each is a set of copy-paste REST calls (create connector, register and deploy, test). The provider coverage mirrors the chat-model blueprints in ml-commons; embedding-only blueprints are not included because LLM judgment needs a generative model.

| Provider | Blueprint | ml-commons blueprint it corresponds to | Transport |
|---|---|---|---|
| OpenAI Chat Completions | [openai_chat_blueprint.md](openai_chat_blueprint.md) | `open_ai_connector_chat_blueprint` | HTTP |
| Azure OpenAI Chat | [azure_openai_chat_blueprint.md](azure_openai_chat_blueprint.md) | `azure_openai_connector_chat_blueprint` | HTTP |
| DeepSeek Chat (OpenAI-compatible) | [deepseek_chat_blueprint.md](deepseek_chat_blueprint.md) | `deepseek_connector_chat_blueprint` | HTTP |
| Ollama / local OpenAI-compatible (vLLM, llama.cpp) | [ollama_chat_blueprint.md](ollama_chat_blueprint.md) | `ollama_connector_chat_blueprint` | HTTP |
| Google Gemini | [google_gemini_chat_blueprint.md](google_gemini_chat_blueprint.md) | `google_gemini_connector_chat_blueprint` | HTTP |
| Anthropic Claude (Bedrock) | [anthropic_claude_bedrock_blueprint.md](anthropic_claude_bedrock_blueprint.md) | `bedrock_connector_anthropic_claude*_blueprint` | AWS SigV4 |
| Amazon Bedrock (Converse, generic) | [bedrock_converse_blueprint.md](bedrock_converse_blueprint.md) | `bedrock_connector_converse_blueprint` | AWS SigV4 |

The **Bedrock Converse** blueprint is generic: the Converse API speaks one wire format for every chat model on Bedrock, so this single blueprint covers Amazon Nova, Anthropic Claude, Meta Llama, Mistral, AI21 Jurassic, Cohere, and OpenAI GPT-OSS on Bedrock. That is why we do not ship a separate blueprint per Bedrock model — use `bedrock_converse_blueprint.md` and set the `model` parameter. The dedicated `anthropic_claude_bedrock_blueprint.md` is kept because it uses Claude's native `invoke` API and documents the response shape behind earlier bug fixes.

Any other provider works too: write a blueprint that maps the neutral parameters to the provider's API and returns the model text in a field SRW can read (see [The neutral contract](#the-neutral-contract) below).

## Workflow

1. Pick the blueprint for the provider you want to use (or adapt one for a new provider).
2. Run its REST calls in order: create the connector, register and deploy the model, then run the test predict to confirm it returns a `response` field.
3. Take the `model_id` from the register step and pass it to the SRW judgment API:

```http
PUT _plugins/_search_relevance/judgments
{
  "name": "my-judgment",
  "type": "LLM_JUDGMENT",
  "modelId": "<model_id from step 2>",
  "querySetId": "<query set id>",
  "searchConfigurationList": ["<search config id>"],
  "size": 10
}
```

There is no `connectorType` field — SRW infers nothing about the provider, because the blueprint already speaks the provider's dialect.

## The neutral contract

SRW sends these parameters to ml-commons on every predict call:

| Parameter | Meaning |
|---|---|
| `system_prompt` | The rater system instruction. |
| `user_prompt` | The search query, hits, and any reference data. |
| `messages` | Legacy OpenAI-shaped `[{system}, {user}]` array. Emitted so already-deployed OpenAI connectors keep working without changes. |
| `response_format` | Legacy OpenAI JSON-schema structured-output spec. |

A blueprint references whichever of these it needs in its `request_body`. ml-commons only substitutes parameters the blueprint actually references, so the unused ones are harmless.

For the response, SRW reads the model text from the first shape it recognises:

1. a neutral top-level `response` string — **set this in every blueprint** via a `post_process_function`; this is the shape SRW relies on for all providers,
2. OpenAI `choices[0].message.content` — a fallback so an already-deployed OpenAI connector with no `post_process_function` keeps working.

If the model text arrives in any other shape (Claude's `content[0].text`, Gemini's `candidates[0].content.parts[0].text`, and so on), the blueprint's `post_process_function` is responsible for copying it into the neutral `response` field. Every blueprint in this directory does so, which is why no provider needs SRW-side code. If a blueprint returns an unrecognised shape, SRW fails that chunk with a clear error naming the keys it actually received.
