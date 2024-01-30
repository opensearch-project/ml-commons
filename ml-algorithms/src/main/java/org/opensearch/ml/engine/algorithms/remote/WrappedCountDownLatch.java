/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.util.concurrent.CountDownLatch;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WrappedCountDownLatch {
    // Should never be null
    private int sequence;
    private CountDownLatch countDownLatch;
}
