/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.model;

import java.io.IOException;
import java.time.Instant;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;

public class MLTaskTests {
    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        Instant now = Instant.now();
        MLTask task1 = MLTask
            .builder()
            .taskId("dummy taskId")
            .taskType(MLTaskType.PREDICTION)
            .modelId(null)
            .createTime(now)
            .state(MLTaskState.RUNNING)
            .error(null)
            .build();
        task1.writeTo(output);
        MLTask task2 = new MLTask(output.bytes().streamInput());
        Assert.assertEquals(task1, task2);
    }
}
