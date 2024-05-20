package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.rest.RestStatus;

import java.io.IOException;

public class RetryableException extends OpenSearchStatusException {
    public RetryableException(String msg, RestStatus status, Throwable cause, Object... args) {
        super(msg, status, cause, args);
    }

    public RetryableException(String msg, RestStatus status, Object... args) {
        this(msg, status, null, args);
    }

    public RetryableException(StreamInput in) throws IOException {
        super(in);
    }
}
