package org.opensearch.ml.rest;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.Strings;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;

public class RestMLSearchActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLSearchAction restMLSearchAction;

    @Before
    public void setup() {
        restMLSearchAction = new RestMLSearchAction();
    }

    @Test
    public void testConstructor() {
        RestMLSearchAction restMLSearchAction = new RestMLSearchAction();
        assertNotNull(restMLSearchAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLSearchAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/_search/", route.getPath());
    }
}
