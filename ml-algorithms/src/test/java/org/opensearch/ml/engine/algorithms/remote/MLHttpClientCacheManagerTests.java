/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

/**
 * Covers the cache-invalidation branch of {@link MLHttpClientCacheManager}: the manager is driven
 * directly with two differing configurations so the replaced-client path is actually exercised.
 */
public class MLHttpClientCacheManagerTests {

    @Mock
    private Client client;

    @Mock
    private ThreadPool threadPool;

    private MLHttpClientCacheManager cacheManager;
    private SdkAsyncHttpClient firstClient;
    private SdkAsyncHttpClient secondClient;
    private HttpConnector connector;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        cacheManager = new MLHttpClientCacheManager();
        firstClient = mock(SdkAsyncHttpClient.class);
        secondClient = mock(SdkAsyncHttpClient.class);
        when(client.threadPool()).thenReturn(threadPool);

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();
        connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
    }

    private ConnectorClientConfig configWithReadTimeout(int readTimeout) {
        return new ConnectorClientConfig(10, readTimeout, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT, false, false, null);
    }

    /** Returns firstClient on the first call and secondClient afterwards. */
    private java.util.function.Supplier<SdkAsyncHttpClient> alternatingFactory() {
        List<SdkAsyncHttpClient> clients = Arrays.asList(firstClient, secondClient);
        Iterator<SdkAsyncHttpClient> iterator = clients.iterator();
        return iterator::next;
    }

    @Test
    public void testGetOrCreateHttpClient_UnchangedConfig_ReusesCachedClient() {
        ConnectorClientConfig config = configWithReadTimeout(30);
        java.util.function.Supplier<SdkAsyncHttpClient> factory = alternatingFactory();

        SdkAsyncHttpClient first = cacheManager.getOrCreateHttpClient(connector, config, client, factory);
        SdkAsyncHttpClient second = cacheManager.getOrCreateHttpClient(connector, config, client, factory);

        assertSame("An unchanged configuration must reuse the cached client", first, second);
        verify(threadPool, never()).schedule(any(), any(), anyString());
        verify(firstClient, never()).close();
    }

    @Test
    public void testGetOrCreateHttpClient_ConfigChanged_SchedulesDeferredCloseOfOldClient() {
        java.util.function.Supplier<SdkAsyncHttpClient> factory = alternatingFactory();

        SdkAsyncHttpClient first = cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(30), client, factory);
        SdkAsyncHttpClient second = cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(60), client, factory);

        assertNotSame("A changed configuration must produce a new client", first, second);
        assertSame(secondClient, second);
        // The old client is closed by the scheduled task, not synchronously.
        verify(threadPool).schedule(any(Runnable.class), any(TimeValue.class), anyString());
        verify(firstClient, never()).close();
    }

    @Test
    public void testGetOrCreateHttpClient_ScheduleRejected_ClosesOldClientImmediately() {
        // A shutting-down node rejects new tasks. The old client must still be released rather than
        // leaking its connection pool and threads.
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString()))
            .thenThrow(new RuntimeException("rejected execution"));
        java.util.function.Supplier<SdkAsyncHttpClient> factory = alternatingFactory();

        SdkAsyncHttpClient first = cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(30), client, factory);
        SdkAsyncHttpClient second = cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(60), client, factory);

        verify(firstClient).close();
        assertNotSame("The swap must still complete when scheduling is rejected", first, second);
        assertSame("The new client must remain cached after the fallback close", secondClient, second);
    }

    @Test
    public void testGetOrCreateHttpClient_ScheduleRejectedAndCloseFails_DoesNotPropagate() {
        // Both the scheduling and the fallback close fail: the swap must still succeed rather than
        // surfacing a cleanup failure to the caller's request.
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString()))
            .thenThrow(new RuntimeException("rejected execution"));
        doThrowOnClose(firstClient);
        java.util.function.Supplier<SdkAsyncHttpClient> factory = alternatingFactory();

        cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(30), client, factory);
        SdkAsyncHttpClient second = cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(60), client, factory);

        assertSame("A failing close must not prevent the new client being cached", secondClient, second);
    }

    private void doThrowOnClose(SdkAsyncHttpClient httpClient) {
        org.mockito.Mockito.doThrow(new RuntimeException("close failed")).when(httpClient).close();
    }

    @Test
    public void testGetOrCreateHttpClient_ClientFactoryFails_KeepsExistingClient() {
        java.util.function.Supplier<SdkAsyncHttpClient> factory = alternatingFactory();
        SdkAsyncHttpClient first = cacheManager.getOrCreateHttpClient(connector, configWithReadTimeout(30), client, factory);

        java.util.function.Supplier<SdkAsyncHttpClient> failingFactory = () -> { throw new RuntimeException("boom"); };
        ConnectorClientConfig changedConfig = configWithReadTimeout(60);

        assertThrows(RuntimeException.class, () -> cacheManager.getOrCreateHttpClient(connector, changedConfig, client, failingFactory));

        // The previous client must survive a failed rebuild, and must not have been closed.
        assertSame(first, cacheManager.getCurrentClient());
        verify(firstClient, never()).close();
    }
}
