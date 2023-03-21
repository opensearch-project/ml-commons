/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

public enum Protocol {
    HTTP;

    public static Protocol from(String value) {
        try {
            return Protocol.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong protocol");
        }
    }
}
