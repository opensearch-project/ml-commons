/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.rest;

import static org.opensearch.ml.rest.RestMLRemoteInferenceIT.createConnector;
import static org.opensearch.ml.rest.RestMLRemoteInferenceIT.deployRemoteModel;
import static org.opensearch.ml.utils.TestHelper.makeRequest;
import static org.opensearch.ml.utils.TestHelper.toHttpEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.searchpipelines.questionanswering.generative.llm.LlmIOUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RestMLRAGSearchProcessorIT extends MLCommonsRestTestCase {

    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private static final String OPENAI_CONNECTOR_BLUEPRINT = "{\n"
        + "    \"name\": \"OpenAI Chat Connector\",\n"
        + "    \"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
        + "    \"version\": 2,\n"
        + "    \"protocol\": \"http\",\n"
        + "    \"parameters\": {\n"
        + "        \"endpoint\": \"api.openai.com\",\n"
        + "        \"model\": \"gpt-3.5-turbo\",\n"
        + "        \"temperature\": 0\n"
        + "    },\n"
        + "    \"credential\": {\n"
        + "        \"openAI_key\": \""
        + OPENAI_KEY
        + "\"\n"
        + "    },\n"
        + "    \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"url\": \"https://${parameters.endpoint}/v1/chat/completions\",\n"
        + "            \"headers\": {\n"
        + "                \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
        + "            },\n"
        + "            \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages}, \\\"temperature\\\": ${parameters.temperature} }\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private static final String OPENAI_4o_CONNECTOR_BLUEPRINT = "{\n"
        + "    \"name\": \"OpenAI Chat Connector\",\n"
        + "    \"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
        + "    \"version\": 2,\n"
        + "    \"protocol\": \"http\",\n"
        + "    \"parameters\": {\n"
        + "        \"endpoint\": \"api.openai.com\",\n"
        + "        \"model\": \"gpt-4o-mini\",\n"
        + "        \"temperature\": 0\n"
        + "    },\n"
        + "    \"credential\": {\n"
        + "        \"openAI_key\": \""
        + OPENAI_KEY
        + "\"\n"
        + "    },\n"
        + "    \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"url\": \"https://${parameters.endpoint}/v1/chat/completions\",\n"
        + "            \"headers\": {\n"
        + "                \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
        + "            },\n"
        + "            \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages}, \\\"temperature\\\": ${parameters.temperature} , \\\"max_tokens\\\": 300 }\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String GITHUB_CI_AWS_REGION = "us-west-2";

    private static final String BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET = "anthropic.claude-3-5-sonnet-20240620-v1:0";
    private static final String BEDROCK_ANTHROPIC_CLAUDE_3_SONNET = "anthropic.claude-3-sonnet-20240229-v1:0";

    private static final String BEDROCK_CONNECTOR_BLUEPRINT_INVOKE = "{\n"
        + "  \"name\": \"Bedrock Connector: claude 3.5\",\n"
        + "  \"description\": \"The connector to bedrock claude 3.5 model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\",\n"
        + "    \"model\": \""
        + "anthropic.claude-3-5-sonnet-20240620-v1:0"
        + "\",\n"
        + "    \"system_prompt\": \"You are a helpful assistant.\",\n"
        + "\"response_filter\": \"$.content[0].text\""
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \""
        + "predict"
        + "\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"headers\": {\n"
        + "                \"content-type\": \"application/json\"\n"
        + "            },\n"
        + "            \"url\": \"https://bedrock-runtime."
        + GITHUB_CI_AWS_REGION
        + ".amazonaws.com/model/"
        + "anthropic.claude-3-5-sonnet-20240620-v1:0"
        + "/invoke\",\n"
        + "           \"request_body\": \"{\\\"messages\\\":[{\\\"role\\\": \\\"user\\\", \\\"content\\\":[ {\\\"type\\\": \\\"text\\\", \\\"text\\\":\\\"${parameters.inputs}\\\"}]}], \\\"max_tokens\\\":300, \\\"temperature\\\":0.5,  \\\"anthropic_version\\\":\\\"bedrock-2023-05-31\\\" }\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private static final String BEDROCK_CONNECTOR_BLUEPRINT1 = "{\n"
        + "  \"name\": \"Bedrock Connector: claude2\",\n"
        + "  \"description\": \"The connector to bedrock claude2 model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"headers\": {\n"
        + "                \"content-type\": \"application/json\"\n"
        + "            },\n"
        + "            \"url\": \"https://bedrock-runtime."
        + GITHUB_CI_AWS_REGION
        + ".amazonaws.com/model/anthropic.claude-v2/invoke\",\n"
        + "            \"request_body\": \"{\\\"prompt\\\":\\\"\\\\n\\\\nHuman: ${parameters.inputs}\\\\n\\\\nAssistant:\\\",\\\"max_tokens_to_sample\\\":300,\\\"temperature\\\":0.5,\\\"top_k\\\":250,\\\"top_p\\\":1,\\\"stop_sequences\\\":[\\\"\\\\\\\\n\\\\\\\\nHuman:\\\"]}\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";
    private static final String BEDROCK_CONNECTOR_BLUEPRINT2 = "{\n"
        + "  \"name\": \"Bedrock Connector: claude2\",\n"
        + "  \"description\": \"The connector to bedrock claude2 model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"headers\": {\n"
        + "                \"content-type\": \"application/json\"\n"
        + "            },\n"
        + "            \"url\": \"https://bedrock-runtime."
        + GITHUB_CI_AWS_REGION
        + ".amazonaws.com/model/anthropic.claude-v2/invoke\",\n"
        + "            \"request_body\": \"{\\\"prompt\\\":\\\"\\\\n\\\\nHuman: ${parameters.inputs}\\\\n\\\\nAssistant:\\\",\\\"max_tokens_to_sample\\\":300,\\\"temperature\\\":0.5,\\\"top_k\\\":250,\\\"top_p\\\":1,\\\"stop_sequences\\\":[\\\"\\\\\\\\n\\\\\\\\nHuman:\\\"]}\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    static final String BEDROCK_CONVERSE_CONNECTOR_BLUEPRINT2 = "{\n"
        + "  \"name\": \"Bedrock Connector: claude 3.5\",\n"
        + "  \"description\": \"The connector to bedrock claude 3.5 model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\",\n"
        + "    \"model\": \""
        + BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET
        + "\",\n"
        + "    \"system_prompt\": \"You are a helpful assistant.\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"headers\": {\n"
        + "                \"content-type\": \"application/json\"\n"
        + "            },\n"
        + "            \"url\": \"https://bedrock-runtime."
        + GITHUB_CI_AWS_REGION
        + ".amazonaws.com/model/"
        + BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET
        + "/converse\",\n"
        + "            \"request_body\": \"{ \\\"system\\\": [{\\\"text\\\": \\\"you are a helpful assistant.\\\"}], \\\"messages\\\": ${parameters.messages} , \\\"inferenceConfig\\\": {\\\"temperature\\\": 0.0, \\\"topP\\\": 0.9, \\\"maxTokens\\\": 1000} }\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private static final String BEDROCK_DOCUMENT_CONVERSE_CONNECTOR_BLUEPRINT2 = "{\n"
        + "  \"name\": \"Bedrock Connector: claude 3\",\n"
        + "  \"description\": \"The connector to bedrock claude 3 model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\",\n"
        + "    \"model\": \""
        + BEDROCK_ANTHROPIC_CLAUDE_3_SONNET
        + "\",\n"
        + "    \"system_prompt\": \"You are a helpful assistant.\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"headers\": {\n"
        + "                \"content-type\": \"application/json\"\n"
        + "            },\n"
        + "            \"url\": \"https://bedrock-runtime."
        + GITHUB_CI_AWS_REGION
        + ".amazonaws.com/model/"
        + BEDROCK_ANTHROPIC_CLAUDE_3_SONNET
        + "/converse\",\n"
        + "            \"request_body\": \"{ \\\"messages\\\": ${parameters.messages} , \\\"inferenceConfig\\\": {\\\"temperature\\\": 0.0, \\\"topP\\\": 0.9, \\\"maxTokens\\\": 1000} }\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private static final String BEDROCK_CONNECTOR_BLUEPRINT = AWS_SESSION_TOKEN == null
        ? BEDROCK_CONNECTOR_BLUEPRINT_INVOKE
        : BEDROCK_CONNECTOR_BLUEPRINT_INVOKE;

    private static final String BEDROCK_CONVERSE_CONNECTOR_BLUEPRINT = AWS_SESSION_TOKEN == null
        ? BEDROCK_CONVERSE_CONNECTOR_BLUEPRINT2
        : BEDROCK_CONVERSE_CONNECTOR_BLUEPRINT2;

    private static final String COHERE_KEY = System.getenv("COHERE_KEY");
    private static final String COHERE_CONNECTOR_BLUEPRINT = "{\n"
        + "    \"name\": \"Cohere Chat Model\",\n"
        + "    \"description\": \"The connector to Cohere's public chat API\",\n"
        + "    \"version\": \"1\",\n"
        + "    \"protocol\": \"http\",\n"
        + "    \"credential\": {\n"
        + "        \"cohere_key\": \""
        + COHERE_KEY
        + "\"\n"
        + "    },\n"
        + "    \"parameters\": {\n"
        + "        \"model\": \"command-a-03-2025\"\n"
        + "    },\n"
        + "    \"client_config\": {\n"
        + "        \"read_timeout\": 60\n"
        + "    },\n"
        + "    \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \"predict\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"url\": \"https://api.cohere.ai/v1/chat\",\n"
        + "            \"headers\": {\n"
        + "                \"Authorization\": \"Bearer ${credential.cohere_key}\",\n"
        + "                \"Request-Source\": \"unspecified:opensearch\"\n"
        + "            },\n"
        + "            \"request_body\": \"{ \\\"message\\\": \\\"${parameters.inputs}\\\", \\\"model\\\": \\\"${parameters.model}\\\" }\" \n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private static final String PIPELINE_TEMPLATE = "{\n"
        + "  \"response_processors\": [\n"
        + "    {\n"
        + "      \"retrieval_augmented_generation\": {\n"
        + "        \"tag\": \"%s\",\n"
        + "        \"description\": \"%s\",\n"
        + "        \"model_id\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_field_list\": [\"%s\"]\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    // In some cases, we do not want a system prompt to be sent to an LLM.
    private static final String PIPELINE_TEMPLATE2 = "{\n"
        + "  \"response_processors\": [\n"
        + "    {\n"
        + "      \"retrieval_augmented_generation\": {\n"
        + "        \"tag\": \"%s\",\n"
        + "        \"description\": \"%s\",\n"
        + "        \"model_id\": \"%s\",\n"
        // + " \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_field_list\": [\"%s\"]\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"llm_question\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_IMAGE_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d,\n"
        + "        \"llm_messages\": [{ \"role\": \"user\", \"content\": [{\"type\": \"text\", \"text\": \"%s\"}, {\"image\": {\"format\": \"%s\", \"%s\": \"%s\"}}] }]\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_DOCUMENT_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d,\n"
        + "        \"llm_messages\": [{ \"role\": \"user\", \"content\": [{\"type\": \"text\", \"text\": \"%s\"}, {\"document\": {\"format\": \"%s\", \"name\": \"%s\", \"data\": \"%s\"}}] }]\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_IMAGE_AND_DOCUMENT_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"llm_question\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d,\n"
        + "        \"llm_messages\": [{ \"role\": \"user\", \"content\": [{\"type\": \"text\", \"text\": \"%s\"}, {\"image\": {\"format\": \"%s\", \"%s\": \"%s\"}} , {\"document\": {\"format\": \"%s\", \"name\": \"%s\", \"data\": \"%s\"}}] }]\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_CONVO_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"llm_question\": \"%s\",\n"
        + "        \"memory_id\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_CONVO_WITH_LLM_RESPONSE_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"llm_question\": \"%s\",\n"
        + "        \"memory_id\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d,\n"
        + "        \"llm_response_field\": \"%s\"\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_CONVO_AND_IMAGE_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"llm_question\": \"%s\",\n"
        + "        \"memory_id\": \"%s\",\n"
        + "        \"system_prompt\": \"%s\",\n"
        + "        \"user_instructions\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d,\n"
        + "        \"llm_messages\": [{ \"role\": \"user\", \"content\": [{\"type\": \"text\", \"text\": \"%s\"}, {\"image\": {\"format\": \"%s\", \"%s\": \"%s\"}}] }]\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_LLM_RESPONSE_FIELD_TEMPLATE = "{\n"
        + "  \"_source\": [\"%s\"],\n"
        + "  \"query\" : {\n"
        + "    \"match\": {\"%s\": \"%s\"}\n"
        + "  },\n"
        + "   \"ext\": {\n"
        + "      \"generative_qa_parameters\": {\n"
        + "        \"llm_model\": \"%s\",\n"
        + "        \"llm_question\": \"%s\",\n"
        + "        \"context_size\": %d,\n"
        + "        \"message_size\": %d,\n"
        + "        \"timeout\": %d,\n"
        + "        \"llm_response_field\": \"%s\"\n"
        + "      }\n"
        + "  }\n"
        + "}";

    private static final String OPENAI_MODEL = "gpt-3.5-turbo";
    private static final String OPENAI_40_MODEL = "gpt-4o-mini";
    private static final String BEDROCK_ANTHROPIC_CLAUDE = "bedrock/anthropic-claude";
    private static final String BEDROCK_CONVERSE_ANTHROPIC_CLAUDE = "bedrock-converse/" + BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET;
    private static final String BEDROCK_CONVERSE_ANTHROPIC_CLAUDE_3 = "bedrock-converse/" + BEDROCK_ANTHROPIC_CLAUDE_3_SONNET;
    private static final String TEST_DOC_PATH = "org/opensearch/ml/rest/test_data/";
    private static Set<String> testDocs = Set.of("qa_doc1.json", "qa_doc2.json", "qa_doc3.json");
    private static final String DEFAULT_USER_AGENT = "Kibana";
    protected ClassLoader classLoader = RestMLRAGSearchProcessorIT.class.getClassLoader();
    private static final String INDEX_NAME = "test";

    private static final String ML_RAG_REMOTE_MODEL_GROUP = "rag_remote_model_group";

    // "client" gets initialized by the test framework at the instance level
    // so we perform this per test case, not via @BeforeClass.
    @Before
    public void init() throws Exception {

        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        // TODO Do we really need to wait this long? This adds 20s to every test case run.
        // Can we instead check the cluster state and move on?
        Thread.sleep(20000);

        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\n"
                    + "        \"plugins.ml_commons.trusted_connector_endpoints_regex\": [\n"
                    + "            \"^https://runtime\\\\.sagemaker\\\\..*\\\\.amazonaws\\\\.com/.*$\",\n"
                    + "            \"^https://api\\\\.openai\\\\.com/.*$\",\n"
                    + "            \"^https://api\\\\.cohere\\\\.ai/.*$\",\n"
                    + "            \"^https://bedrock.*\\\\.amazonaws.com/.*$\"\n"
                    + "        ]\n"
                    + "    }}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

        for (String doc : testDocs) {
            String requestBody = Files.readString(Path.of(classLoader.getResource(TEST_DOC_PATH + doc).toURI()));
            index(INDEX_NAME, requestBody);
        }
    }

    void index(String indexName, String requestBody) throws Exception {
        makeRequest(
            client(),
            "POST",
            indexName + "/_doc",
            null,
            toHttpEntity(requestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Response statsResponse = makeRequest(client(), "GET", indexName, ImmutableMap.of(), "", null);
        assertEquals(RestStatus.OK, TestHelper.restStatus(statsResponse));
        String result = EntityUtils.toString(statsResponse.getEntity());
        assertTrue(result.contains(indexName));
    }

    public void testBM25WithOpenAI() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(OPENAI_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAI";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_MODEL;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.systemPrompt = "You are great at answering questions";
        requestParameters.userInstructions = "Follow my instructions as best you can";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithOpenAIWithImage() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(OPENAI_4o_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "openAI-GPT-4o-mini completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAIWithImage";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        byte[] rawImage = FileUtils
            .readFileToByteArray(Path.of(classLoader.getResource(TEST_DOC_PATH + "openai_boardwalk.jpg").toURI()).toFile());
        String imageContent = Base64.getEncoder().encodeToString(rawImage);

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_40_MODEL;
        requestParameters.llmQuestion = "what is this image";
        requestParameters.systemPrompt = "You are great at answering questions";
        requestParameters.userInstructions = "Follow my instructions as best you can";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.imageFormat = "jpeg";
        requestParameters.imageType = "data";
        requestParameters.imageData = imageContent;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);

        requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_40_MODEL;
        requestParameters.llmQuestion = "what is this image";
        requestParameters.systemPrompt = "You are great at answering questions";
        requestParameters.userInstructions = "Follow my instructions as best you can";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.imageFormat = "jpeg";
        requestParameters.imageType = "url";
        requestParameters.imageData = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800";
        Response response3 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap3 = parseResponseToMap(response3);
        ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithBedrock() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        Response response = createConnector(BEDROCK_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Bedrock Anthropic Claude", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithBedrock";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_ANTHROPIC_CLAUDE;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.llmResponseField = "response";
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithBedrockConverse() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            System.out.println("Skipping testBM25WithBedrockConverse because AWS_ACCESS_KEY_ID is null");
            return;
        }

        System.out.println("Running testBM25WithBedrockConverse");

        Response response = createConnector(BEDROCK_CONVERSE_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Bedrock Anthropic Claude", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithBedrockConverse";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_CONVERSE_ANTHROPIC_CLAUDE;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithBedrockConverseUsingLlmMessages() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            System.out.println("Skipping testBM25WithBedrockConverseUsingLlmMessages because AWS_ACCESS_KEY_ID is null");
            return;
        }
        System.out.println("Running testBM25WithBedrockConverseUsingLlmMessages");

        Response response = createConnector(BEDROCK_CONVERSE_CONNECTOR_BLUEPRINT2);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Bedrock Anthropic Claude", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithBedrockConverseUsingLlmMessages";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        byte[] rawImage = FileUtils
            .readFileToByteArray(Path.of(classLoader.getResource(TEST_DOC_PATH + "openai_boardwalk.jpg").toURI()).toFile());
        String imageContent = Base64.getEncoder().encodeToString(rawImage);

        SearchRequestParameters requestParameters = new SearchRequestParameters();

        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_CONVERSE_ANTHROPIC_CLAUDE;
        requestParameters.llmQuestion = "describe the image and answer the question: would lincoln have liked this place";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.imageFormat = "jpeg";
        requestParameters.imageType = "data"; // Bedrock does not support URLs
        requestParameters.imageData = imageContent;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithBedrockConverseUsingLlmMessagesForDocumentChat() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            System.out.println("Skipping testBM25WithBedrockConverseUsingLlmMessagesForDocumentChat because AWS_ACCESS_KEY_ID is null");
            return;
        }

        System.out.println("Running testBM25WithBedrockConverseUsingLlmMessagesForDocumentChat");
        Response response = createConnector(BEDROCK_DOCUMENT_CONVERSE_CONNECTOR_BLUEPRINT2);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Bedrock Anthropic Claude", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithBedrockConverseUsingLlmMessagesForDocumentChat";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        // pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline2("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        byte[] docBytes = FileUtils.readFileToByteArray(Path.of(classLoader.getResource(TEST_DOC_PATH + "lincoln.pdf").toURI()).toFile());
        String docContent = Base64.getEncoder().encodeToString(docBytes);

        SearchRequestParameters requestParameters;
        requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_CONVERSE_ANTHROPIC_CLAUDE_3;
        requestParameters.llmQuestion = "use the information from the attached document to tell me something interesting about lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.documentFormat = "pdf";
        requestParameters.documentName = "lincoln";
        requestParameters.documentData = docContent;
        Response response3 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response3.getStatusLine().getStatusCode());

        Map responseMap3 = parseResponseToMap(response3);
        Map ext = (Map) responseMap3.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithOpenAIWithConversation() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            System.out.println("Skipping testBM25WithOpenAIWithConversation because OPENAI_KEY is null");
            return;
        }
        System.out.println("Running testBM25WithOpenAIWithConversation");

        Response response = createConnector(OPENAI_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAIWithConversation";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        String conversationId = createConversation("test_convo_1");
        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_MODEL;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.conversationId = conversationId;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);

        String interactionId = (String) rag.get("message_id");
        assertNotNull(interactionId);
    }

    public void testBM25WithOpenAIWithConversationAndImage() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            System.out.println("Skipping testBM25WithOpenAIWithConversationAndImage because OPENAI_KEY is null");
            return;
        }
        System.out.println("Running testBM25WithOpenAIWithConversationAndImage");

        Response response = createConnector(OPENAI_4o_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "openAI-GPT-4 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAIWithConversationAndImage";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        String conversationId = createConversation("test_convo_1");
        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_40_MODEL;
        requestParameters.llmQuestion = "describe the image and answer the question: can you picture lincoln enjoying himself there";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.conversationId = conversationId;
        requestParameters.imageFormat = "jpeg";
        requestParameters.imageType = "url";
        requestParameters.imageData = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800";
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);

        String interactionId = (String) rag.get("message_id");
        assertNotNull(interactionId);
    }

    public void testBM25WithBedrockWithConversation() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        Response response = createConnector(BEDROCK_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Bedrock", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithBedrockWithConversation";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        String conversationId = createConversation("test_convo_1");
        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_ANTHROPIC_CLAUDE;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.conversationId = conversationId;
        requestParameters.llmResponseField = "response";
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);

        String interactionId = (String) rag.get("message_id");
        assertNotNull(interactionId);
    }

    public void testBM25WithCohere() throws Exception {
        // Skip test if key is null
        if (COHERE_KEY == null) {
            return;
        }
        Response response = createConnector(COHERE_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Cohere Chat Completion v1", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithCohere";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = LlmIOUtil.COHERE_PROVIDER_PREFIX + "command";
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithCohereUsingLlmResponseField() throws Exception {
        // Skip test if key is null
        if (COHERE_KEY == null) {
            return;
        }
        Response response = createConnector(COHERE_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel(ML_RAG_REMOTE_MODEL_GROUP, "Cohere Chat Completion v1", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithCohereUsingLlmResponseField";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = "command";
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.llmResponseField = "text";
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    private Response createSearchPipeline(String pipeline, PipelineParameters parameters) throws Exception {
        return makeRequest(
            client(),
            "PUT",
            String.format(Locale.ROOT, "/_search/pipeline/%s", pipeline),
            null,
            toHttpEntity(
                String
                    .format(
                        Locale.ROOT,
                        PIPELINE_TEMPLATE,
                        parameters.tag,
                        parameters.description,
                        parameters.modelId,
                        parameters.systemPrompt,
                        parameters.userInstructions,
                        parameters.context_field
                    )
            ),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
    }

    // No system prompt
    private Response createSearchPipeline2(String pipeline, PipelineParameters parameters) throws Exception {
        return makeRequest(
            client(),
            "PUT",
            String.format(Locale.ROOT, "/_search/pipeline/%s", pipeline),
            null,
            toHttpEntity(
                String
                    .format(
                        Locale.ROOT,
                        PIPELINE_TEMPLATE2,
                        parameters.tag,
                        parameters.description,
                        parameters.modelId,
                        parameters.userInstructions,
                        parameters.context_field
                    )
            ),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
    }

    private Response performSearch(String indexName, String pipeline, int size, SearchRequestParameters requestParameters)
        throws Exception {

        // TODO build these templates dynamically
        String httpEntity = requestParameters.llmResponseField != null && requestParameters.conversationId == null
            ? String
                .format(
                    Locale.ROOT,
                    BM25_SEARCH_REQUEST_WITH_LLM_RESPONSE_FIELD_TEMPLATE,
                    requestParameters.source,
                    requestParameters.source,
                    requestParameters.match,
                    requestParameters.llmModel,
                    requestParameters.llmQuestion,
                    requestParameters.contextSize,
                    requestParameters.interactionSize,
                    requestParameters.timeout,
                    requestParameters.llmResponseField
                )
            : (requestParameters.documentData != null && requestParameters.imageType != null)
                ? String
                    .format(
                        Locale.ROOT,
                        BM25_SEARCH_REQUEST_WITH_IMAGE_AND_DOCUMENT_TEMPLATE,
                        requestParameters.source,
                        requestParameters.source,
                        requestParameters.match,
                        requestParameters.llmModel,
                        requestParameters.systemPrompt,
                        requestParameters.userInstructions,
                        requestParameters.contextSize,
                        requestParameters.interactionSize,
                        requestParameters.timeout,
                        requestParameters.llmQuestion,
                        requestParameters.imageFormat,
                        requestParameters.imageType,
                        requestParameters.imageData,
                        requestParameters.documentFormat,
                        requestParameters.documentName,
                        requestParameters.documentData
                    )
            : (requestParameters.documentData != null)
                ? String
                    .format(
                        Locale.ROOT,
                        BM25_SEARCH_REQUEST_WITH_DOCUMENT_TEMPLATE,
                        requestParameters.source,
                        requestParameters.source,
                        requestParameters.match,
                        requestParameters.llmModel,
                        requestParameters.userInstructions,
                        requestParameters.contextSize,
                        requestParameters.interactionSize,
                        requestParameters.timeout,
                        requestParameters.llmQuestion,
                        requestParameters.documentFormat,
                        requestParameters.documentName,
                        requestParameters.documentData
                    )
            : (requestParameters.conversationId != null && requestParameters.imageType != null)
                ? String
                    .format(
                        Locale.ROOT,
                        BM25_SEARCH_REQUEST_WITH_CONVO_AND_IMAGE_TEMPLATE,
                        requestParameters.source,
                        requestParameters.source,
                        requestParameters.match,
                        requestParameters.llmModel,
                        requestParameters.llmQuestion,
                        requestParameters.conversationId,
                        requestParameters.systemPrompt,
                        requestParameters.userInstructions,
                        requestParameters.contextSize,
                        requestParameters.interactionSize,
                        requestParameters.timeout,
                        requestParameters.llmQuestion,
                        requestParameters.imageFormat,
                        requestParameters.imageType,
                        requestParameters.imageData
                    )
            : (requestParameters.imageType != null)
                ? String
                    .format(
                        Locale.ROOT,
                        BM25_SEARCH_REQUEST_WITH_IMAGE_TEMPLATE,
                        requestParameters.source,
                        requestParameters.source,
                        requestParameters.match,
                        requestParameters.llmModel,
                        requestParameters.systemPrompt,
                        requestParameters.userInstructions,
                        requestParameters.contextSize,
                        requestParameters.interactionSize,
                        requestParameters.timeout,
                        requestParameters.llmQuestion,
                        requestParameters.imageFormat,
                        requestParameters.imageType,
                        requestParameters.imageData
                    )
            : (requestParameters.conversationId == null)
                ? String
                    .format(
                        Locale.ROOT,
                        BM25_SEARCH_REQUEST_TEMPLATE,
                        requestParameters.source,
                        requestParameters.source,
                        requestParameters.match,
                        requestParameters.llmModel,
                        requestParameters.llmQuestion,
                        requestParameters.systemPrompt,
                        requestParameters.userInstructions,
                        requestParameters.contextSize,
                        requestParameters.interactionSize,
                        requestParameters.timeout
                    )
            : (requestParameters.llmResponseField == null)
                ? String
                    .format(
                        Locale.ROOT,
                        BM25_SEARCH_REQUEST_WITH_CONVO_TEMPLATE,
                        requestParameters.source,
                        requestParameters.source,
                        requestParameters.match,
                        requestParameters.llmModel,
                        requestParameters.llmQuestion,
                        requestParameters.conversationId,
                        requestParameters.systemPrompt,
                        requestParameters.userInstructions,
                        requestParameters.contextSize,
                        requestParameters.interactionSize,
                        requestParameters.timeout
                    )
            : String
                .format(
                    Locale.ROOT,
                    BM25_SEARCH_REQUEST_WITH_CONVO_WITH_LLM_RESPONSE_TEMPLATE,
                    requestParameters.source,
                    requestParameters.source,
                    requestParameters.match,
                    requestParameters.llmModel,
                    requestParameters.llmQuestion,
                    requestParameters.conversationId,
                    requestParameters.systemPrompt,
                    requestParameters.userInstructions,
                    requestParameters.contextSize,
                    requestParameters.interactionSize,
                    requestParameters.timeout,
                    requestParameters.llmResponseField
                );
        return makeRequest(
            client(),
            "POST",
            String.format(Locale.ROOT, "/%s/_search", indexName),
            Map.of("search_pipeline", pipeline, "size", String.valueOf(size)),
            toHttpEntity(httpEntity),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
    }

    private String createConversation(String name) throws Exception {
        Response response = makeRequest(
            client(),
            "POST",
            "/_plugins/_ml/memory",
            null,
            toHttpEntity(String.format(Locale.ROOT, "{\"name\": \"%s\"}", name)),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        return (String) ((Map) parseResponseToMap(response)).get("memory_id");
    }

    static class PipelineParameters {
        String tag;
        String description;
        String modelId;
        String systemPrompt;
        String userInstructions;
        String context_field;
    }

    static class SearchRequestParameters {
        String source;
        String match;
        String llmModel;
        String llmQuestion;
        String systemPrompt;
        String userInstructions;
        int contextSize;
        int interactionSize;
        int timeout;
        String conversationId;

        String llmResponseField;
        String imageFormat;
        String imageType;
        String imageData;
        String documentFormat;
        String documentName;
        String documentData;
    }
}
