/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;

public class SearchQueryInputDatasetTest {
    @Test
    public void writeTo_Success() throws IOException {
        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset
            .builder()
            .indices(Arrays.asList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().query(new MatchAllQueryBuilder()).size(1))
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        searchQueryInputDataset.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLInputDataType inputDataType = streamInput.readEnum(MLInputDataType.class);
        assertEquals(MLInputDataType.SEARCH_QUERY, inputDataType);
        searchQueryInputDataset = new SearchQueryInputDataset(streamInput);
        assertEquals(1, searchQueryInputDataset.getIndices().size());
        assertEquals(1, searchQueryInputDataset.getSearchSourceBuilder().size());
        assertEquals(new MatchAllQueryBuilder(), searchQueryInputDataset.getSearchSourceBuilder().query());
    }

    @Test
    public void init_EmptyIndices() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> SearchQueryInputDataset
                .builder()
                .indices(new ArrayList<>())
                .searchSourceBuilder(new SearchSourceBuilder().size(1))
                .build()
        );
        assertEquals("indices can't be empty", exception.getMessage());
    }
}
