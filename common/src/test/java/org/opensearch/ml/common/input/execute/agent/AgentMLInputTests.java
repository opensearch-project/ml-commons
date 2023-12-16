/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import org.junit.Test;
import org.opensearch.core.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.AggregationBuilders;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentMLInputTests {

    @Test
    public void testConstructorWithAgentIdFunctionNameAndDataset() {
        // Arrange
        String agentId = "testAgentId";
        FunctionName functionName = FunctionName.AGENT; // Assuming FunctionName is an enum or similar
        MLInputDataset dataset = mock(MLInputDataset.class); // Mock the MLInputDataset

        // Act
        AgentMLInput input = new AgentMLInput(agentId, functionName, dataset);

        // Assert
        assertEquals(agentId, input.getAgentId());
        assertEquals(functionName, input.getAlgorithm());
        assertEquals(dataset, input.getInputDataset());
    }

    @Test
    public void testWriteTo() throws IOException {
        // Arrange
        String agentId = "testAgentId";
        AgentMLInput input = new AgentMLInput(agentId, FunctionName.AGENT, null);
        StreamOutput out = mock(StreamOutput.class);

        // Act
        input.writeTo(out);

        // Assert
        verify(out).writeString(agentId);
    }

    @Test
    public void testConstructorWithStreamInput() throws IOException {
        // Arrange
        String agentId = "testAgentId";
        StreamInput in = mock(StreamInput.class);
        when(in.readString()).thenReturn(agentId);

        // Act
        AgentMLInput input = new AgentMLInput(in);

        // Assert
        assertEquals(agentId, input.getAgentId());
    }

    @Test
    public void testConstructorWithXContentParser() throws IOException {
        // Arrange
        XContentParser parser = mock(XContentParser.class);

        // Simulate parser behavior for START_OBJECT token
        when(parser.currentToken()).thenReturn(XContentParser.Token.START_OBJECT);
        when(parser.nextToken()).thenReturn(XContentParser.Token.FIELD_NAME)
                .thenReturn(XContentParser.Token.VALUE_STRING)
                .thenReturn(XContentParser.Token.FIELD_NAME) // For PARAMETERS_FIELD
                .thenReturn(XContentParser.Token.START_OBJECT) // Start of PARAMETERS_FIELD map
                .thenReturn(XContentParser.Token.FIELD_NAME) // Key in PARAMETERS_FIELD map
                .thenReturn(XContentParser.Token.VALUE_STRING) // Value in PARAMETERS_FIELD map
                .thenReturn(XContentParser.Token.END_OBJECT) // End of PARAMETERS_FIELD map
                .thenReturn(XContentParser.Token.END_OBJECT); // End of the main object

        // Simulate parser behavior for agent_id
        when(parser.currentName()).thenReturn("agent_id")
                .thenReturn("parameters")
                .thenReturn("paramKey");
        when(parser.text()).thenReturn("testAgentId")
                .thenReturn("paramValue");

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

}
