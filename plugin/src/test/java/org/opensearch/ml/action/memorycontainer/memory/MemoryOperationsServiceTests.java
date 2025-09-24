/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.transport.client.Client;

public class MemoryOperationsServiceTests {

    @Mock
    private Client client;

    private MemoryOperationsService service;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MemoryOperationsService(client);
    }

    @Test
    public void testExecuteMemoryOperations_NoDecisionsCompletes() {
        MemoryConfiguration configuration = buildConfiguration();
        MLCreateEventInput input = mock(MLCreateEventInput.class);
        ActionListener<List<MemoryResult>> listener = mock(ActionListener.class);

        service
            .executeMemoryOperations(
                Collections.emptyList(),
                configuration,
                Collections.emptyMap(),
                mock(User.class),
                input,
                configuration,
                listener
            );

        verify(listener).onResponse(any());
    }

    @Test
    public void testBulkIndexMemoriesWithResults_NoRequestsFailsFast() {
        ActionListener<MLCreateEventResponse> listener = mock(ActionListener.class);
        service.bulkIndexMemoriesWithResults(Collections.emptyList(), Collections.emptyList(), "session", "index", listener);
        verify(listener).onFailure(any(IllegalStateException.class));
    }

    private MemoryConfiguration buildConfiguration() {
        return MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .strategies(
                Collections
                    .singletonList(
                        MemoryStrategy.builder().id("default").enabled(true).type("SEMANTIC").namespace(Collections.emptyList()).build()
                    )
            )
            .disableHistory(true)
            .disableSession(true)
            .build();
    }
}
