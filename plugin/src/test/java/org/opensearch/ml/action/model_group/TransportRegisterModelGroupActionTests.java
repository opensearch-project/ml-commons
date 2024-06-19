/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
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

    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

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

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private final List<String> backendRoles = Arrays.asList("IT", "HR");

    private static TestThreadPool testThreadPool = new TestThreadPool(
        TransportRegisterModelGroupActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        transportRegisterModelGroupAction = new TransportRegisterModelGroupAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            threadPool,
            client,
            sdkClient,
            clusterService,
            modelAccessControlHelper,
            mlModelGroupManager,
            mlFeatureEnabledSetting
        );
        assertNotNull(transportRegisterModelGroupAction);

    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
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

    @Test
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

    @Test
    public void test_TenantIdValidationFailure() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
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
