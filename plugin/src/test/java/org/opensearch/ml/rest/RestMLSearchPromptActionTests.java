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

public class RestMLSearchPromptActionTests extends OpenSearchTestCase {

    private RestMLSearchPromptAction restMLSearchPromptAction;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        restMLSearchPromptAction = new RestMLSearchPromptAction(mlFeatureEnabledSetting);
    }

    public void testConstructor() {
        RestMLSearchPromptAction restMLSearchPromptAction = new RestMLSearchPromptAction(mlFeatureEnabledSetting);
        assertNotNull(restMLSearchPromptAction);
    }

    public void testGetName() {
        String actionName = restMLSearchPromptAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_prompt_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchPromptAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertThat(route.getMethod(), Matchers.either(Matchers.is(RestRequest.Method.POST)).or(Matchers.is(RestRequest.Method.GET)));
        assertEquals("/_plugins/_ml/prompts/_search", route.getPath());
    }
}
