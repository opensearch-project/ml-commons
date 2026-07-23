/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

import com.google.common.collect.ImmutableList;

public class TextDocsBatchableInputTests {

    private TextDocsBatchableInput handler;

    @Before
    public void setUp() {
        handler = new TextDocsBatchableInput();
    }

    private MLInput textInput(ModelResultFilter filter, String... docs) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(ImmutableList.copyOf(docs)).resultFilter(filter).build();
        return MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(dataSet).build();
    }

    @Test
    public void toItemsComputesUtf8ByteSize() {
        MLInput input = textInput(null, "a", "€"); // '€' is 3 UTF-8 bytes
        List<BatchItem> items = handler.toItems(input);
        assertEquals(2, items.size());
        assertEquals(1, items.get(0).getByteSize());
        assertEquals("€".getBytes(StandardCharsets.UTF_8).length, items.get(1).getByteSize());
        assertEquals("a", items.get(0).getPayload());
    }

    @Test
    public void mergePreservesResultFilterAndSelectsDocs() {
        ModelResultFilter filter = new ModelResultFilter(true, true, null, null);
        MLInput input = textInput(filter, "x", "y", "z");
        List<BatchItem> items = handler.toItems(input);
        MLInput sub = handler.merge(input, items.subList(1, 3));
        TextDocsInputDataSet subDataSet = (TextDocsInputDataSet) sub.getInputDataset();
        assertEquals(ImmutableList.of("y", "z"), subDataSet.getDocs());
        assertEquals(FunctionName.TEXT_EMBEDDING, sub.getAlgorithm());
        assertEquals(filter.isReturnBytes(), subDataSet.getResultFilter().isReturnBytes());
    }

    @Test
    public void combineFlattensSubBatchTensorsIntoSingleGroupInOrder() {
        // Each sub-batch call returns one ModelTensors group holding one tensor per doc (remote-embedding shape).
        MLOutput firstCall = tensorOutput("t0", "t1"); // 2-doc sub-batch
        MLOutput secondCall = tensorOutput("t2");      // 1-doc sub-batch
        MLOutput merged = handler.combine(ImmutableList.of(firstCall, secondCall));

        List<ModelTensors> groups = ((ModelTensorOutput) merged).getMlModelOutputs();
        assertEquals("reassembled output must be a single group, like an un-split call", 1, groups.size());
        List<ModelTensor> flat = groups.get(0).getMlModelTensors();
        assertEquals(3, flat.size());
        assertEquals("t0", flat.get(0).getName());
        assertEquals("t1", flat.get(1).getName());
        assertEquals("t2", flat.get(2).getName());
    }

    @Test(expected = IllegalStateException.class)
    public void combineRejectsNonTensorOutput() {
        handler.combine(ImmutableList.of(org.mockito.Mockito.mock(MLOutput.class)));
    }

    @Test
    public void combineIgnoresNullModelOutputs() {
        MLOutput withNull = ModelTensorOutput.builder().mlModelOutputs(null).build();
        MLOutput merged = handler.combine(ImmutableList.of(withNull, tensorOutput("t0")));
        assertEquals(1, ((ModelTensorOutput) merged).getMlModelOutputs().get(0).getMlModelTensors().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toItemsRejectsNonTextDocsInput() {
        MLInput input = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .inputDataset(new TextSimilarityInputDataSet("q", ImmutableList.of("d")))
            .build();
        handler.toItems(input);
    }

    /** One model call's output: a single ModelTensors group with one tensor per doc. */
    private MLOutput tensorOutput(String... names) {
        List<ModelTensor> tensors = new java.util.ArrayList<>();
        for (String name : names) {
            tensors.add(ModelTensor.builder().name(name).build());
        }
        return ModelTensorOutput.builder().mlModelOutputs(ImmutableList.of(ModelTensors.builder().mlModelTensors(tensors).build())).build();
    }
}
