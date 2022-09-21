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

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.TEXT)
public class TextInputDataSet extends MLInputDataset{

    public static final String TEXT_FIELD = "text";
    private MLModelResultFilter resultFilter;

    private List<String> docs;

    public TextInputDataSet(List<String> docs, MLModelResultFilter resultFilter) {
        super(MLInputDataType.TEXT);
        this.resultFilter = resultFilter;
        Objects.requireNonNull(docs);
        if (docs.size() == 0) {
            throw new IllegalArgumentException("empty docs");
        }
        this.docs = docs;
    }

    public TextInputDataSet(StreamInput streamInput) throws IOException {
        super(MLInputDataType.TEXT);
        docs = streamInput.readStringList();
        if (streamInput.readBoolean()) {
            resultFilter = new MLModelResultFilter(streamInput);
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
