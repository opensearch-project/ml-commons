/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;

import com.google.common.collect.ImmutableList;

public class RestMLSearchConnectorAction extends AbstractMLSearchAction<Connector> {
    private static final String ML_SEARCH_CONNECTOR_ACTION = "ml_search_connector_action";
    private static final String SEARCH_CONNECTOR_PATH = ML_BASE_URI + "/connectors/_search";

    public RestMLSearchConnectorAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(
            ImmutableList.of(SEARCH_CONNECTOR_PATH),
            ML_CONNECTOR_INDEX,
            Connector.class,
            MLConnectorSearchAction.INSTANCE,
            mlFeatureEnabledSetting
        );
    }

    @Override
    public String getName() {
        return ML_SEARCH_CONNECTOR_ACTION;
    }
}
