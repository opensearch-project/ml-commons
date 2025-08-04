/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;

public class AgentMLInputTests {

    @Test
    public void testConstructorWithAgentIdFunctionNameAndDataset() {
        // Arrange
        String agentId = "testAgentId";
        FunctionName functionName = FunctionName.AGENT; // Assuming FunctionName is an enum or similar
        MLInputDataset dataset = mock(MLInputDataset.class); // Mock the MLInputDataset

        // Act
        AgentMLInput input = new AgentMLInput(agentId, null, functionName, dataset);

        // Assert
        assertEquals(agentId, input.getAgentId());
        assertEquals(functionName, input.getAlgorithm());
        assertEquals(dataset, input.getInputDataset());
    }

    @Test
    public void testConstructorWithXContentParser() throws IOException {
        // Arrange
        XContentParser parser = mock(XContentParser.class);

        // Simulate parser behavior for START_OBJECT token
        when(parser.currentToken()).thenReturn(XContentParser.Token.START_OBJECT);
        when(parser.nextToken())
            .thenReturn(XContentParser.Token.FIELD_NAME)
            .thenReturn(XContentParser.Token.VALUE_STRING)
            .thenReturn(XContentParser.Token.FIELD_NAME) // For PARAMETERS_FIELD
            .thenReturn(XContentParser.Token.START_OBJECT) // Start of PARAMETERS_FIELD map
            .thenReturn(XContentParser.Token.FIELD_NAME) // Key in PARAMETERS_FIELD map
            .thenReturn(XContentParser.Token.VALUE_STRING) // Value in PARAMETERS_FIELD map
            .thenReturn(XContentParser.Token.END_OBJECT) // End of PARAMETERS_FIELD map
            .thenReturn(XContentParser.Token.END_OBJECT); // End of the main object

        // Simulate parser behavior for agent_id
        when(parser.currentName()).thenReturn("agent_id").thenReturn("parameters").thenReturn("paramKey");
        when(parser.text()).thenReturn("testAgentId").thenReturn("paramValue");

        // Simulate parser behavior for parameters
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("paramKey", "paramValue");
        when(parser.map()).thenReturn(paramMap);

        // Act
        AgentMLInput input = new AgentMLInput(parser, FunctionName.AGENT);

        // Assert
        assertEquals("testAgentId", input.getAgentId());
        assertNotNull(input.getInputDataset());
        assertTrue(input.getInputDataset() instanceof RemoteInferenceInputDataSet);
        // Additional assertions for RemoteInferenceInputDataSet
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) input.getInputDataset();
        assertEquals("paramValue", dataset.getParameters().get("paramKey"));
    }

    @Test
    public void testConstructorWithMLAgent() {
        // Arrange
        String agentId = "testAgentId";
        String tenantId = "testTenantId";
        FunctionName functionName = FunctionName.AGENT;
        MLInputDataset dataset = mock(MLInputDataset.class);
        Boolean isAsync = true;
        MLAgent mlAgent = mock(MLAgent.class);

        // Act
        AgentMLInput input = new AgentMLInput(agentId, tenantId, functionName, dataset, isAsync, mlAgent);

        // Assert
        assertEquals(agentId, input.getAgentId());
        assertEquals(tenantId, input.getTenantId());
        assertEquals(functionName, input.getAlgorithm());
        assertEquals(dataset, input.getInputDataset());
        assertEquals(isAsync, input.getIsAsync());
        assertEquals(mlAgent, input.getMlAgent());
    }

    @Test
    public void testWriteTo_WithTenantId_VersionCompatibility() throws IOException {
        // Arrange
        String agentId = "testAgentId";
        String tenantId = "testTenantId";
        AgentMLInput input = new AgentMLInput(agentId, tenantId, FunctionName.AGENT, null);

        // Act and Assert for older version (before VERSION_2_19_0)
        StreamOutput oldVersionOut = mock(StreamOutput.class);
        when(oldVersionOut.getVersion()).thenReturn(Version.V_2_18_0); // Older version
        input.writeTo(oldVersionOut);

        // Verify tenantId is NOT written
        verify(oldVersionOut).writeString(agentId);
        verify(oldVersionOut, never()).writeOptionalString(tenantId);

        // Act and Assert for newer version (VERSION_2_19_0 and above)
        StreamOutput newVersionOut = mock(StreamOutput.class);
        when(newVersionOut.getVersion()).thenReturn(Version.V_2_19_0); // Newer version
        input.writeTo(newVersionOut);

        // Verify tenantId is written
        verify(newVersionOut).writeString(agentId);
        verify(newVersionOut).writeOptionalString(tenantId);
    }

    @Test
    public void testWriteTo_WithMLAgent_VersionCompatibility() throws IOException {
        // Arrange
        String agentId = "testAgentId";
        String tenantId = "testTenantId";
        MLAgent mlAgent = mock(MLAgent.class);
        AgentMLInput input = new AgentMLInput(agentId, tenantId, FunctionName.AGENT, null, false, mlAgent);

        // Act and Assert for version before MINIMAL_SUPPORTED_VERSION_FOR_INLINE_AGENT
        StreamOutput oldVersionOut = mock(StreamOutput.class);
        when(oldVersionOut.getVersion()).thenReturn(Version.V_3_1_0); // Version before 3.2.0
        input.writeTo(oldVersionOut);

        // Verify MLAgent is NOT written for older versions
        verify(oldVersionOut).writeString(agentId);
        verify(mlAgent, never()).writeTo(oldVersionOut);

        // Act and Assert for MINIMAL_SUPPORTED_VERSION_FOR_INLINE_AGENT and above
        StreamOutput newVersionOut = mock(StreamOutput.class);
        when(newVersionOut.getVersion()).thenReturn(Version.V_3_2_0); // Version 3.2.0 and above
        input.writeTo(newVersionOut);

        // Verify MLAgent is written for newer versions
        verify(newVersionOut).writeString(agentId);
        verify(mlAgent).writeTo(newVersionOut);
    }

    @Test
    public void testConstructorWithStreamInput_VersionCompatibility() throws IOException {
        // Arrange for older version
        StreamInput oldVersionIn = mock(StreamInput.class);
        when(oldVersionIn.getVersion()).thenReturn(Version.V_2_18_0); // Older version
        when(oldVersionIn.readString()).thenReturn("testAgentId");

        // Act and Assert for older version
        AgentMLInput inputOldVersion = new AgentMLInput(oldVersionIn);
        assertEquals("testAgentId", inputOldVersion.getAgentId());
        assertNull(inputOldVersion.getTenantId()); // tenantId should be null for older versions

        // Arrange for newer version
        StreamInput newVersionIn = mock(StreamInput.class);
        when(newVersionIn.getVersion()).thenReturn(Version.V_2_19_0); // Newer version
        when(newVersionIn.readString()).thenReturn("testAgentId");
        when(newVersionIn.readOptionalString()).thenReturn("testTenantId");

        // Act and Assert for newer version
        AgentMLInput inputNewVersion = new AgentMLInput(newVersionIn);
        assertEquals("testAgentId", inputNewVersion.getAgentId());
        assertEquals("testTenantId", inputNewVersion.getTenantId()); // tenantId should be populated for newer versions
    }
}
