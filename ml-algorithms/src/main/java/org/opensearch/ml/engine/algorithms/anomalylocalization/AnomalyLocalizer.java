/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.execute.anomalylocalization.AnomalyLocalizationInput;
import org.opensearch.ml.common.output.execute.anomalylocalization.AnomalyLocalizationOutput;

/**
 * Localizes aggregate anomalies.
 */
public interface AnomalyLocalizer {

    /**
     * Gets localized entity results for data specified in the input.
     *
     * @param input    Information about aggregation and metadata.
     * @param listener Listener to localized details or exception.
     */
    void getLocalizationResults(AnomalyLocalizationInput input, ActionListener<AnomalyLocalizationOutput> listener);
}
