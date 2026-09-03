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
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesAction;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesRequest;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLListAgenticSearchTemplatesActionTests extends OpenSearchTestCase {
    private RestMLListAgenticSearchTemplatesAction restAction;
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
        restAction = new RestMLListAgenticSearchTemplatesAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> null).when(client).execute(eq(MLListAgenticSearchTemplatesAction.INSTANCE), any(), any());
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
        assertEquals("ml_list_agentic_search_templates_action", restAction.getName());
    }

    public void testRoutes() {
        RestHandler.Route route = restAction.routes().get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/agentic_search_templates", route.getPath());
    }

    public void testGetRequestDefaultsWhenNoParams() {
        MLListAgenticSearchTemplatesRequest result = restAction.getRequest(listRequest(null, null));
        assertEquals(0, result.getFrom());
        assertEquals(10, result.getSize());
    }

    public void testGetRequestReadsParams() {
        MLListAgenticSearchTemplatesRequest result = restAction.getRequest(listRequest("5", "25"));
        assertEquals(5, result.getFrom());
        assertEquals(25, result.getSize());
    }

    public void testGetRequestNegativeFrom() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(listRequest("-1", "10")));
        assertEquals("Parameter 'from' must be non-negative", e.getMessage());
    }

    public void testGetRequestSizeTooLarge() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(listRequest("0", "1001")));
        assertEquals("Parameter 'size' must be between 1 and 1000", e.getMessage());
    }

    public void testGetRequestSizeZero() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(listRequest("0", "0")));
        assertEquals("Parameter 'size' must be between 1 and 1000", e.getMessage());
    }

    public void testGetRequestAgentFrameworkDisabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(listRequest(null, null)));
        assertEquals("Agent framework is disabled", e.getMessage());
    }

    public void testGetRequestAgenticSearchTemplateDisabled() {
        when(mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> restAction.getRequest(listRequest(null, null)));
        assertTrue(e.getMessage().contains("agentic search template APIs are not enabled"));
    }

    private RestRequest listRequest(String from, String size) {
        Map<String, String> params = new HashMap<>();
        if (from != null) {
            params.put("from", from);
        }
        if (size != null) {
            params.put("size", size);
        }
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
