/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.io.IOException;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.rest.RestStatus;

public class SageMakerThrottlingException extends OpenSearchStatusException {
    public SageMakerThrottlingException(String msg, RestStatus status, Throwable cause, Object... args) {
        super(msg, status, cause, args);
    }

    public SageMakerThrottlingException(String msg, RestStatus status, Object... args) {
        this(msg, status, null, args);
    }

    public SageMakerThrottlingException(StreamInput in) throws IOException {
        super(in);
    }
}
