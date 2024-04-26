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

@Data
@AllArgsConstructor
public class ExecutionContext {
    // Should never be null
    private int sequence;
    private CountDownLatch countDownLatch;
    // This is to hold any exception thrown in a split-batch request
    private AtomicReference<Exception> exceptionHolder;
}
