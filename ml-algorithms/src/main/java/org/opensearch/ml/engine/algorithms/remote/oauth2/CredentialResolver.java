/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import org.opensearch.core.action.ActionListener;

/** Where the values of the {@code ${credential.<key>}} references in {@code auth_config} come from. */
public interface CredentialResolver {
    /** One read of the referenced values. */
    void snapshot(ActionListener<CredentialSnapshot> listener);
}
