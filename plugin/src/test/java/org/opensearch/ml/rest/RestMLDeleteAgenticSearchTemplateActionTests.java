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
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateRequest;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLDeleteAgenticSearchTemplateActionTests extends OpenSearchTestCase {
    private RestMLDeleteAgenticSearchTemplateAction restAction;
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
        restAction = new RestMLDeleteAgenticSearchTemplateAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> null).when(client).execute(eq(MLDeleteAgenticSearchTemplateAction.INSTANCE), any(), any());
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
        assertEquals("ml_delete_agentic_search_template_action", restAction.getName());
    }

    public void testRoutes() {
        RestHandler.Route route = restAction.routes().get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/agentic_search_templates/{template_id}", route.getPath());
    }

    public void testGetRequestReadsPathParam() {
        MLDeleteAgenticSearchTemplateRequest result = restAction.getRequest(idRequest("product_search"));
        assertEquals("product_search", result.getTemplateId());
    }

    public void testGetRequestMissingTemplateId() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(idRequest("")));
        assertEquals("Template id is required", e.getMessage());
    }

    public void testGetRequestAgentFrameworkDisabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(idRequest("t")));
        assertEquals("Agent framework is disabled", e.getMessage());
    }

    public void testGetRequestAgenticSearchTemplateDisabled() {
        when(mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(idRequest("t")));
        assertTrue(e.getMessage().contains("agentic search template APIs are not enabled"));
    }

    private RestRequest idRequest(String templateId) {
        Map<String, String> params = new HashMap<>();
        params.put(RestActionUtils.PARAMETER_TEMPLATE_ID, templateId);
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
