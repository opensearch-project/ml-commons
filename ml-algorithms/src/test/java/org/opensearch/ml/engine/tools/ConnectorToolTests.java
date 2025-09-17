/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorAction;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.transport.client.Client;

public class ConnectorToolTests {

    @Mock
    private Client client;
    private Map<String, String> otherParams;

    @Mock
    private Parser mockOutputParser;

    @Mock
    private ActionListener<ModelTensorOutput> listener;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ConnectorTool.Factory.getInstance().init(client);

        otherParams = Map.of("other", "[\"bar\"]");
    }

    @Test
    public void testConnectorTool_NullConnectorId() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLExecuteConnectorAction.INSTANCE), any(), any());

        Exception exception = assertThrows(
            IllegalArgumentException.class,
            () -> ConnectorTool.Factory.getInstance().create(Map.of("test1", "value1"))
        );
        MatcherAssert.assertThat(exception.getMessage(), containsString("Connector ID can't be null or empty"));
    }

    @Test
    public void testConnectorTool_DefaultOutputParser() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLExecuteConnectorAction.INSTANCE), any(), any());

        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test_connector"));
        tool.run(null, ActionListener.wrap(r -> { assertEquals("response 1", r); }, e -> { throw new RuntimeException("Test failed"); }));
    }

    @Test
    public void testConnectorTool_NullOutputParser() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLExecuteConnectorAction.INSTANCE), any(), any());

        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test_connector"));
        tool.setOutputParser(null);

        tool.run(null, ActionListener.wrap(r -> {
            List response = (List) r;
            assertEquals(1, response.size());
            assertEquals(1, ((ModelTensors) response.get(0)).getMlModelTensors().size());
            ModelTensor modelTensor1 = ((ModelTensors) response.get(0)).getMlModelTensors().get(0);
            assertEquals(2, modelTensor1.getDataAsMap().size());
            assertEquals("response 1", modelTensor1.getDataAsMap().get("response"));
            assertEquals("action1", modelTensor1.getDataAsMap().get("action"));
        }, e -> { throw new RuntimeException("Test failed"); }));
    }

    @Test
    public void testConnectorTool_NotNullParameters() {
        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test1"));
        assertTrue(tool.validate(Map.of("key1", "value1")));
    }

    @Test
    public void testConnectorTool_NullParameters() {
        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test1"));
        assertFalse(tool.validate(Map.of()));
    }

    @Test
    public void testConnectorTool_EmptyParameters() {
        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test1"));
        assertFalse(tool.validate(null));
    }

    @Test
    public void testConnectorTool_GetType() {
        ConnectorTool.Factory.getInstance().init(client);
        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test1"));
        assertEquals("ConnectorTool", tool.getType());
    }

    @Test
    public void testRunWithError() {
        // Mocking the client.execute to simulate an error
        String errorMessage = "Test Exception";
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(client).execute(any(), any(), any());

        // Running the test
        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test1"));
        tool.setOutputParser(mockOutputParser);
        tool.run(otherParams, listener);

        // Verifying that onFailure was called
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testTool() {
        Tool tool = ConnectorTool.Factory.getInstance().create(Map.of("connector_id", "test1"));
        assertEquals(ConnectorTool.TYPE, tool.getName());
        assertEquals(ConnectorTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertTrue(tool.validate(otherParams));
        assertEquals(ConnectorTool.Factory.DEFAULT_DESCRIPTION, tool.getDescription());
        assertEquals(ConnectorTool.Factory.DEFAULT_DESCRIPTION, ConnectorTool.Factory.getInstance().getDefaultDescription());
        assertEquals(ConnectorTool.TYPE, ConnectorTool.Factory.getInstance().getDefaultType());
        assertNull(ConnectorTool.Factory.getInstance().getDefaultVersion());
    }

}
