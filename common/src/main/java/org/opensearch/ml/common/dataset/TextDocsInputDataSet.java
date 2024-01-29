/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.output.model.ModelResultFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.TEXT_DOCS)
public class TextDocsInputDataSet extends MLInputDataset{

    private ModelResultFilter resultFilter;

    private List<String> docs;

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_MULTI_MODAL = Version.V_2_11_0;

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
        Version version = streamInput.getVersion();
        if (version.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_MULTI_MODAL)) {
            docs = new ArrayList<>();
            int size = streamInput.readInt();
            for (int i=0; i<size; i++) {
                docs.add(streamInput.readOptionalString());
            }
        } else {
            docs = streamInput.readStringList();
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
        Version version = streamOutput.getVersion();
        if (version.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_MULTI_MODAL)) {
            streamOutput.writeInt(docs.size());
            for (String doc : docs) {
                streamOutput.writeOptionalString(doc);
            }
        } else {
            streamOutput.writeStringCollection(docs);
        }
        if (resultFilter != null) {
            streamOutput.writeBoolean(true);
            resultFilter.writeTo(streamOutput);
        } else {
            streamOutput.writeBoolean(false);
        }
    }
}
