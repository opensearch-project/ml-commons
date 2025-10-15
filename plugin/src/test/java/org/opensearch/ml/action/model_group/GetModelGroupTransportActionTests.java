/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetRequest;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetModelGroupTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLModelGroupGetResponse> actionListener;

    @Mock
    ClusterService clusterService;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetModelGroupTransportAction getModelGroupTransportAction;
    MLModelGroupGetRequest mlModelGroupGetRequest;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        mlModelGroupGetRequest = MLModelGroupGetRequest.builder().modelGroupId("test_id").build();

        getModelGroupTransportAction = spy(
            new GetModelGroupTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(4);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void test_Success() throws IOException {

        GetResponse getResponse = prepareMLModelGroup();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelGroupTransportAction.doExecute(null, mlModelGroupGetRequest, actionListener);
        ArgumentCaptor<MLModelGroupGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLModelGroupGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testGetModel_UserHasNoAccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(4);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        GetResponse getResponse = prepareMLModelGroup();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelGroupTransportAction.doExecute(null, mlModelGroupGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model group", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_ValidateAccessFailed() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(4);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        GetResponse getResponse = prepareMLModelGroup();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelGroupTransportAction.doExecute(null, mlModelGroupGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_NotExistsResponse() {
        GetResult getResult = new GetResult(
            ML_MODEL_GROUP_INDEX,
            "fake_id",
            UNASSIGNED_SEQ_NO,
            UNASSIGNED_PRIMARY_TERM,
            -1L,
            false,
            null,
            null,
            null
        );
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(new GetResponse(getResult));
            return null;
        }).when(client).get(any(), any());
        getModelGroupTransportAction.doExecute(null, mlModelGroupGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group with the provided model group id: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_IndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Fail to find model group index"));
            return null;
        }).when(client).get(any(), any());
        getModelGroupTransportAction.doExecute(null, mlModelGroupGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group index", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_RuntimeException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).get(any(), any());
        getModelGroupTransportAction.doExecute(null, mlModelGroupGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-model-group", argumentCaptor.getValue().getMessage());
    }

    public void test_Get_RSC_FeatureEnabled_TypeEnabled_SkipsLegacyValidation() throws IOException {
        // Force RSC fast-path (feature + type enabled)
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);

        when(rsc.isFeatureEnabledForType(any())).thenReturn(true);

        // Tenant on request and document must match for TenantAwareHelper.validateTenantResource
        String tenantId = "t-1";
        MLModelGroupGetRequest req = MLModelGroupGetRequest.builder().modelGroupId("mg-123").tenantId(tenantId).build();

        // SDK returns the model-group doc
        GetResponse getResponse = prepareMLModelGroup();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        // Execute
        getModelGroupTransportAction.doExecute(null, req, actionListener);

        // Legacy validation MUST be skipped
        verify(modelAccessControlHelper, times(0)).validateModelGroupAccess(any(), any(), any(), any(), any());

        ArgumentCaptor<MLModelGroupGetResponse> captor = ArgumentCaptor.forClass(MLModelGroupGetResponse.class);
        verify(actionListener).onResponse(captor.capture());
        MLModelGroupGetResponse resp = captor.getValue();
        assertNotNull(resp);
        assertNotNull(resp.getMlModelGroup());
        assertEquals("modelGroup", resp.getMlModelGroup().getName());
    }

    public void test_Get_RSC_FeatureEnabled_TypeDisabled_UsesLegacyValidation() throws IOException {
        // Feature enabled globally but TYPE disabled → legacy path
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);

        when(rsc.isFeatureEnabledForType(any())).thenReturn(false);

        // Allow legacy access validation to pass
        doAnswer(inv -> {
            ActionListener<Boolean> l = inv.getArgument(4);
            l.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), anyString(), anyString(), any(), any());

        String tenantId = "t-2";
        MLModelGroupGetRequest req = MLModelGroupGetRequest.builder().modelGroupId("mg-456").tenantId(tenantId).build();

        GetResponse getResponse = prepareMLModelGroup();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelGroupTransportAction.doExecute(null, req, actionListener);

        // Legacy validation MUST run
        verify(modelAccessControlHelper, times(1)).validateModelGroupAccess(any(), eq("mg-456"), anyString(), eq(client), any());

        // Successful response
        ArgumentCaptor<MLModelGroupGetResponse> captor = ArgumentCaptor.forClass(MLModelGroupGetResponse.class);
        verify(actionListener, times(1)).onResponse(captor.capture());
        assertEquals("modelGroup", captor.getValue().getMlModelGroup().getName());
    }

    public void test_Get_RSC_FeatureDisabled_UsesLegacyValidation() throws IOException {
        // Entire feature disabled → legacy path
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);

        // Allow legacy access validation to pass
        doAnswer(inv -> {
            ActionListener<Boolean> l = inv.getArgument(4);
            l.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), anyString(), anyString(), any(), any());

        String tenantId = "t-3";
        MLModelGroupGetRequest req = MLModelGroupGetRequest.builder().modelGroupId("mg-789").tenantId(tenantId).build();

        GetResponse getResponse = prepareMLModelGroup();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelGroupTransportAction.doExecute(null, req, actionListener);

        // Legacy validation MUST run
        verify(modelAccessControlHelper, times(1)).validateModelGroupAccess(any(), eq("mg-789"), anyString(), eq(client), any());

        // Successful response
        ArgumentCaptor<MLModelGroupGetResponse> captor = ArgumentCaptor.forClass(MLModelGroupGetResponse.class);
        verify(actionListener, times(1)).onResponse(captor.capture());
        assertEquals("modelGroup", captor.getValue().getMlModelGroup().getName());
    }

    public GetResponse prepareMLModelGroup() throws IOException {
        MLModelGroup mlModelGroup = MLModelGroup
            .builder()
            .modelGroupId("test_id")
            .name("modelGroup")
            .description("this is an example description")
            .latestVersion(1)
            .access("private")
            .build();
        XContentBuilder content = mlModelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
