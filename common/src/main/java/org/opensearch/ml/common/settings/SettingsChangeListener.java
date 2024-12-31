/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

public interface SettingsChangeListener {
    void onMultiTenancyEnabledChanged(boolean isEnabled);
}
