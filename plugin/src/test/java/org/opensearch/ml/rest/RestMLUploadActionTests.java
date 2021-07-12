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

public class RestMLUploadActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLUploadAction restMLUploadAction;

    @Before
    public void setup() {
        restMLUploadAction = new RestMLUploadAction();
    }

    @Test
    public void testConstructor() {
        RestMLUploadAction restMLUploadAction = new RestMLUploadAction();
        assertNotNull(restMLUploadAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLUploadAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_upload_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUploadAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_upload/", route.getPath());
    }
}
