/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.util.List;

import org.junit.Before;
import org.opensearch.common.Strings;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;

public class RestMLDeleteModelActionTests extends OpenSearchTestCase {

    private RestMLDeleteModelAction restMLDeleteModelAction;

    @Before
    public void setup() {
        restMLDeleteModelAction = new RestMLDeleteModelAction();
    }

    public void testConstructor() {
        RestMLDeleteModelAction mlDeleteModelAction = new RestMLDeleteModelAction();
        assertNotNull(mlDeleteModelAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_model_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}", route.getPath());
    }
}
