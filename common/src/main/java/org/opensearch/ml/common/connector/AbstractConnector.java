/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Getter;

import java.util.Map;

public abstract class AbstractConnector implements Connector {
    @Getter
    protected String httpMethod;
    @Getter
    protected Map<String, String> parameters;
    protected Map<String, String> credential;
    protected Map<String, String> decryptedHeaders;

    public Map<String, String> createHeaders() {
        return decryptedHeaders;
    }
}
