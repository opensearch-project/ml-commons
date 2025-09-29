/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;

public class AGUIInputConverterTest {

    @Test
    public void testIsAGUIInput_ValidAGUIFormat() {
        String aguiInput = "{\"threadId\":\"thread_123\",\"runId\":\"run_456\",\"messages\":[],\"tools\":[]}";
        assertTrue("Should detect valid AG-UI input format", AGUIInputConverter.isAGUIInput(aguiInput));
    }

    @Test
    public void testIsAGUIInput_InvalidFormat() {
        String standardInput = "{\"question\":\"What is the weather?\"}";
        assertFalse("Should not detect standard ML-Commons input as AG-UI format",
                   AGUIInputConverter.isAGUIInput(standardInput));
    }

    @Test
    public void testIsAGUIInput_MalformedJSON() {
        String malformedInput = "{invalid json";
        assertFalse("Should handle malformed JSON gracefully", AGUIInputConverter.isAGUIInput(malformedInput));
    }

    @Test
    public void testConvertFromAGUIInput_BasicConversion() {
        String aguiInput = "{\n" +
            "  \"threadId\": \"thread_123\",\n" +
            "  \"runId\": \"run_456\",\n" +
            "  \"state\": {\"status\": \"active\"},\n" +
            "  \"messages\": [\n" +
            "    {\"id\": \"msg_1\", \"role\": \"user\", \"content\": \"Hello world\"}\n" +
            "  ],\n" +
            "  \"tools\": [\n" +
            "    {\"name\": \"search\", \"description\": \"Search tool\"}\n" +
            "  ],\n" +
            "  \"context\": [],\n" +
            "  \"forwardedProps\": {}\n" +
            "}";

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(
            aguiInput, "test_agent_id", "test_tenant", false);

        assertNotNull("Converted result should not be null", result);
        assertEquals("Agent ID should be set correctly", "test_agent_id", result.getAgentId());
        assertEquals("Tenant ID should be set correctly", "test_tenant", result.getTenantId());
        assertFalse("Async flag should be set correctly", result.getIsAsync());

        // Check that input dataset contains AG-UI parameters
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertNotNull("Input dataset should not be null", inputDataSet);
        assertNotNull("Parameters should not be null", inputDataSet.getParameters());

        assertTrue("Should contain AG-UI thread ID",
                  inputDataSet.getParameters().containsKey("agui_thread_id"));
        assertEquals("Thread ID should match", "thread_123",
                    inputDataSet.getParameters().get("agui_thread_id"));

        assertTrue("Should contain AG-UI run ID",
                  inputDataSet.getParameters().containsKey("agui_run_id"));
        assertEquals("Run ID should match", "run_456",
                    inputDataSet.getParameters().get("agui_run_id"));

        assertTrue("Should extract user question",
                  inputDataSet.getParameters().containsKey("question"));
        assertEquals("User question should be extracted", "Hello world",
                    inputDataSet.getParameters().get("question"));
    }

    @Test
    public void testConvertFromAGUIInput_MinimalInput() {
        String aguiInput = "{\n" +
            "  \"threadId\": \"thread_minimal\",\n" +
            "  \"runId\": \"run_minimal\",\n" +
            "  \"state\": null,\n" +
            "  \"messages\": [],\n" +
            "  \"tools\": [],\n" +
            "  \"context\": [],\n" +
            "  \"forwardedProps\": null\n" +
            "}";

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(
            aguiInput, "minimal_agent", "minimal_tenant", true);

        assertNotNull("Converted result should not be null", result);
        assertEquals("Agent ID should be set correctly", "minimal_agent", result.getAgentId());
        assertTrue("Async should be true, but was: " + result.getIsAsync(), result.getIsAsync());

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertTrue("Should contain thread ID",
                  inputDataSet.getParameters().containsKey("agui_thread_id"));
        assertEquals("Thread ID should match", "thread_minimal",
                    inputDataSet.getParameters().get("agui_thread_id"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertFromAGUIInput_InvalidJSON() {
        String invalidInput = "{invalid json}";
        AGUIInputConverter.convertFromAGUIInput(invalidInput, "agent_id", "tenant", false);
    }

    @Test
    public void testReconstructAGUIInput() {
        // Create parameters that would result from conversion
        java.util.Map<String, String> parameters = new java.util.HashMap<>();
        parameters.put("agui_thread_id", "reconstruct_thread");
        parameters.put("agui_run_id", "reconstruct_run");
        parameters.put("agui_state", "{\"status\":\"active\"}");
        parameters.put("agui_messages", "[]");
        parameters.put("agui_tools", "[]");

        com.google.gson.JsonObject result = AGUIInputConverter.reconstructAGUIInput(parameters);

        assertNotNull("Reconstructed result should not be null", result);
        assertTrue("Should contain threadId", result.has("threadId"));
        assertEquals("Thread ID should match", "reconstruct_thread",
                    result.get("threadId").getAsString());
        assertTrue("Should contain runId", result.has("runId"));
        assertEquals("Run ID should match", "reconstruct_run",
                    result.get("runId").getAsString());
    }
}