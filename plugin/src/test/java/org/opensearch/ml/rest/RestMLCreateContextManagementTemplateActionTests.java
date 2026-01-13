/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLCreateContextManagementTemplateActionTests extends OpenSearchTestCase {
    private RestMLCreateContextManagementTemplateAction restAction;
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
        restAction = new RestMLCreateContextManagementTemplateAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            // Mock successful execution - actionListener not used in this test
            return null;
        }).when(client).execute(eq(MLCreateContextManagementTemplateAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLCreateContextManagementTemplateAction action = new RestMLCreateContextManagementTemplateAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_create_context_management_template_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/context_management/{template_name}", route.getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateContextManagementTemplateRequest> argumentCaptor = ArgumentCaptor
            .forClass(MLCreateContextManagementTemplateRequest.class);
        verify(client, times(1)).execute(eq(MLCreateContextManagementTemplateAction.INSTANCE), argumentCaptor.capture(), any());
        String templateName = argumentCaptor.getValue().getTemplateName();
        assertEquals("test_template", templateName);
    }

    public void testPrepareRequestReturnsRestChannelConsumer() throws Exception {
        RestRequest request = getRestRequest();
        Object consumer = restAction.prepareRequest(request, client);

        assertNotNull(consumer);

        // Execute the consumer to test the actual execution path using reflection
        java.lang.reflect.Method acceptMethod = consumer.getClass().getMethod("accept", Object.class);
        acceptMethod.invoke(consumer, channel);

        ArgumentCaptor<MLCreateContextManagementTemplateRequest> argumentCaptor = ArgumentCaptor
            .forClass(MLCreateContextManagementTemplateRequest.class);
        verify(client, times(1)).execute(eq(MLCreateContextManagementTemplateAction.INSTANCE), argumentCaptor.capture(), any());
        String templateName = argumentCaptor.getValue().getTemplateName();
        assertEquals("test_template", templateName);
    }

    public void testPrepareRequestWithAgentFrameworkDisabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        
        assertThrows(IllegalStateException.class, () -> restAction.handleRequest(request, channel, client));
    }

    public void testGetRequestWithAgentFrameworkDisabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> restAction.getRequest(request));
        assertEquals("Agent framework is disabled", exception.getMessage());
    }

    public void testGetRequestWithMissingTemplateName() {
        Map<String, String> params = new HashMap<>();
        // No template name parameter
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(params)
            .withContent(new BytesArray(getValidTemplateContent()), XContentType.JSON)
            .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Template name is required", exception.getMessage());
    }

    public void testGetRequestWithEmptyTemplateName() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(params)
            .withContent(new BytesArray(getValidTemplateContent()), XContentType.JSON)
            .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Template name is required", exception.getMessage());
    }

    public void testGetRequestWithWhitespaceOnlyTemplateName() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "   ");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(params)
            .withContent(new BytesArray(getValidTemplateContent()), XContentType.JSON)
            .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Template name is required", exception.getMessage());
    }

    public void testGetRequestWithInvalidJsonContent() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "test_template");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(params)
            .withContent(new BytesArray("invalid json"), XContentType.JSON)
            .build();

        assertThrows(Exception.class, () -> restAction.getRequest(request));
    }

    public void testGetRequestWithValidInput() throws Exception {
        RestRequest request = getRestRequest();
        MLCreateContextManagementTemplateRequest result = restAction.getRequest(request);

        assertNotNull(result);
        assertEquals("test_template", result.getTemplateName());
        assertNotNull(result.getTemplate());
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "test_template");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(params)
            .withContent(new BytesArray(getValidTemplateContent()), XContentType.JSON)
            .build();
    }

    private String getValidTemplateContent() {
        return "{\n"
            + "  \"description\": \"Test template\",\n"
            + "  \"hooks\": {\n"
            + "    \"PRE_LLM\": [\n"
            + "      {\n"
            + "        \"type\": \"SummarizationManager\",\n"
            + "        \"config\": {\n"
            + "          \"summary_ratio\": 0.3,\n"
            + "          \"preserve_recent_messages\": 10\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}";
    }
}
