/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.getUpdatePromptRestRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

import com.google.gson.Gson;

public class RestMLUpdatePromptActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLUpdatePromptAction restMLUpdatePromptAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLUpdatePromptAction = new RestMLUpdatePromptAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUpdatePromptAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUpdatePromptAction restMLUpdatePromptAction = new RestMLUpdatePromptAction(mlFeatureEnabledSetting);
        assertNotNull(restMLUpdatePromptAction);
    }

    public void testGetName() {
        String actionName = restMLUpdatePromptAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_prompt_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdatePromptAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/prompts/{prompt_id}", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getUpdatePromptRestRequest(null);
        MLUpdatePromptRequest mlUpdatePromptRequest = restMLUpdatePromptAction.getRequest(request);

        MLUpdatePromptInput mlUpdatePromptInput = mlUpdatePromptRequest.getMlUpdatePromptInput();
        verifyParsedUpdatePromptInput(mlUpdatePromptInput);
    }

    public void testGetRequest_MultiTenancyEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getUpdatePromptRestRequest("tenantId");
        MLUpdatePromptRequest mlUpdatePromptRequest = restMLUpdatePromptAction.getRequest(request);

        MLUpdatePromptInput mlUpdatePromptInput = mlUpdatePromptRequest.getMlUpdatePromptInput();
        verifyParsedUpdatePromptInput(mlUpdatePromptInput);
        assertEquals("tenantId", mlUpdatePromptInput.getTenantId());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getUpdatePromptRestRequest(null);
        restMLUpdatePromptAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLUpdatePromptRequest> argumentcaptor = ArgumentCaptor.forClass(MLUpdatePromptRequest.class);
        verify(client, times(1)).execute(eq(MLUpdatePromptAction.INSTANCE), argumentcaptor.capture(), any());
        MLUpdatePromptInput mlUpdatePromptInput = argumentcaptor.getValue().getMlUpdatePromptInput();
        verifyParsedUpdatePromptInput(mlUpdatePromptInput);
    }

    public void testUpdatePromptRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Update prompt request has empty body");
        RestRequest request = getRestRequestWithEmptyContent();
        restMLUpdatePromptAction.handleRequest(request, channel, client);
    }

    public void testUpdatePromptRequestWithNullPromptId() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain prompt_id");
        RestRequest request = getRestRequestWithNullPromptId();
        restMLUpdatePromptAction.handleRequest(request, channel, client);
    }

    private static void verifyParsedUpdatePromptInput(MLUpdatePromptInput mlUpdatePromptInput) {
        Map<String, String> expectedPrompt = new HashMap<>();
        expectedPrompt.put("system", "update system prompt");
        expectedPrompt.put("user", "update user prompt");
        assertEquals("update_prompt", mlUpdatePromptInput.getName());
        assertEquals(expectedPrompt, mlUpdatePromptInput.getPrompt());
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.PUT;
        Map<String, String> params = new HashMap<>();
        params.put("prompt_id", "test_prompt_id");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/prompts/{prompt_id}")
            .withParams(params)
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithNullPromptId() {
        RestRequest.Method method = RestRequest.Method.PUT;
        final Map<String, Object> updateContent = Map.of("name", "updated_name");
        String requestContent = new Gson().toJson(updateContent).toString();
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/prompts/{prompt_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }
}
