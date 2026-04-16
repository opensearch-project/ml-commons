/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
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

    public void testGetThreadContext() {
        ThreadContext result = adapter.getThreadContext();

        assertNotNull(result);
        assertSame(threadContext, result);
    }

    public void testGetDelegate() {
        assertSame(mockClient, adapter.getDelegate());
    }
}
