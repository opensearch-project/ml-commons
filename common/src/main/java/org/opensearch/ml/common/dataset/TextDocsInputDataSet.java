/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.output.model.ModelResultFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.TEXT_DOCS)
public class TextDocsInputDataSet extends MLInputDataset{

    public static final String DOCS_FIELD = "docs";
    private ModelResultFilter resultFilter;

    private List<String> docs;

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
        docs = streamInput.readStringList();
        if (streamInput.readBoolean()) {
            resultFilter = new ModelResultFilter(streamInput);
        } else {
            resultFilter = null;
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        super.writeTo(streamOutput);
        streamOutput.writeStringCollection(docs);
        if (resultFilter != null) {
            streamOutput.writeBoolean(true);
            resultFilter.writeTo(streamOutput);
        } else {
            streamOutput.writeBoolean(false);
        }
    }
}
