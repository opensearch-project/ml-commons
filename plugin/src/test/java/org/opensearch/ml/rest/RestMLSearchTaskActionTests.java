/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.mockito.Mock;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;

public class RestMLSearchTaskActionTests extends OpenSearchTestCase {

    private RestMLSearchTaskAction restMLSearchTaskAction;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        restMLSearchTaskAction = new RestMLSearchTaskAction(mlFeatureEnabledSetting);
    }

    public void testConstructor() {
        RestMLSearchTaskAction mlSearchTaskAction = new RestMLSearchTaskAction(mlFeatureEnabledSetting);
        assertNotNull(mlSearchTaskAction);
    }

    public void testGetName() {
        String actionName = restMLSearchTaskAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_task_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchTaskAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route postRoute = routes.get(0);
        assertEquals(RestRequest.Method.POST, postRoute.getMethod());
        assertThat(postRoute.getMethod(), Matchers.either(Matchers.is(RestRequest.Method.POST)).or(Matchers.is(RestRequest.Method.GET)));
        assertEquals("/_plugins/_ml/tasks/_search", postRoute.getPath());
    }
}
