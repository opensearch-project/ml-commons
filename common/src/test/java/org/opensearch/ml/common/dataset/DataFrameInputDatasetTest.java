/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

public class DataFrameInputDatasetTest {

    @Test
    public void writeTo_Success() throws IOException {
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset
            .builder()
            .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
                {
                    put("key1", 2.0D);
                }
            })))
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        dataFrameInputDataset.writeTo(bytesStreamOutput);
        assertEquals(21, bytesStreamOutput.size());
    }
}
