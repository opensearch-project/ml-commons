/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class ClientAdapterTests extends OpenSearchTestCase {

    @Mock
    private Client mockClient;

    @Mock
    private ThreadPool mockThreadPool;

    private ThreadContext threadContext;
    private ClientAdapter adapter;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        threadContext = new ThreadContext(Settings.EMPTY);
        when(mockClient.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);
        adapter = new ClientAdapter(mockClient);
    }

    public void testGetDelegate() {
        assertSame(mockClient, adapter.getDelegate());
    }

    @SuppressWarnings("unchecked")
    public void testExecute() {
        MLPredictionTaskRequest mockRequest = org.mockito.Mockito.mock(MLPredictionTaskRequest.class);
        ActionListener mockListener = org.mockito.Mockito.mock(ActionListener.class);

        adapter.execute(MLPredictionStreamTaskAction.INSTANCE, mockRequest, mockListener);

        verify(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), eq(mockRequest), eq(mockListener));
    }
}
