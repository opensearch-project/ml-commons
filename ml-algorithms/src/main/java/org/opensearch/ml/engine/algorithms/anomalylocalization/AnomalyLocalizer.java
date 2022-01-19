/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import org.opensearch.action.ActionListener;

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
