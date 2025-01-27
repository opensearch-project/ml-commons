/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.dataset;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.IMAGE_EMBEDDING)
public class ImageEmbeddingInputDataSet extends MLInputDataset {

    List<String> base64Images;

    @Builder(toBuilder = true)
    public ImageEmbeddingInputDataSet(List<String> base64Images) {
        super(MLInputDataType.IMAGE_EMBEDDING);
        if (base64Images == null) {
            throw new IllegalArgumentException("Image in base64 is not provided");
        }
        this.base64Images = base64Images;
    }

    public ImageEmbeddingInputDataSet(StreamInput in) throws IOException {
        super(MLInputDataType.IMAGE_EMBEDDING);
        this.base64Images = in.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringCollection(base64Images);
    }
}
