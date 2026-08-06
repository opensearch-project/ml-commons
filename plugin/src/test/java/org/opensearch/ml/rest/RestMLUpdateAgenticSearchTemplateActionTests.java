/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateRequest;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLUpdateAgenticSearchTemplateActionTests extends OpenSearchTestCase {
    private RestMLUpdateAgenticSearchTemplateAction restAction;
    private NodeClient client;
    private ThreadPool threadPool;
    private AutoCloseable mocks;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()).thenReturn(true);
        restAction = new RestMLUpdateAgenticSearchTemplateAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> null).when(client).execute(eq(MLUpdateAgenticSearchTemplateAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        client.close();
        threadPool.shutdown();
        mocks.close();
        super.tearDown();
    }

    public void testGetName() {
        assertFalse(Strings.isNullOrEmpty(restAction.getName()));
        assertEquals("ml_update_agentic_search_template_action", restAction.getName());
    }

    public void testRoutes() {
        RestHandler.Route route = restAction.routes().get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/agentic_search_templates/{template_id}", route.getPath());
    }

    public void testGetRequestParsesBodyAndPathId() throws Exception {
        RestRequest request = updateRequest("product_search", "{\"description\":\"new desc\"}");
        MLUpdateAgenticSearchTemplateRequest result = restAction.getRequest(request);
        assertEquals("product_search", result.getTemplateId());
        assertNotNull(result.getTemplate());
        // The URL id is authoritative on the parsed patch.
        assertEquals("product_search", result.getTemplate().getTemplateId());
        assertEquals("new desc", result.getTemplate().getDescription());
    }

    public void testGetRequestMissingTemplateId() throws Exception {
        RestRequest request = updateRequest("", "{\"description\":\"d\"}");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Template id is required", e.getMessage());
    }

    public void testGetRequestMissingBody() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(pathParams("t")).build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Update body is required", e.getMessage());
    }

    public void testGetRequestAgentFrameworkDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestRequest request = updateRequest("t", "{\"description\":\"d\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(request));
        assertEquals("Agent framework is disabled", e.getMessage());
    }

    public void testGetRequestAgenticSearchTemplateDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        RestRequest request = updateRequest("t", "{\"description\":\"d\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(request));
        assertTrue(e.getMessage().contains("agentic search template APIs are not enabled"));
    }

    private Map<String, String> pathParams(String templateId) {
        Map<String, String> params = new HashMap<>();
        params.put(RestActionUtils.PARAMETER_TEMPLATE_ID, templateId);
        return params;
    }

    private RestRequest updateRequest(String templateId, String json) {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(pathParams(templateId))
            .withContent(new BytesArray(json), XContentType.JSON)
            .build();
    }
}
