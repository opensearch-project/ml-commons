/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse.TriggerStatus;
import org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportExecuteMemoryRetentionActionTests extends OpenSearchTestCase {

    private TransportExecuteMemoryRetentionAction action;

    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLExecuteMemoryRetentionResponse> actionListener;

    // The mocked processor we inject into the singleton so getInstance() returns it.
    @Mock
    private MemoryRetentionJobProcessor mockProcessor;

    @Captor
    private ArgumentCaptor<MLExecuteMemoryRetentionResponse> responseCaptor;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private ActionRequest request;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        request = MLExecuteMemoryRetentionRequest.builder().build();

        // Inject the mock into the singleton so the transport action's getInstance(...) call returns it
        // instead of constructing a real processor. This lets us drive triggerRun()'s outcome directly.
        setSingletonInstance(mockProcessor);

        action = new TransportExecuteMemoryRetentionAction(
            transportService,
            actionFilters,
            client,
            clusterService,
            threadPool,
            mlFeatureEnabledSetting
        );
    }

    @After
    public void cleanup() throws Exception {
        MemoryRetentionJobProcessor.reset();
    }

    public void testTriggeredSuccess() {
        when(mockProcessor.triggerRun()).thenReturn(TriggerStatus.TRIGGERED);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.TRIGGERED, response.getStatus());
        assertTrue(response.getStatus().isTriggered());
        assertNotNull(response.getMessage());
    }

    public void testAlreadyRunning() {
        when(mockProcessor.triggerRun()).thenReturn(TriggerStatus.ALREADY_RUNNING);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.ALREADY_RUNNING, response.getStatus());
        assertFalse(response.getStatus().isTriggered());
    }

    public void testRetentionDisabled() {
        when(mockProcessor.triggerRun()).thenReturn(TriggerStatus.RETENTION_DISABLED);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.RETENTION_DISABLED, response.getStatus());
        assertFalse(response.getStatus().isTriggered());
    }

    public void testMultiTenancyEnabled() {
        when(mockProcessor.triggerRun()).thenReturn(TriggerStatus.MULTI_TENANCY_ENABLED);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.MULTI_TENANCY_ENABLED, response.getStatus());
        assertFalse(response.getStatus().isTriggered());
    }

    public void testAgenticMemoryDisabledIsForbidden() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        action.doExecute(task, request, actionListener);

        verify(actionListener, never()).onResponse(any());
        verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        Exception e = exceptionCaptor.getValue();
        assertTrue(e instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) e).status());
        // The processor must never be touched when the feature is off.
        verify(mockProcessor, never()).triggerRun();
    }

    public void testProcessorFailureIsInternalServerError() {
        when(mockProcessor.triggerRun()).thenThrow(new RuntimeException("kickoff failed"));

        action.doExecute(task, request, actionListener);

        verify(actionListener, never()).onResponse(any());
        verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        Exception e = exceptionCaptor.getValue();
        assertTrue(e instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) e).status());
    }

    public void testFromActionRequestConversion() {
        // Drive doExecute with a generic ActionRequest (not the concrete type) to exercise the
        // fromActionRequest path used by the transport layer for cross-node serialization.
        when(mockProcessor.triggerRun()).thenReturn(TriggerStatus.TRIGGERED);
        ActionRequest generic = mock(ActionRequest.class);

        action.doExecute(task, generic, actionListener);

        // doExecute does not itself convert (it delegates straight to the processor), so it still
        // acknowledges; this guards that a non-concrete request does not blow up the handler.
        verify(actionListener, times(1)).onResponse(any());
    }

    private void setSingletonInstance(MemoryRetentionJobProcessor processor) throws Exception {
        Field instanceField = MemoryRetentionJobProcessor.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, processor);
    }
}
