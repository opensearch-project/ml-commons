/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLSearchMemoryContainerActionTests extends OpenSearchTestCase {

    private RestMLSearchMemoryContainerAction restMLSearchMemoryContainerAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true); // Enable by default for tests
        restMLSearchMemoryContainerAction = new RestMLSearchMemoryContainerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = new NodeClient(Settings.EMPTY, threadPool);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLSearchMemoryContainerAction action = new RestMLSearchMemoryContainerAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restMLSearchMemoryContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_memory_container_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchMemoryContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        // AbstractMLSearchAction creates both POST and GET routes for each path
        assertEquals(2, routes.size());

        // First route should be POST
        RestHandler.Route postRoute = routes.get(0);
        assertEquals(RestRequest.Method.POST, postRoute.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/_search", postRoute.getPath());

        // Second route should be GET
        RestHandler.Route getRoute = routes.get(1);
        assertEquals(RestRequest.Method.GET, getRoute.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/_search", getRoute.getPath());
    }

    public void testPrepareRequestWithAgenticMemoryDisabled() {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        String requestBody = "{\n"
            + "  \"query\": {\n"
            + "    \"match_all\": {}\n"
            + "  }\n"
            + "}";

        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withContent(new BytesArray(requestBody), XContentType.JSON)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_search")
            .build();

        FakeRestChannel channel = new FakeRestChannel(request, false, 0);

        // Expect OpenSearchStatusException when feature is disabled
        OpenSearchStatusException exception = expectThrows(
            OpenSearchStatusException.class,
            () -> restMLSearchMemoryContainerAction.handleRequest(request, channel, client)
        );

        assertEquals(RestStatus.FORBIDDEN, exception.status());
        assertTrue(
            exception.getMessage().contains("The Agentic Memory APIs are not enabled")
        );
    }

    public void testPrepareRequestWithAgenticMemoryEnabled() {
        // Feature is enabled by default in setup
        String requestBody = "{}"; // Simple empty query

        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withContent(new BytesArray(requestBody), XContentType.JSON)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_search")
            .build();

        FakeRestChannel channel = new FakeRestChannel(request, false, 1);

        // This should not throw an exception
        try {
            restMLSearchMemoryContainerAction.handleRequest(request, channel, client);
            // If we get here without exception, the feature check passed
            // The actual search execution will fail due to missing setup, but that's OK for this test
        } catch (OpenSearchStatusException e) {
            // If it's a FORBIDDEN status, the feature check failed
            if (e.status() == RestStatus.FORBIDDEN) {
                fail("Feature check should have passed when agentic memory is enabled");
            }
            // Other exceptions are OK - they're from the actual search execution
        } catch (Exception e) {
            // Other exceptions are expected since we don't have full search infrastructure set up
            // As long as it's not a FORBIDDEN from the feature check, we're good
        }
    }
}
