/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionType;
import org.opensearch.client.AdminClient;
import org.opensearch.client.ClusterAdminClient;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.client.node.NodeClient;
import org.opensearch.commons.alerting.action.GetAlertsResponse;
import org.opensearch.commons.alerting.model.Alert;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

public class SearchMonitorsToolTests {
    @Mock
    private NodeClient nodeClient;
    @Mock
    private AdminClient adminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;
    @Mock
    private ClusterAdminClient clusterAdminClient;

    private Map<String, String> nullParams;
    private Map<String, String> emptyParams;
    private Map<String, String> nonEmptyParams;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        SearchMonitorsTool.Factory.getInstance().init(nodeClient);

        nullParams = null;
        emptyParams = Collections.emptyMap();
        nonEmptyParams = Map.of("monitorName", "foo");
    }

    // TODO: add tests to trigger run() with different param values

    @Test
    public void testValidate() {
        Tool tool = SearchMonitorsTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(SearchMonitorsTool.TYPE, tool.getType());
        assertTrue(tool.validate(emptyParams));
        assertTrue(tool.validate(nonEmptyParams));
        assertTrue(tool.validate(nullParams));
    }
}
