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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;

import static org.junit.Assert.assertEquals;

public class MLInputDatasetReaderTest {

    MLInputDatasetReader mlInputDatasetReader = new MLInputDatasetReader();

    @Test
    public void read_Success_DataFrameInputDataset() throws IOException {
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder()
            .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                put("key1", 2.0D);
            }})))
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        dataFrameInputDataset.writeTo(bytesStreamOutput);
        MLInputDataset inputDataset = mlInputDatasetReader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(MLInputDataType.DATA_FRAME, inputDataset.getInputDataType());
        assertEquals(1, ((DataFrameInputDataset) inputDataset).getDataFrame().size());
    }

    @Test
    public void read_Success_SearchQueryInputDataset() throws IOException {
        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset.builder()
            .indices(Arrays.asList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().size(1))
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        searchQueryInputDataset.writeTo(bytesStreamOutput);
        MLInputDataset inputDataset = mlInputDatasetReader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(MLInputDataType.SEARCH_QUERY, inputDataset.getInputDataType());
        searchQueryInputDataset = (SearchQueryInputDataset) inputDataset;
        assertEquals(Arrays.asList("index1"), searchQueryInputDataset.getIndices());
    }
}