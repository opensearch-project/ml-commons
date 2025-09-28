/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.STREAM_DISABLED_ERR_MSG;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.rest.FakeRestRequest;

public class RestMLPredictionStreamActionTests {

    private RestMLPredictionStreamAction restAction;
    private MLModelManager modelManager;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private ClusterService clusterService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        modelManager = mock(MLModelManager.class);
        mlFeatureEnabledSetting = mock(MLFeatureEnabledSetting.class);
        clusterService = mock(ClusterService.class);
        restAction = new RestMLPredictionStreamAction(modelManager, mlFeatureEnabledSetting, clusterService);
    }

    @Test
    public void testGetName() {
        assertEquals("ml_prediction_stream_action", restAction.getName());
    }

    @Test
    public void testRoutes() {
        List<RestMLPredictionStreamAction.Route> routes = restAction.routes();
        assertEquals(1, routes.size());

        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertTrue(routes.get(0).getPath().contains("/models/"));
        assertTrue(routes.get(0).getPath().contains("/_predict/stream"));
    }

    @Test
    public void testConstructor() {
        assertNotNull(restAction);
        RestMLPredictionStreamAction newAction = new RestMLPredictionStreamAction(modelManager, mlFeatureEnabledSetting, clusterService);
        assertNotNull(newAction);
        assertEquals("ml_prediction_stream_action", newAction.getName());
    }

    @Test
    public void testPrepareRequestWhenStreamDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(false);
        FakeRestRequest request = createFakeRestRequestWithValidContent("/_plugins/_ml/models/test-model/_predict/stream");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> restAction.prepareRequest(request, null)
        );
        assertEquals(STREAM_DISABLED_ERR_MSG, exception.getMessage());
    }

    @Test
    public void testGetRequestSuccessWithRemoteModel() throws IOException {
        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        FakeRestRequest request = createFakeRestRequestWithValidContent("/_plugins/_ml/models/test-model/_predict/stream");
        BytesReference content = request.content();

        MLPredictionTaskRequest result = restAction.getRequest("test-model", "REMOTE", request, content);

        assertNotNull(result);
        assertEquals("test-model", result.getModelId());
        assertNotNull(result.getMlInput());
        verify(mlFeatureEnabledSetting).isRemoteInferenceEnabled();
        verify(mlFeatureEnabledSetting).isMultiTenancyEnabled();
    }

    @Test
    public void testGetRequestWithRemoteModelDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        FakeRestRequest request = createFakeRestRequestWithValidContent("/_plugins/_ml/models/test-model/_predict");
        BytesReference content = request.content();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            restAction.getRequest("test-model", "REMOTE", request, content);
        });
        assertEquals(REMOTE_INFERENCE_DISABLED_ERR_MSG, exception.getMessage());
    }

    private FakeRestRequest createFakeRestRequestWithValidContent(String path) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test-model");

        BytesReference content = BytesReference
            .bytes(
                XContentFactory
                    .jsonBuilder()
                    .startObject()
                    .field("parameters")
                    .startObject()
                    .field("prompt", "Hello world")
                    .endObject()
                    .endObject()
            );

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(path)
            .withParams(params)
            .withContent(content, XContentType.JSON)
            .build();
    }
}
