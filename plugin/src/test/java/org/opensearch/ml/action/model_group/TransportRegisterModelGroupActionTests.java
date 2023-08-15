/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportRegisterModelGroupActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private TransportService transportService;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Task task;

    @Mock
    private Client client;
    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ActionListener<MLRegisterModelGroupResponse> actionListener;

    @Mock
    private IndexResponse indexResponse;

    ThreadContext threadContext;

    private TransportRegisterModelGroupAction transportRegisterModelGroupAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    @Mock
    private MLModelGroupManager mlModelGroupManager;

    private final List<String> backendRoles = Arrays.asList("IT", "HR");

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        transportRegisterModelGroupAction = new TransportRegisterModelGroupAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            threadPool,
            client,
            clusterService,
            modelAccessControlHelper,
            mlModelGroupManager
        );
        assertNotNull(transportRegisterModelGroupAction);

    }

    public void test_Success() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("modelGroupID");
            return null;
        }).when(mlModelGroupManager).createModelGroup(any(), any());

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_Failure() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Failed to init model group index"));
            return null;
        }).when(mlModelGroupManager).createModelGroup(any(), any());

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to init model group index", argumentCaptor.getValue().getMessage());
    }

    private MLRegisterModelGroupRequest prepareRequest(
        List<String> backendRoles,
        AccessMode modelAccessMode,
        Boolean isAddAllBackendRoles
    ) {
        MLRegisterModelGroupInput registerModelGroupInput = MLRegisterModelGroupInput
            .builder()
            .name("modelGroupName")
            .description("This is a test model group")
            .backendRoles(backendRoles)
            .modelAccessMode(modelAccessMode)
            .isAddAllBackendRoles(isAddAllBackendRoles)
            .build();
        return new MLRegisterModelGroupRequest(registerModelGroupInput);
    }

}
