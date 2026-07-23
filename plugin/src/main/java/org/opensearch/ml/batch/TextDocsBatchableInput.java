/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

/**
 * Batching strategy for text documents. Each document becomes one item sized by its UTF-8 byte
 * length, each sub-batch is rebuilt as a text-docs input that keeps the original result filter, and
 * the sub-batch outputs are flattened back into a single result group in the original input order.
 */
public class TextDocsBatchableInput implements BatchableInput {

    @Override
    public List<BatchItem> toItems(MLInput input) {
        List<String> docs = asTextDocs(input).getDocs();
        List<BatchItem> items = new ArrayList<>(docs.size());
        for (String doc : docs) {
            long byteSize = doc == null ? 0L : doc.getBytes(StandardCharsets.UTF_8).length;
            items.add(new BatchItem(doc, byteSize));
        }
        return items;
    }

    @Override
    public MLInput merge(MLInput source, List<BatchItem> items) {
        List<String> docs = new ArrayList<>(items.size());
        for (BatchItem item : items) {
            docs.add((String) item.getPayload());
        }
        TextDocsInputDataSet subDataSet = asTextDocs(source).toBuilder().docs(docs).build();
        return source.toBuilder().inputDataset(subDataSet).build();
    }

    @Override
    public MLOutput combine(List<MLOutput> orderedOutputs) {
        // A remote embedding call returns all of its per-item results in a single group. To make the
        // reassembled response identical to an un-split call, flatten every sub-batch's results back
        // into one group, preserving the original input order.
        List<ModelTensor> tensors = new ArrayList<>();
        for (MLOutput output : orderedOutputs) {
            List<ModelTensors> groups = asTensorOutput(output).getMlModelOutputs();
            if (groups == null) {
                continue;
            }
            for (ModelTensors group : groups) {
                if (group.getMlModelTensors() != null) {
                    tensors.addAll(group.getMlModelTensors());
                }
            }
        }
        ModelTensors combined = ModelTensors.builder().mlModelTensors(tensors).build();
        return ModelTensorOutput.builder().mlModelOutputs(List.of(combined)).build();
    }

    private TextDocsInputDataSet asTextDocs(MLInput input) {
        if (input == null || !(input.getInputDataset() instanceof TextDocsInputDataSet)) {
            throw new IllegalArgumentException("TextDocsBatchableInput requires a TextDocsInputDataSet");
        }
        return (TextDocsInputDataSet) input.getInputDataset();
    }

    private ModelTensorOutput asTensorOutput(MLOutput output) {
        if (!(output instanceof ModelTensorOutput)) {
            throw new IllegalStateException(
                "Expected ModelTensorOutput but got " + (output == null ? "null" : output.getClass().getSimpleName())
            );
        }
        return (ModelTensorOutput) output;
    }
}
