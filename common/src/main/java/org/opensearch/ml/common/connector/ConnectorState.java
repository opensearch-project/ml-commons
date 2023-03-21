/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

public enum ConnectorState {
    CREATED,
    HEALTH,
    UNHEALTHY;

    public static ConnectorState from(String value) {
        try {
            return ConnectorState.valueOf(value);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Wrong Connector State!");
        }

    }
}
