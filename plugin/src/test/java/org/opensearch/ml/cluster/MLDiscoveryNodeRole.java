/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.settings.Setting;

public class MLDiscoveryNodeRole extends DiscoveryNodeRole {

    protected MLDiscoveryNodeRole() {
        super("ml", "ml");
    }

    @Override
    public Setting<Boolean> legacySetting() {
        return null;
    }
}
