/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLAgenticMemoryIT extends MLCommonsRestTestCase {

    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String GITHUB_CI_AWS_REGION = "us-west-2";

    private static final String TEST_AGENT_ID = "test_agent_123";
    private static final String TEST_SESSION_ID = "test_session_456";

    private final String openaiConnectorEntity = "{\n"
        + "    \"name\": \"My openai connector: gpt-4o-mini\",\n"
        + "    \"description\": \"The connector to openai chat model\",\n"
        + "    \"version\": 1,\n"
        + "    \"protocol\": \"http\",\n"
        + "    \"parameters\": {\n"
        + "      \"model\": \"gpt-4o-mini\",\n"
        + "      \"response_filter\": \"$.choices[0].message.content\"\n"
        + "    },\n"
        + "    \"credential\": {\n"
        + "      \"openAI_key\": \""
        + OPENAI_KEY
        + "\"\n"
        + "    },\n"
        + "    \"actions\": [\n"
        + "      {\n"
        + "        \"action_type\": \"predict\",\n"
        + "        \"method\": \"POST\",\n"
        + "        \"url\": \"https://api.openai.com/v1/chat/completions\",\n"
        + "        \"headers\": {\n"
        + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
        + "        },\n"
        + "        \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": [{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"${parameters.system_prompt}\\\"},{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"${parameters.messages}\\\"}]}\"\n"
        + "      }\n"
        + "    ]\n"
        + "}";

    private final String claudeConnectorEntity = "{\n"
        + "    \"name\": \"Amazon Bedrock Connector: LLM\",\n"
        + "    \"description\": \"The connector to bedrock Claude 3.7 sonnet model\",\n"
        + "    \"version\": 1,\n"
        + "    \"protocol\": \"aws_sigv4\",\n"
        + "    \"parameters\": {\n"
        + "      \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "      \"service_name\": \"bedrock\",\n"
        + "      \"max_tokens\": 8000,\n"
        + "      \"temperature\": 1,\n"
        + "      \"anthropic_version\": \"bedrock-2023-05-31\",\n"
        + "      \"model\": \"us.anthropic.claude-3-7-sonnet-20250219-v1:0\",\n"
        + "      \"response_filter\": \"$.content[0].text\"\n"
        + "    },\n"
        + "    \"credential\": {\n"
        + "        \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "        \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "        \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "    },\n"
        + "    \"actions\": [{\n"
        + "      \"action_type\": \"predict\",\n"
        + "      \"method\": \"POST\",\n"
        + "      \"headers\": {\"content-type\": \"application/json\"},\n"
        + "      \"url\": \"https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke\",\n"
        + "      \"request_body\": \"{ \\\"system\\\": \\\"${parameters.system_prompt}\\\", \\\"anthropic_version\\\": \\\"${parameters.anthropic_version}\\\", \\\"max_tokens\\\": ${parameters.max_tokens}, \\\"temperature\\\": ${parameters.temperature}, \\\"messages\\\": [{\\\"role\\\":\\\"user\\\",\\\"content\\\":[{ \\\"type\\\": \\\"text\\\", \\\"text\\\":\\\"${parameters.messages}\\\"}]}]}\"\n"
        + "    }]\n"
        + "}";

    @Before
    public void setup() throws IOException, InterruptedException {
        // Enable agentic memory
        updateClusterSettings(ML_COMMONS_AGENTIC_MEMORY_ENABLED.getKey(), true);
    }

    @Test
    public void testCreateMemoryContainerAndAddMemories_openAI() throws IOException, InterruptedException {
        // Create OpenAI model and memory container
        String openaiModelId = registerLLMModel();
        String memoryContainerId = createMemoryContainerWithModel(
            "OpenAI Test Memory Container",
            "Store conversations with OpenAI model",
            openaiModelId
        );

        try {
            // Test adding memories with ADD operation
            String addMemoryRequest = "{\n"
                + "    \"messages\": [\n"
                + "        {\n"
                + "            \"role\": \"user\",\n"
                + "            \"content\": \"I like popcorn\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"role\": \"assistant\",\n"
                + "            \"content\": \"I like Indian food\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"agent_id\": \""
                + TEST_AGENT_ID
                + "\",\n"
                + "    \"session_id\": \""
                + TEST_SESSION_ID
                + "\"\n"
                + "}";

            Response response = addMemories(memoryContainerId, addMemoryRequest);
            String responseBody = TestHelper.httpEntityToString(response.getEntity());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

            // Verify response structure
            assertNotNull("Response should not be null", responseMap);
            assertTrue("Response should contain results", responseMap.containsKey("results"));
            assertTrue("Response should contain session_id", responseMap.containsKey("session_id"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");
            assertEquals("Should have 2 memories", 2, results.size());

            // Verify first memory (ADD event)
            Map<String, Object> firstMemory = results.get(0);
            assertTrue("First memory should contain id", firstMemory.containsKey("id"));
            assertTrue("First memory should contain text", firstMemory.containsKey("text"));
            assertEquals("First memory should be ADD event", "ADD", firstMemory.get("event"));
            assertTrue("First memory text should contain popcorn", firstMemory.get("text").toString().toLowerCase().contains("popcorn"));

            // Verify second memory (ADD event)
            Map<String, Object> secondMemory = results.get(1);
            assertTrue("Second memory should contain id", secondMemory.containsKey("id"));
            assertTrue("Second memory should contain text", secondMemory.containsKey("text"));
            assertEquals("Second memory should be ADD event", "ADD", secondMemory.get("event"));
            assertTrue(
                "Second memory text should contain indian food",
                secondMemory.get("text").toString().toLowerCase().contains("indian")
            );
        } finally {
            // Clean up OpenAI memory container and model
            deleteMemoryContainer(memoryContainerId);
            deleteModel(openaiModelId);
        }
    }

    @Test
    public void testUpdateMemoriesWithContradictoryInformation_openAI() throws IOException, InterruptedException {
        // Create OpenAI model and memory container
        String openaiModelId = registerLLMModel();
        String memoryContainerId = createMemoryContainerWithModel(
            "OpenAI Test Memory Container",
            "Store conversations with OpenAI model",
            openaiModelId
        );

        try {
            // First add initial memories
            String initialMemoryRequest = "{\n"
                + "    \"messages\": [\n"
                + "        {\n"
                + "            \"role\": \"user\",\n"
                + "            \"content\": \"I like popcorn\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"role\": \"assistant\",\n"
                + "            \"content\": \"I like Indian food\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"agent_id\": \""
                + TEST_AGENT_ID
                + "\",\n"
                + "    \"session_id\": \""
                + TEST_SESSION_ID
                + "\"\n"
                + "}";

            addMemories(memoryContainerId, initialMemoryRequest);

            // Now add contradictory information
            String updateMemoryRequest = "{\n"
                + "    \"messages\": [\n"
                + "        {\n"
                + "            \"role\": \"user\",\n"
                + "            \"content\": \"Actually I don't like popcorn\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"role\": \"assistant\",\n"
                + "            \"content\": \"I like Indian food\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"agent_id\": \""
                + TEST_AGENT_ID
                + "\",\n"
                + "    \"session_id\": \""
                + TEST_SESSION_ID
                + "\"\n"
                + "}";

            Response response = addMemories(memoryContainerId, updateMemoryRequest);
            String responseBody = TestHelper.httpEntityToString(response.getEntity());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");
            assertTrue("Should have at least one result", results.size() >= 1);

            // Verify that we get either UPDATE or DELETE events for contradictory information
            boolean hasUpdateOrDelete = results
                .stream()
                .anyMatch(result -> "UPDATE".equals(result.get("event")) || "DELETE".equals(result.get("event")));
            assertTrue("Should have UPDATE or DELETE event for contradictory information", hasUpdateOrDelete);
        } finally {
            // Clean up OpenAI memory container and model
            deleteMemoryContainer(memoryContainerId);
            deleteModel(openaiModelId);
        }
    }

    @Test
    public void testMemoryContainerLifecycle() throws IOException, InterruptedException {

        String memoryContainerId = createMemoryContainerWithModel(
            "OpenAI Test Memory Container",
            "Store conversations with OpenAI model",
            "TEST_MODEL_ID"
        );

        try {
            // Test getting memory container
            Response getResponse = getMemoryContainer(memoryContainerId);
            String getResponseBody = TestHelper.httpEntityToString(getResponse.getEntity());
            @SuppressWarnings("unchecked")
            Map<String, Object> getResponseMap = gson.fromJson(getResponseBody, Map.class);

            assertNotNull("Get container response should not be null", getResponseMap);
            assertTrue("Container should have name", getResponseMap.containsKey("name"));
            assertTrue("Container should have description", getResponseMap.containsKey("description"));
            assertTrue("Container should have created time", getResponseMap.containsKey("created_time"));
            assertTrue("Container should have last updated time", getResponseMap.containsKey("last_updated_time"));
            assertTrue("Container should have memory storage config", getResponseMap.containsKey("memory_storage_config"));
            assertTrue(
                "Container should have memory storage config",
                ((Map<String, Object>) getResponseMap.get("memory_storage_config")).containsKey("llm_model_id")
            );
            assertEquals(
                "llm_model_id should match",
                ((Map<String, Object>) getResponseMap.get("memory_storage_config")).get("llm_model_id"),
                "TEST_MODEL_ID"
            );

        } finally {
            // Clean up OpenAI memory container and model
            deleteMemoryContainer(memoryContainerId);
        }
    }

    @Test
    public void testCreateMemoryContainerAndAddMemories_claude() throws IOException, InterruptedException {
        if (awsCredentialsNotSet()) {
            return;
        }

        // Register Claude model and create memory container
        String claudeModelId = registerClaudeModel();
        String claudeMemoryContainerId = createMemoryContainerWithModel(
            "Claude Test Memory Container",
            "Store conversations with Claude model",
            claudeModelId
        );

        try {
            // Test adding memories with ADD operation using Claude
            String addMemoryRequest = "{\n"
                + "    \"messages\": [\n"
                + "        {\n"
                + "            \"role\": \"user\",\n"
                + "            \"content\": \"I like popcorn\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"role\": \"assistant\",\n"
                + "            \"content\": \"I like Indian food\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"agent_id\": \""
                + TEST_AGENT_ID
                + "\",\n"
                + "    \"session_id\": \""
                + TEST_SESSION_ID
                + "\"\n"
                + "}";

            Response response = addMemories(claudeMemoryContainerId, addMemoryRequest);
            String responseBody = TestHelper.httpEntityToString(response.getEntity());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

            // Verify response structure
            assertNotNull("Response should not be null", responseMap);
            assertTrue("Response should contain results", responseMap.containsKey("results"));
            assertTrue("Response should contain session_id", responseMap.containsKey("session_id"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");
            assertEquals("Should have 2 memories", 2, results.size());

            // Verify first memory (ADD event)
            Map<String, Object> firstMemory = results.get(0);
            assertTrue("First memory should contain id", firstMemory.containsKey("id"));
            assertTrue("First memory should contain text", firstMemory.containsKey("text"));
            assertEquals("First memory should be ADD event", "ADD", firstMemory.get("event"));
            assertTrue("First memory text should contain popcorn", firstMemory.get("text").toString().toLowerCase().contains("popcorn"));

            // Verify second memory (ADD event)
            Map<String, Object> secondMemory = results.get(1);
            assertTrue("Second memory should contain id", secondMemory.containsKey("id"));
            assertTrue("Second memory should contain text", secondMemory.containsKey("text"));
            assertEquals("Second memory should be ADD event", "ADD", secondMemory.get("event"));
            assertTrue(
                "Second memory text should contain indian food",
                secondMemory.get("text").toString().toLowerCase().contains("indian")
            );
        } finally {
            // Clean up Claude memory container and model
            deleteMemoryContainer(claudeMemoryContainerId);
            deleteModel(claudeModelId);
        }
    }

    @Test
    public void testUpdateMemoriesWithContradictoryInformation_claude() throws IOException, InterruptedException {
        if (awsCredentialsNotSet()) {
            return;
        }

        // Register Claude model and create memory container
        String claudeModelId = registerClaudeModel();
        String claudeMemoryContainerId = createMemoryContainerWithModel(
            "Claude Test Memory Container",
            "Store conversations with Claude model",
            claudeModelId
        );

        try {
            // First add initial memories
            String initialMemoryRequest = "{\n"
                + "    \"messages\": [\n"
                + "        {\n"
                + "            \"role\": \"user\",\n"
                + "            \"content\": \"I like popcorn\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"role\": \"assistant\",\n"
                + "            \"content\": \"I like Indian food\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"agent_id\": \""
                + TEST_AGENT_ID
                + "\",\n"
                + "    \"session_id\": \""
                + TEST_SESSION_ID
                + "\"\n"
                + "}";

            addMemories(claudeMemoryContainerId, initialMemoryRequest);

            // Now add contradictory information
            String updateMemoryRequest = "{\n"
                + "    \"messages\": [\n"
                + "        {\n"
                + "            \"role\": \"user\",\n"
                + "            \"content\": \"Actually I don't like popcorn\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"role\": \"assistant\",\n"
                + "            \"content\": \"I like Indian food\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"agent_id\": \""
                + TEST_AGENT_ID
                + "\",\n"
                + "    \"session_id\": \""
                + TEST_SESSION_ID
                + "\"\n"
                + "}";

            Response response = addMemories(claudeMemoryContainerId, updateMemoryRequest);
            String responseBody = TestHelper.httpEntityToString(response.getEntity());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");
            assertTrue("Should have at least one result", results.size() >= 1);

            // Verify that we get either UPDATE or DELETE events for contradictory information
            boolean hasUpdateOrDelete = results
                .stream()
                .anyMatch(result -> "UPDATE".equals(result.get("event")) || "DELETE".equals(result.get("event")));
            assertTrue("Should have UPDATE or DELETE event for contradictory information", hasUpdateOrDelete);
        } finally {
            // Clean up Claude memory container and model
            deleteMemoryContainer(claudeMemoryContainerId);
            deleteModel(claudeModelId);
        }
    }

    private String registerLLMModel() throws IOException, InterruptedException {
        String openaiModelName = "openai gpt-4o-mini model " + randomAlphaOfLength(5);
        return registerRemoteModel(openaiConnectorEntity, openaiModelName, true);
    }

    private String createMemoryContainerWithModel(String name, String description, String modelId) throws IOException {
        String createRequest = "{\n"
            + "    \"name\": \""
            + name
            + "\",\n"
            + "    \"description\": \""
            + description
            + "\",\n"
            + "    \"memory_storage_config\": {\n"
            + "        \"llm_model_id\": \""
            + modelId
            + "\"\n"
            + "    }\n"
            + "}";

        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/memory_containers/_create",
                null,
                new StringEntity(createRequest),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        String responseBody = TestHelper.httpEntityToString(response.getEntity());
        @SuppressWarnings("unchecked")
        Map<String, String> responseMap = gson.fromJson(responseBody, Map.class);
        return responseMap.get("memory_container_id");
    }

    private Response addMemories(String containerId, String requestBody) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/memory_containers/" + containerId + "/memories",
                null,
                new StringEntity(requestBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    private Response getMemoryContainer(String containerId) throws IOException {
        return TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/memory_containers/" + containerId, null, "", List.of());
    }

    private void deleteMemoryContainer(String containerId) throws IOException {
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/memory_containers/" + containerId, null, "", List.of());
    }

    private String registerClaudeModel() throws IOException, InterruptedException {
        String claudeModelName = "claude model " + randomAlphaOfLength(5);
        return registerRemoteModel(claudeConnectorEntity, claudeModelName, true);
    }

    private boolean awsCredentialsNotSet() {
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            log.info("#### The AWS credentials are not set. Skipping Claude tests. ####");
            return true;
        }
        return false;
    }

    private void deleteModel(String modelId) throws IOException {
        try {
            // First try to undeploy the model
            TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_undeploy", null, "", List.of());
        } catch (Exception e) {
            log.info("Model {} might not be deployed, continuing with deletion", modelId);
        }

        try {
            // Then delete the model
            log.info("Deleting model: {}", modelId);
            TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/models/" + modelId, null, "", List.of());
        } catch (Exception e) {
            log.warn("Failed to delete model: {}", modelId, e);
        }
    }
}
