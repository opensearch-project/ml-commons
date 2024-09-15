/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

/**
 * ML task states.
 * <ul>
 * <li><code>CREATED</code>:
 *     When user send a machine-learning(ML) request(training/inference), we will create one task to track
 *     ML task execution and set its state as CREATED
 *
 * <li><code>RUNNING</code>:
 *     Once MLTask is dispatched to work node and start to run corresponding ML algorithm, we will set the task state as RUNNING
 *
 * <li><code>COMPLETED</code>:
 *     When all training/inference completed, we will set task state as COMPLETED
 *
 * <li><code>FAILED</code>:
 *     If any exception happen, we will set task state as FAILED
 * </ul>
 */
public enum MLTaskState {
    CREATED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    COMPLETED_WITH_ERROR,
    CANCELLING,
    EXPIRED
}
