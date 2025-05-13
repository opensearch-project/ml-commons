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
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_PROMPT_ID;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptGetAction;
import org.opensearch.ml.common.transport.prompt.MLPromptGetRequest;
import org.opensearch.ml.common.transport.prompt.MLPromptGetResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLGetPromptActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLGetPromptAction restMLGetPromptAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLGetPromptAction = new RestMLGetPromptAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLPromptGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLPromptGetAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLGetPromptAction mlGetPromptAction = new RestMLGetPromptAction(mlFeatureEnabledSetting);
        assertNotNull(mlGetPromptAction);
    }

    public void testGetName() {
        String actionName = restMLGetPromptAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_prompt_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetPromptAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/prompts/{prompt_id}", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getRestRequest(null);
        MLPromptGetRequest mlPromptGetRequest = restMLGetPromptAction.getRequest(request);

        String promptId = mlPromptGetRequest.getPromptId();
        assertEquals(promptId, "prompt_id");
    }

    public void testGetRequest_MultiTenancyEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getRestRequest("tenantId");
        MLPromptGetRequest mlPromptGetRequest = restMLGetPromptAction.getRequest(request);

        String promptId = mlPromptGetRequest.getPromptId();
        String tenantId = mlPromptGetRequest.getTenantId();
        assertEquals("tenantId", tenantId);
        assertEquals("prompt_id", promptId);
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest(null);
        restMLGetPromptAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLPromptGetRequest> argumentcaptor = ArgumentCaptor.forClass(MLPromptGetRequest.class);
        verify(client, times(1)).execute(eq(MLPromptGetAction.INSTANCE), argumentcaptor.capture(), any());
        String promptId = argumentcaptor.getValue().getPromptId();
        assertEquals(promptId, "prompt_id");
    }

    public void testPrepareRequest_EmptyContent() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain prompt_id");
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restMLGetPromptAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest(String tenantId) {
        Map<String, String> params = new HashMap<>();
        Map<String, List<String>> headers = new HashMap<>();
        params.put(PARAMETER_PROMPT_ID, "prompt_id");
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        }
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(headers).withParams(params).build();
    }
}
