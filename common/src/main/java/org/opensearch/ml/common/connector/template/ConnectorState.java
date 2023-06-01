/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.template;

public enum ConnectorState {
    CREATED,
    DEPLOYED,
    CREATE_FAILED,
    DEPLOY_FAILED;

    public static ConnectorState from(String value) {
        try {
            return ConnectorState.valueOf(value);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Wrong Connector State!");
        }

    }
}
