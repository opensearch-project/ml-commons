package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.node.Node;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;

public class RemoteConnectorExecutorTest {
    @Mock
    private ScriptService scriptService;
    @Mock
    private Connector connector;
    @Mock
    private Client client;
    @Mock
    private MLInput mockMlInput;
    @Mock
    private ActionListener<MLTaskResponse> callback;
    @Mock
    private RemoteConnectorExecutor mockRemoteConnectorExecutor;
    @Mock
    private ConnectorAction connectorAction;
    @Mock
    private ThreadPool threadPool;

    private RemoteConnectorExecutor remoteConnectorExecutor;

    private static final String USER_DEFINED_PREPROCESS =
        "\\n    StringBuilder builder = new StringBuilder();\\n    builder.append(\\\"\\\\\\\"\\\");\\n    String first = params.text_docs[0];\\n    builder.append(first);\\n    builder.append(\\\"\\\\\\\"\\\");\\n    def parameters = \\\"{\\\" +\\\"\\\\\\\"inputText\\\\\\\":\\\" + builder + \\\"}\\\";\\n    return  \\\"{\\\" +\\\"\\\\\\\"parameters\\\\\\\":\\\" + parameters + \\\"}\\\";";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        remoteConnectorExecutor = new TestRemoteConnectorExecutor(mockRemoteConnectorExecutor);
        lenient().when(connector.findPredictAction()).thenReturn(Optional.of(connectorAction));
        threadPool = new ThreadPool(Settings.builder().put(Node.NODE_NAME_SETTING.getKey(), "RemoteConnectorExecutorTest").build());
        lenient().when(client.threadPool()).thenReturn(threadPool);
    }

    @Test
    public void test_executePredict_shouldCutBatch_invalidArgumentException() {
        TextDocsInputDataSet dataSet = prepareTextDocsInputDataSet(10);
        when(connector.getParameters()).thenReturn(Collections.singletonMap("max_batch_size", "-1"));
        when(mockMlInput.getInputDataset()).thenReturn(dataSet);

        remoteConnectorExecutor.executePredict(mockMlInput, callback);
        verify(callback).onFailure(any(IllegalArgumentException.class));
        verify(mockRemoteConnectorExecutor, never()).invokeRemoteModel(any(), anyMap(), anyString(), anyMap(), any(), any());
    }

    @Test
    public void test_executePredict_shouldCutBatch_parameter_not_set() {
        TextDocsInputDataSet dataSet = prepareTextDocsInputDataSet(10);
        when(connector.getParameters()).thenReturn(Collections.emptyMap());
        when(mockMlInput.getInputDataset()).thenReturn(dataSet);

        remoteConnectorExecutor.executePredict(mockMlInput, callback);
        ArgumentCaptor<MLInput> mlInputCaptor = ArgumentCaptor.forClass(MLInput.class);
        verify(mockRemoteConnectorExecutor).invokeRemoteModel(mlInputCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals(TextDocsInputDataSet.class, mlInputCaptor.getValue().getInputDataset().getClass());
        TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInputCaptor.getValue().getInputDataset();
        assertEquals(10, textDocsInputDataSet.getDocs().size());
    }

    @Test
    public void test_executePredict_shouldCutBatch_with_step_size_parameter() {
        TextDocsInputDataSet dataSet = prepareTextDocsInputDataSet(9);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input_docs_processed_step_size", "2");
        parameters.put("max_batch_size", "4");
        when(connector.getParameters()).thenReturn(parameters);
        when(mockMlInput.getInputDataset()).thenReturn(dataSet);

        remoteConnectorExecutor.executePredict(mockMlInput, callback);
        ArgumentCaptor<MLInput> mlInputCaptor = ArgumentCaptor.forClass(MLInput.class);
        verify(mockRemoteConnectorExecutor, times(5)).invokeRemoteModel(mlInputCaptor.capture(), any(), any(), any(), any(), any());
        for (int i = 0; i < 5; ++i) {
            MLInput mlInput = mlInputCaptor.getAllValues().get(i);
            assertEquals(TextDocsInputDataSet.class, mlInput.getInputDataset().getClass());
            TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
            if (i < 4) {
                assertEquals(2, textDocsInputDataSet.getDocs().size());
            } else {
                assertEquals(1, textDocsInputDataSet.getDocs().size());
            }
        }
    }

    @Test
    public void test_executePredict_shouldCutBatch_with_preprocess() {
        TextDocsInputDataSet dataSet = prepareTextDocsInputDataSet(9);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("max_batch_size", "4");
        when(connector.getParameters()).thenReturn(parameters);
        when(mockMlInput.getInputDataset()).thenReturn(dataSet);
        when(connectorAction.getPreProcessFunction()).thenReturn(USER_DEFINED_PREPROCESS);
        String preprocessResult = "{\"parameters\": { \"input\": \"test doc1\" } }";
        when(scriptService.compile(any(), any())).then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult));

        remoteConnectorExecutor.executePredict(mockMlInput, callback);
        ArgumentCaptor<MLInput> mlInputCaptor = ArgumentCaptor.forClass(MLInput.class);
        verify(mockRemoteConnectorExecutor, times(9)).invokeRemoteModel(mlInputCaptor.capture(), any(), any(), any(), any(), any());
        for (int i = 0; i < 9; ++i) {
            MLInput mlInput = mlInputCaptor.getAllValues().get(i);
            assertEquals(TextDocsInputDataSet.class, mlInput.getInputDataset().getClass());
            TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
            assertEquals(1, textDocsInputDataSet.getDocs().size());
        }
    }

    @Test
    public void test_executePredict_shouldCutBatch_with_sort_one_batch() {
        List<String> docs = Arrays.asList("444", "33", "22", "5555", "1");
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(docs).build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("max_batch_size", "2");
        when(connector.getParameters()).thenReturn(parameters);
        when(mockMlInput.getInputDataset()).thenReturn(dataSet);

        remoteConnectorExecutor.executePredict(mockMlInput, callback);
        ArgumentCaptor<MLInput> mlInputCaptor = ArgumentCaptor.forClass(MLInput.class);
        ArgumentCaptor<ExecutionContext> executionContextCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(mockRemoteConnectorExecutor, times(3))
            .invokeRemoteModel(mlInputCaptor.capture(), any(), any(), any(), executionContextCaptor.capture(), any());
        // first batch
        verifyBatch(mlInputCaptor.getAllValues().get(0), 2, Arrays.asList("1", "33"));
        // second batch
        verifyBatch(mlInputCaptor.getAllValues().get(1), 2, Arrays.asList("22", "444"));
        // third batch
        verifyBatch(mlInputCaptor.getAllValues().get(2), 1, List.of("5555"));
        Map<Integer, Integer> expectedMap = new HashMap<>();
        expectedMap.put(3, 0);
        expectedMap.put(1, 1);
        expectedMap.put(2, 2);
        expectedMap.put(0, 4);
        expectedMap.put(4, 3);
        assertEquals(expectedMap, executionContextCaptor.getValue().getOriginalOrder());
    }

    @Test
    public void test_executePredict_shouldCutBatch_no_sort_one_doc_in_batch() {
        List<String> docs = Arrays.asList("444", "33", "22", "5555", "1");
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(docs).build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("max_batch_size", "1");
        when(connector.getParameters()).thenReturn(parameters);
        when(mockMlInput.getInputDataset()).thenReturn(dataSet);

        remoteConnectorExecutor.executePredict(mockMlInput, callback);
        ArgumentCaptor<MLInput> mlInputCaptor = ArgumentCaptor.forClass(MLInput.class);
        ArgumentCaptor<ExecutionContext> executionContextCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(mockRemoteConnectorExecutor, times(5))
            .invokeRemoteModel(mlInputCaptor.capture(), any(), any(), any(), executionContextCaptor.capture(), any());
        for (int i = 0; i < 5; ++i) {
            verifyBatch(mlInputCaptor.getAllValues().get(i), 1, Collections.singletonList(docs.get(i)));
            assertTrue(executionContextCaptor.getAllValues().get(i).getOriginalOrder().isEmpty());
        }
    }

    private void verifyBatch(MLInput mlInput, int expectedSize, List<String> expectedDocs) {
        assertEquals(TextDocsInputDataSet.class, mlInput.getInputDataset().getClass());
        TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();

        assertEquals(expectedSize, textDocsInputDataSet.getDocs().size());
        for (int i = 0; i < expectedSize; ++i) {
            assertEquals(expectedDocs.get(i), textDocsInputDataSet.getDocs().get(i));
        }
    }

    private TextDocsInputDataSet prepareTextDocsInputDataSet(int count) {
        List<String> docs = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            docs.add(RandomStringUtils.random(10, true, false));
        }
        return TextDocsInputDataSet.builder().docs(docs).build();
    }

    private class TestRemoteConnectorExecutor implements RemoteConnectorExecutor {

        final private RemoteConnectorExecutor delegate;

        private TestRemoteConnectorExecutor(RemoteConnectorExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public ScriptService getScriptService() {
            return scriptService;
        }

        @Override
        public Connector getConnector() {
            return connector;
        }

        @Override
        public TokenBucket getRateLimiter() {
            return null;
        }

        @Override
        public Map<String, TokenBucket> getUserRateLimiterMap() {
            return Collections.emptyMap();
        }

        @Override
        public MLGuard getMlGuard() {
            return null;
        }

        @Override
        public Client getClient() {
            return client;
        }

        @Override
        public void invokeRemoteModel(
            MLInput mlInput,
            Map<String, String> parameters,
            String payload,
            Map<Integer, ModelTensors> tensorOutputs,
            ExecutionContext countDownLatch,
            ActionListener<List<ModelTensors>> actionListener
        ) {
            this.delegate.invokeRemoteModel(mlInput, parameters, payload, tensorOutputs, countDownLatch, actionListener);
        }
    }
}
