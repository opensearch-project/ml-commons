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

public class RestMLDeleteTaskActionTests extends OpenSearchTestCase {

    private RestMLDeleteTaskAction restMLDeleteTaskAction;

    @Before
    public void setup() {
        restMLDeleteTaskAction = new RestMLDeleteTaskAction();
    }

    public void testConstructor() {
        RestMLDeleteTaskAction mlDeleteTaskAction = new RestMLDeleteTaskAction();
        assertNotNull(mlDeleteTaskAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteTaskAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_task_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteTaskAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/tasks/{task_id}", route.getPath());
    }
}
