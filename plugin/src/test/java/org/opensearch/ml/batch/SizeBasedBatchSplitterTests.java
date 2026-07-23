/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.model.BatchInferenceConfig;

public class SizeBasedBatchSplitterTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private SizeBasedBatchSplitter splitter;

    @Before
    public void setUp() {
        splitter = new SizeBasedBatchSplitter();
    }

    private List<BatchItem> items(long... byteSizes) {
        List<BatchItem> items = new ArrayList<>();
        for (int i = 0; i < byteSizes.length; i++) {
            items.add(new BatchItem("doc" + i, byteSizes[i]));
        }
        return items;
    }

    private List<Integer> sizes(List<List<BatchItem>> batches) {
        List<Integer> sizes = new ArrayList<>();
        for (List<BatchItem> batch : batches) {
            sizes.add(batch.size());
        }
        return sizes;
    }

    @Test
    public void splitsByCountCeiling() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).build();
        List<List<BatchItem>> batches = splitter.split(items(1, 1, 1, 1, 1), config);
        assertEquals(List.of(2, 2, 1), sizes(batches));
    }

    @Test
    public void singleBatchWhenUnderLimits() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(100).maxBytesPerRequest(1000L).build();
        List<List<BatchItem>> batches = splitter.split(items(10, 10, 10), config);
        assertEquals(1, batches.size());
        assertEquals(3, batches.get(0).size());
    }

    @Test
    public void splitsByByteCeiling() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1000).maxBytesPerRequest(100L).build();
        // 60 + 60 > 100 -> split; each batch holds one 60-byte item, third 30-byte item joins none (60+30>100)
        List<List<BatchItem>> batches = splitter.split(items(60, 60, 30), config);
        assertEquals(List.of(1, 2), sizes(batches));
        // batch 2 = [60, 30] = 90 <= 100
        assertEquals(60, batches.get(1).get(0).getByteSize());
        assertEquals(30, batches.get(1).get(1).getByteSize());
    }

    @Test
    public void oversizeItemGetsOwnBatchAndIsNeverDropped() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1000).maxBytesPerRequest(100L).build();
        List<List<BatchItem>> batches = splitter.split(items(10, 500, 10), config);
        // [10] | [500] (oversize, alone) | [10]
        assertEquals(List.of(1, 1, 1), sizes(batches));
        assertEquals(500, batches.get(1).get(0).getByteSize());
        // no input dropped
        assertEquals(3, batches.stream().mapToInt(List::size).sum());
    }

    @Test
    public void leadingOversizeItemIsolated() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1000).maxBytesPerRequest(100L).build();
        List<List<BatchItem>> batches = splitter.split(items(500, 10, 10), config);
        assertEquals(List.of(1, 2), sizes(batches));
        assertEquals(500, batches.get(0).get(0).getByteSize());
    }

    @Test
    public void byteLimitDisabledOnlyUsesCount() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).build(); // bytes disabled (-1)
        List<List<BatchItem>> batches = splitter.split(items(1_000_000, 1_000_000, 1_000_000), config);
        assertEquals(List.of(2, 1), sizes(batches));
    }

    @Test
    public void preservesOrderAcrossBatches() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).build();
        List<List<BatchItem>> batches = splitter.split(items(1, 1, 1, 1, 1), config);
        int seen = 0;
        for (List<BatchItem> batch : batches) {
            for (BatchItem item : batch) {
                assertEquals("doc" + seen++, item.getPayload());
            }
        }
        assertEquals(5, seen);
    }

    @Test
    public void singleItemProducesSingleBatch() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).maxBytesPerRequest(100L).build();
        assertEquals(List.of(1), sizes(splitter.split(items(10), config)));
    }

    @Test
    public void maxItemsOneGivesEachItemItsOwnBatch() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        assertEquals(List.of(1, 1, 1), sizes(splitter.split(items(1, 1, 1), config)));
    }

    @Test
    public void itemEqualToByteLimitIsNotOversize() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1000).maxBytesPerRequest(100L).build();
        // each item is exactly at the limit; 100 + 100 > 100 so they split one per batch
        assertEquals(List.of(1, 1), sizes(splitter.split(items(100, 100), config)));
    }

    @Test
    public void cumulativeBytesExactlyAtLimitStayTogether() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1000).maxBytesPerRequest(100L).build();
        // 40 + 60 == 100 (not > 100) so they stay in one batch
        assertEquals(List.of(2), sizes(splitter.split(items(40, 60), config)));
    }

    @Test
    public void trailingOversizeItemIsolated() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1000).maxBytesPerRequest(100L).build();
        List<List<BatchItem>> batches = splitter.split(items(10, 10, 500), config);
        assertEquals(List.of(2, 1), sizes(batches));
        assertEquals(500, batches.get(1).get(0).getByteSize());
    }

    @Test
    public void zeroByteItemsGroupByCountOnly() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).maxBytesPerRequest(100L).build();
        assertEquals(List.of(2, 1), sizes(splitter.split(items(0, 0, 0), config)));
    }

    @Test
    public void rejectsEmptyItems() {
        exceptionRule.expect(IllegalArgumentException.class);
        splitter.split(new ArrayList<>(), BatchInferenceConfig.builder().build());
    }

    @Test
    public void rejectsNullConfig() {
        exceptionRule.expect(IllegalArgumentException.class);
        splitter.split(items(1), null);
    }
}
