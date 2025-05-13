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
import static org.opensearch.ml.utils.TestHelper.getCreatePromptRestRequest;
import static org.opensearch.ml.utils.TestHelper.verifyParsedCreatePromptInput;

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
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLCreatePromptActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLCreatePromptAction restMLCreatePromptAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLCreatePromptAction = new RestMLCreatePromptAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLCreatePromptResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLCreatePromptAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLCreatePromptAction mlCreatePromptAction = new RestMLCreatePromptAction(mlFeatureEnabledSetting);
        assertNotNull(mlCreatePromptAction);
    }

    public void testGetName() {
        String actionName = restMLCreatePromptAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_create_prompt_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLCreatePromptAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/prompts/_create", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getCreatePromptRestRequest(null);
        MLCreatePromptRequest mlCreatePromptRequest = restMLCreatePromptAction.getRequest(request);

        MLCreatePromptInput mlCreatePromptInput = mlCreatePromptRequest.getMlCreatePromptInput();
        verifyParsedCreatePromptInput(mlCreatePromptInput);
    }

    public void testGetRequest_MultiTenancyEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getCreatePromptRestRequest("tenantId");
        MLCreatePromptRequest mlCreatePromptrequest = restMLCreatePromptAction.getRequest(request);

        MLCreatePromptInput mlCreatePromptInput = mlCreatePromptrequest.getMlCreatePromptInput();
        verifyParsedCreatePromptInput(mlCreatePromptInput);
        assertEquals("tenantId", mlCreatePromptInput.getTenantId());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getCreatePromptRestRequest(null);
        restMLCreatePromptAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreatePromptRequest> argumentCaptor = ArgumentCaptor.forClass(MLCreatePromptRequest.class);
        verify(client, times(1)).execute(eq(MLCreatePromptAction.INSTANCE), argumentCaptor.capture(), any());
        MLCreatePromptInput mlCreatePromptInput = argumentCaptor.getValue().getMlCreatePromptInput();
        verifyParsedCreatePromptInput(mlCreatePromptInput);
    }

    public void testPrepareRequest_EmptyContent() throws Exception {
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage("Create Prompt request has empty body");
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restMLCreatePromptAction.handleRequest(request, channel, client);
    }
}
