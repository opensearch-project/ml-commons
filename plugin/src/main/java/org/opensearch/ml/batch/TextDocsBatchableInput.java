/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesReference;
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

    private static final List<BatchItem> KEY_PAYLOAD = List.of(new BatchItem("", 0L));

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
    public String groupKey(MLInput input) {
        // Key over merge with a placeholder payload, so it always covers exactly the non-payload state merge copies.
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            merge(input, KEY_PAYLOAD).writeTo(out);
            return Base64.getEncoder().encodeToString(BytesReference.toBytes(out.bytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute batch group key for text-docs input", e);
        }
    }

    @Override
    public MLOutput combine(List<MLOutput> orderedOutputs) {
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

    @Override
    public List<MLOutput> distribute(MLOutput batchedOutput) {
        List<MLOutput> perItem = new ArrayList<>();
        List<ModelTensors> groups = asTensorOutput(batchedOutput).getMlModelOutputs();
        if (groups == null) {
            return perItem;
        }
        for (ModelTensors group : groups) {
            Integer statusCode = group.getStatusCode();
            if (group.getMlModelTensors() == null) {
                continue;
            }
            for (ModelTensor tensor : group.getMlModelTensors()) {
                ModelTensors single = ModelTensors.builder().mlModelTensors(new ArrayList<>(List.of(tensor))).build();
                single.setStatusCode(statusCode);
                perItem.add(ModelTensorOutput.builder().mlModelOutputs(new ArrayList<>(List.of(single))).build());
            }
        }
        return perItem;
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
