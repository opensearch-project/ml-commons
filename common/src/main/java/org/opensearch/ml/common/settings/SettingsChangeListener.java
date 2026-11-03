/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

/**
 * Interface for handling settings changes in the OpenSearch ML plugin.
 */
public interface SettingsChangeListener {
    /**
     * Callback method that gets triggered when the multi-tenancy setting changes.
     *
     * @param isEnabled A boolean value indicating the new state of the multi-tenancy setting:
     *                  <ul>
     *                    <li><code>true</code> if multi-tenancy is enabled</li>
     *                    <li><code>false</code> if multi-tenancy is disabled</li>
     *                  </ul>
     */
    default void onMultiTenancyEnabledChanged(boolean isEnabled) {
        // do nothing
    }

    /**
     * Callback method that gets triggered when the static metric collection setting changes.
     *
     * @param isEnabled A boolean value indicating the new state of the static metric collection setting:
     *                  <ul>
     *                    <li><code>true</code> if static metric collection is enabled</li>
     *                    <li><code>false</code> if static metric collection is disabled</li>
     *                  </ul>
     */
    default void onStaticMetricCollectionEnabledChanged(boolean isEnabled) {
        // do nothing
    }
}
