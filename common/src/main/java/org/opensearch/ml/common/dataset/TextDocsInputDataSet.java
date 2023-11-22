/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.output.model.ModelResultFilter;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.TEXT_DOCS)
public class TextDocsInputDataSet extends MLInputDataset {

    private ModelResultFilter resultFilter;

    private List<String> docs;

    @Builder(toBuilder = true)
    public TextDocsInputDataSet(List<String> docs, ModelResultFilter resultFilter) {
        super(MLInputDataType.TEXT_DOCS);
        this.resultFilter = resultFilter;
        Objects.requireNonNull(docs);
        if (docs.size() == 0) {
            throw new IllegalArgumentException("empty docs");
        }
        this.docs = docs;
    }

    public TextDocsInputDataSet(StreamInput streamInput) throws IOException {
        super(MLInputDataType.TEXT_DOCS);
        docs = new ArrayList<>();
        int size = streamInput.readInt();
        for (int i = 0; i < size; i++) {
            docs.add(streamInput.readOptionalString());
        }
        if (streamInput.readBoolean()) {
            resultFilter = new ModelResultFilter(streamInput);
        } else {
            resultFilter = null;
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        super.writeTo(streamOutput);
        streamOutput.writeInt(docs.size());
        for (String doc : docs) {
            streamOutput.writeOptionalString(doc);
        }
        if (resultFilter != null) {
            streamOutput.writeBoolean(true);
            resultFilter.writeTo(streamOutput);
        } else {
            streamOutput.writeBoolean(false);
        }
    }
}
