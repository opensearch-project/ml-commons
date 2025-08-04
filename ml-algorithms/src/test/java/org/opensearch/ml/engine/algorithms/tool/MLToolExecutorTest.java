/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.tool;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.tool.ToolMLInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

public class MLToolExecutorTest {

    @Mock
    private Client client;
    @Mock
    private SdkClient sdkClient;
    private Settings settings;
    @Mock
    private ClusterService clusterService;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private Map<String, Tool.Factory> toolFactories;
    @Mock
    private Tool.Factory toolFactory;
    @Mock
    private Tool tool;
    @Mock
    private ActionListener<Output> actionListener;
    @Mock
    private ToolMLInput toolMLInput;
    @Mock
    private RemoteInferenceInputDataSet inputDataSet;

    @Captor
    private ArgumentCaptor<Output> outputCaptor;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private MLToolExecutor mlToolExecutor;
    private Map<String, String> parameters;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_EXECUTE_TOOL_ENABLED.getKey(), true).build();

        parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("model_id", "test_model");

        toolFactories = new HashMap<>();
        toolFactories.put("TestTool", toolFactory);

        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_EXECUTE_TOOL_ENABLED)));

        mlToolExecutor = Mockito.spy(new MLToolExecutor(client, sdkClient, settings, clusterService, xContentRegistry, toolFactories));
    }

    @Test
     public void test_ExecuteSuccess() {
         when(toolMLInput.getToolName()).thenReturn("TestTool");
         when(toolMLInput.getInputDataset()).thenReturn(inputDataSet);
         when(inputDataSet.getParameters()).thenReturn(parameters);
         when(toolFactory.create(any())).thenReturn(tool);
         when(tool.validate(parameters)).thenReturn(true);

         Mockito.doAnswer(invocation -> {
             ActionListener<Object> listener = invocation.getArgument(1);
             listener.onResponse("test result");
             return null;
         }).when(tool).run(Mockito.eq(parameters), any());

         mlToolExecutor.execute(toolMLInput, actionListener);

         Mockito.verify(actionListener).onResponse(outputCaptor.capture());
         Output output = outputCaptor.getValue();
         Assert.assertTrue(output instanceof ModelTensorOutput);
     }

    @Test
    public void test_ToolNotFound() {
        when(toolMLInput.getToolName()).thenReturn("InvalidTool");
        when(toolMLInput.getInputDataset()).thenReturn(inputDataSet);
        when(inputDataSet.getParameters()).thenReturn(parameters);

        mlToolExecutor.execute(toolMLInput, actionListener);

        Mockito.verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertEquals("Tool not found: InvalidTool", exception.getMessage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NullInput_ThrowsException() {
        mlToolExecutor.execute(null, actionListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NonToolInput_ThrowsException() {
        Input input = Mockito.mock(Input.class);
        mlToolExecutor.execute(input, actionListener);
    }

    @Test
    public void test_ValidationFailed() {
        when(toolMLInput.getToolName()).thenReturn("TestTool");
        when(toolMLInput.getInputDataset()).thenReturn(inputDataSet);
        when(inputDataSet.getParameters()).thenReturn(parameters);
        when(toolFactory.create(any())).thenReturn(tool);
        when(tool.validate(parameters)).thenReturn(false);

        mlToolExecutor.execute(toolMLInput, actionListener);

        Mockito.verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertEquals("Invalid parameters for tool: TestTool", exception.getMessage());
    }

    @Test
     public void test_ToolExecutionFailed() {
         when(toolMLInput.getToolName()).thenReturn("TestTool");
         when(toolMLInput.getInputDataset()).thenReturn(inputDataSet);
         when(inputDataSet.getParameters()).thenReturn(parameters);
         when(toolFactory.create(any())).thenReturn(tool);
         when(tool.validate(parameters)).thenReturn(true);

         Mockito.doAnswer(invocation -> {
             ActionListener<Object> listener = invocation.getArgument(1);
             listener.onFailure(new RuntimeException("Tool execution failed"));
             return null;
         }).when(tool).run(Mockito.eq(parameters), any());

         mlToolExecutor.execute(toolMLInput, actionListener);

         Mockito.verify(actionListener).onFailure(exceptionCaptor.capture());
         Exception exception = exceptionCaptor.getValue();
         Assert.assertEquals("Tool execution failed", exception.getMessage());
     }

    @Test
    public void test_EmptyInputData() {
        when(toolMLInput.getToolName()).thenReturn("TestTool");
        when(toolMLInput.getInputDataset()).thenReturn(null);

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            mlToolExecutor.execute(toolMLInput, actionListener);
        });
    }
}
