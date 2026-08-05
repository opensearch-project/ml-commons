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

import java.util.Collections;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateRequest;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLRegisterAgenticSearchTemplateActionTests extends OpenSearchTestCase {
    private RestMLRegisterAgenticSearchTemplateAction restAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()).thenReturn(true);
        restAction = new RestMLRegisterAgenticSearchTemplateAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> null).when(client).execute(eq(MLRegisterAgenticSearchTemplateAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertFalse(Strings.isNullOrEmpty(restAction.getName()));
        assertEquals("ml_register_agentic_search_template_action", restAction.getName());
    }

    public void testRoutes() {
        RestHandler.Route route = restAction.routes().get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/agentic_search_templates", route.getPath());
    }

    public void testGetRequestParsesBody() throws Exception {
        RestRequest request = bodyRequest("{\"template_name\":\"product_search\",\"index\":\"products\",\"description\":\"desc\"}");
        MLRegisterAgenticSearchTemplateRequest result = restAction.getRequest(request);
        assertEquals("product_search", result.getTemplateId());
        assertEquals("products", result.getIndex());
        assertEquals("desc", result.getDescription());
    }

    public void testGetRequestMissingTemplateName() throws Exception {
        RestRequest request = bodyRequest("{\"index\":\"products\"}");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("'template_name' is required", e.getMessage());
    }

    public void testGetRequestMissingIndex() throws Exception {
        RestRequest request = bodyRequest("{\"template_name\":\"product_search\"}");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("'index' is required", e.getMessage());
    }

    public void testGetRequestAgentFrameworkDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestRequest request = bodyRequest("{\"template_name\":\"t\",\"index\":\"i\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(request));
        assertEquals("Agent framework is disabled", e.getMessage());
    }

    public void testGetRequestAgenticSearchTemplateDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        RestRequest request = bodyRequest("{\"template_name\":\"t\",\"index\":\"i\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(request));
        assertTrue(e.getMessage().contains("agentic search template APIs are not enabled"));
    }

    private RestRequest bodyRequest(String json) {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(new BytesArray(json), XContentType.JSON)
            .withParams(Collections.emptyMap())
            .build();
    }
}
