/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * This class encapsulates several parameters that are used in a split-batch request case.
 * A batch request is that in neural-search side multiple fields are send in one request to ml-commons,
 * but the remote model doesn't accept list of string inputs so in ml-commons the request needs split.
 * sequence is used to identify the index of the split request.
 * countDownLatch is used to wait for all the split requests to finish.
 * exceptionHolder is used to hold any exception thrown in a split-batch request.
 */
@Data
@AllArgsConstructor
public class ExecutionContext {
    // Should never be null
    private int sequence;
    private CountDownLatch countDownLatch;
    // This is to hold any exception thrown in a split-batch request
    private AtomicReference<Exception> exceptionHolder;
}
