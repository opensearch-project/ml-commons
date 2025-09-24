/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportCreateEventActionTests {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting featureEnabledSetting;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLCreateEventResponse> listener;

    private TransportCreateEventAction action;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(client.threadPool()).thenReturn(threadPool);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        action = new TransportCreateEventAction(
            transportService,
            actionFilters,
            client,
            xContentRegistry,
            featureEnabledSetting,
            memoryContainerHelper,
            threadPool
        );
    }

    @Test
    public void testDoExecute_FeatureDisabled() {
        when(featureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        action.doExecute(task, mock(MLCreateEventRequest.class), listener);
        verify(listener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_ContainerLookupFailure() {
        when(featureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(memoryContainerHelper.getMemoryContainer(eq("container-1"), any())).thenAnswer(invocation -> {
            ActionListener<MLMemoryContainer> callback = invocation.getArgument(1);
            callback.onFailure(new IllegalArgumentException("not found"));
            return null;
        });

        MLCreateEventInput input = MLCreateEventInput
            .builder()
            .memoryContainerId("container-1")
            .messages(Collections.singletonList(MessageInput.builder().role("user").content("hello").build()))
            .build();
        MLCreateEventRequest request = MLCreateEventRequest.builder().mlCreateEventInput(input).build();

        action.doExecute(task, request, listener);
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }
}
