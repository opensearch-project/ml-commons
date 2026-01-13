/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.contextmanagement.MLUpdateContextManagementTemplateRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLUpdateContextManagementTemplateActionTests extends OpenSearchTestCase {

    private RestMLUpdateContextManagementTemplateAction restAction;

    @Mock
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restAction = new RestMLUpdateContextManagementTemplateAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> { return null; }).when(client).execute(any(), any(), any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/context_management/{template_name}", route.getPath());
    }

    @Test
    public void testGetName() {
        String actionName = restAction.getName();
        assertFalse(actionName.isEmpty());
        assertEquals("ml_update_context_management_template_action", actionName);
    }

    @Test
    public void testPrepareRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "test_template");

        String requestContent = "{"
            + "\"name\": \"test_template\","
            + "\"description\": \"Test template\","
            + "\"context_managers\": []"
            + "}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/test_template")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaTypeRegistry.JSON)
            .build();

        restAction.prepareRequest(request, client);
    }

    @Test
    public void testGetRequest_AgentFrameworkDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "test_template");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/test_template")
            .withParams(params)
            .withContent(new BytesArray("{}"), MediaTypeRegistry.JSON)
            .build();

        IllegalStateException exception = expectThrows(IllegalStateException.class, () -> {
            restAction.getRequest(request);
        });
        assertEquals("Agent framework is disabled", exception.getMessage());
    }

    @Test
    public void testGetRequest_MissingTemplateName() throws Exception {
        Map<String, String> params = new HashMap<>();
        // No template name parameter

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/")
            .withParams(params)
            .withContent(new BytesArray("{}"), MediaTypeRegistry.JSON)
            .build();

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> { restAction.getRequest(request); });
        assertEquals("Template name is required", exception.getMessage());
    }

    @Test
    public void testGetRequest_EmptyTemplateName() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/")
            .withParams(params)
            .withContent(new BytesArray("{}"), MediaTypeRegistry.JSON)
            .build();

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> { restAction.getRequest(request); });
        assertEquals("Template name is required", exception.getMessage());
    }

    @Test
    public void testGetRequest_WhitespaceTemplateName() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "   ");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/")
            .withParams(params)
            .withContent(new BytesArray("{}"), MediaTypeRegistry.JSON)
            .build();

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> { restAction.getRequest(request); });
        assertEquals("Template name is required", exception.getMessage());
    }

    @Test
    public void testGetRequest_ValidRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "test_template");

        String requestContent = "{"
            + "\"name\": \"test_template\","
            + "\"description\": \"Test template\","
            + "\"context_managers\": []"
            + "}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/test_template")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaTypeRegistry.JSON)
            .build();

        MLUpdateContextManagementTemplateRequest result = restAction.getRequest(request);
        assertNotNull(result);
        assertEquals("test_template", result.getTemplateName());
        assertNotNull(result.getTemplate());
    }
}
