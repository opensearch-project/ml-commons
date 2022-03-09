/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.opensearch.common.Strings;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;

public class RestMLSearchModelActionTests extends OpenSearchTestCase {

    private RestMLSearchModelAction restMLSearchModelAction;

    @Before
    public void setup() {
        restMLSearchModelAction = new RestMLSearchModelAction();
    }

    public void testConstructor() {
        RestMLSearchModelAction mlSearchModelAction = new RestMLSearchModelAction();
        assertNotNull(mlSearchModelAction);
    }

    public void testGetName() {
        String actionName = restMLSearchModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_model_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route postRoute = routes.get(0);
        assertEquals(RestRequest.Method.POST, postRoute.getMethod());
        assertThat(postRoute.getMethod(), Matchers.either(Matchers.is(RestRequest.Method.POST)).or(Matchers.is(RestRequest.Method.GET)));
        assertEquals("/_plugins/_ml/models/_search", postRoute.getPath());
    }
}
