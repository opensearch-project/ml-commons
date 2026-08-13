/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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

    private org.opensearch.common.util.concurrent.ThreadContext threadContext;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Wire a real ThreadContext so RestActionUtils.getUserContext(client) can read the user header.
        // No header set = security disabled (user == null) -> admin gate is a no-op, matching most tests.
        threadContext = new org.opensearch.common.util.concurrent.ThreadContext(org.opensearch.common.settings.Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

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

    /**
     * Stub the async {@code triggerRun(ActionListener)} so it synchronously reports {@code status} to its
     * listener, mirroring the fast (already-gated / lock-uncontended) path.
     */
    @SuppressWarnings("unchecked")
    private void stubTriggerRunResponds(TriggerStatus status) {
        doAnswer(invocation -> {
            ActionListener<TriggerStatus> l = invocation.getArgument(0);
            l.onResponse(status);
            return null;
        }).when(mockProcessor).triggerRun(any(ActionListener.class));
    }

    public void testTriggeredSuccess() {
        stubTriggerRunResponds(TriggerStatus.TRIGGERED);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.TRIGGERED, response.getStatus());
        assertTrue(response.getStatus().isTriggered());
        assertNotNull(response.getMessage());
    }

    public void testAlreadyRunning() {
        stubTriggerRunResponds(TriggerStatus.ALREADY_RUNNING);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.ALREADY_RUNNING, response.getStatus());
        assertFalse(response.getStatus().isTriggered());
    }

    public void testRetentionDisabled() {
        stubTriggerRunResponds(TriggerStatus.RETENTION_DISABLED);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        MLExecuteMemoryRetentionResponse response = responseCaptor.getValue();
        assertEquals(TriggerStatus.RETENTION_DISABLED, response.getStatus());
        assertFalse(response.getStatus().isTriggered());
    }

    public void testMultiTenancyEnabled() {
        stubTriggerRunResponds(TriggerStatus.MULTI_TENANCY_ENABLED);

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
        verify(mockProcessor, never()).triggerRun(any(ActionListener.class));
    }

    @SuppressWarnings("unchecked")
    public void testNonAdminUserIsForbidden() throws Exception {
        // A security-enabled, non-admin caller must be rejected: on-demand execute is cluster-wide and
        // could force early deletion of data the caller cannot otherwise reach.
        setUser("alice", "some_backend_role");

        action.doExecute(task, request, actionListener);

        verify(actionListener, never()).onResponse(any());
        verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        Exception e = exceptionCaptor.getValue();
        assertTrue(e instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) e).status());
        // The processor must never be touched when the caller is not an admin.
        verify(mockProcessor, never()).triggerRun(any(ActionListener.class));
    }

    @SuppressWarnings("unchecked")
    public void testAdminUserIsAllowed() throws Exception {
        // An all_access admin passes the gate and the trigger proceeds.
        setUser("admin", "all_access");
        stubTriggerRunResponds(TriggerStatus.TRIGGERED);

        action.doExecute(task, request, actionListener);

        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());
        assertEquals(TriggerStatus.TRIGGERED, responseCaptor.getValue().getStatus());
        verify(mockProcessor, times(1)).triggerRun(any(ActionListener.class));
    }

    /** Put a user + roles into the thread context in the format RestActionUtils.getUserContext expects. */
    private void setUser(String name, String... roles) {
        String rolesCsv = String.join(",", roles);
        threadContext
            .putTransient(org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, name + "||" + rolesCsv);
    }

    @SuppressWarnings("unchecked")
    public void testProcessorFailureIsInternalServerError() {
        // Async failure: triggerRun reports onFailure to its listener (e.g. lock acquisition failed).
        doAnswer(invocation -> {
            ActionListener<TriggerStatus> l = invocation.getArgument(0);
            l.onFailure(new RuntimeException("kickoff failed"));
            return null;
        }).when(mockProcessor).triggerRun(any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        verify(actionListener, never()).onResponse(any());
        verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        Exception e = exceptionCaptor.getValue();
        assertTrue(e instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) e).status());
    }

    @SuppressWarnings("unchecked")
    public void testProcessorSynchronousThrowIsInternalServerError() {
        // A synchronous throw from triggerRun (before it can invoke the listener) is caught by doExecute's
        // try/catch and surfaced as a 500.
        doThrow(new RuntimeException("synchronous boom")).when(mockProcessor).triggerRun(any(ActionListener.class));

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
        stubTriggerRunResponds(TriggerStatus.TRIGGERED);
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
