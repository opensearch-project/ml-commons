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

public class RestMLGetTaskActionTests extends OpenSearchTestCase {

    private RestMLGetTaskAction restMLGetTaskAction;

    @Before
    public void setup() {
        restMLGetTaskAction = new RestMLGetTaskAction();
    }

    public void testConstructor() {
        RestMLGetTaskAction mlGetTaskAction = new RestMLGetTaskAction();
        assertNotNull(mlGetTaskAction);
    }

    public void testGetName() {
        String actionName = restMLGetTaskAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_task_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetTaskAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/tasks/{task_id}", route.getPath());
    }
}
