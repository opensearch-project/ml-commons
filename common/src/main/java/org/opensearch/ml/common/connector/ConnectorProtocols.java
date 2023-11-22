/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.Arrays;
import java.util.List;

public class ConnectorProtocols {

    public static final String HTTP = "http";
    public static final String AWS_SIGV4 = "aws_sigv4";

    public static final List<String> VALID_PROTOCOLS = Arrays.asList(AWS_SIGV4, HTTP);

    public static void validateProtocol(String protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException(
                "Connector protocol is null. Please use one of " + Arrays.toString(VALID_PROTOCOLS.toArray(new String[0]))
            );
        }
        if (!VALID_PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException(
                "Unsupported connector protocol. Please use one of " + Arrays.toString(VALID_PROTOCOLS.toArray(new String[0]))
            );
        }
    }
}
