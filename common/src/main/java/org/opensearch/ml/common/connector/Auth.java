/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

public enum Auth {
    BASIC,
    API_KEY,
    SIGv4;

    public static Auth from(String value) {
        try {
            return Auth.valueOf(value);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Authentication method not supported!");
        }

    }
}
