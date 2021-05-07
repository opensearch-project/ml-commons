/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.common.dataset;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import static org.junit.Assert.assertEquals;

public class DataFrameInputDatasetTest {

    @Test
    public void writeTo_Success() throws IOException {
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder()
            .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                put("key1", 2.0D);
            }})))
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        dataFrameInputDataset.writeTo(bytesStreamOutput);
        assertEquals(21, bytesStreamOutput.size());
    }
}