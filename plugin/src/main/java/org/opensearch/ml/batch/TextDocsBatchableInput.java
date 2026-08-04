/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensearch.ml.common.dataset.MLInputDataset;
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

    /**
     * Rebuilds a sub-batch as a text-docs input: only the documents change, and the rest of the request
     * is copied unchanged, such as the algorithm parameters and the result filter. Copying is safe only
     * while those fields mean the same thing for every document, applying to the request as a whole
     * rather than to a document at a particular position. A field whose meaning depends on where a
     * document sits in the list cannot be copied this way, and would have to be split and remapped here.
     */
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
        Integer commonStatusCode = null;
        boolean statusCodeSeen = false;
        for (MLOutput output : orderedOutputs) {
            List<ModelTensors> groups = asTensorOutput(output).getMlModelOutputs();
            if (groups == null) {
                continue;
            }
            for (ModelTensors group : groups) {
                if (group.getMlModelTensors() != null) {
                    tensors.addAll(group.getMlModelTensors());
                }
                Integer statusCode = group.getStatusCode();
                if (!statusCodeSeen) {
                    commonStatusCode = statusCode;
                    statusCodeSeen = true;
                } else if (!Objects.equals(commonStatusCode, statusCode)) {
                    throw new IllegalStateException(
                        "Expected every sub-batch output to report the same "
                            + ModelTensors.STATUS_CODE_FIELD
                            + ", but got both "
                            + commonStatusCode
                            + " and "
                            + statusCode
                            + ", so the sub-batch results cannot be merged into one response"
                    );
                }
            }
        }
        ModelTensors combined = ModelTensors.builder().mlModelTensors(tensors).build();
        combined.setStatusCode(commonStatusCode);
        return ModelTensorOutput.builder().mlModelOutputs(List.of(combined)).build();
    }

    private TextDocsInputDataSet asTextDocs(MLInput input) {
        MLInputDataset dataset = input == null ? null : input.getInputDataset();
        if (!(dataset instanceof TextDocsInputDataSet)) {
            throw new IllegalArgumentException(
                "Expected TextDocsInputDataSet but got " + (dataset == null ? "null" : dataset.getClass().getSimpleName())
            );
        }
        return (TextDocsInputDataSet) dataset;
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
