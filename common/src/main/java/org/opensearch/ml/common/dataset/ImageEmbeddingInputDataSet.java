/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.dataset;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;

import java.io.IOException;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.IMAGE_EMBEDDING)
public class ImageEmbeddingInputDataSet extends MLInputDataset {

    String base64Image;

    @Builder(toBuilder = true)
    public ImageEmbeddingInputDataSet(String base64Image) {
        super(MLInputDataType.IMAGE_EMBEDDING);
        if(base64Image == null) {
            throw new IllegalArgumentException("Image is not provided");
        }
        this.base64Image = base64Image;
    }

    public ImageEmbeddingInputDataSet(StreamInput in) throws IOException {
        super(MLInputDataType.IMAGE_EMBEDDING);
        this.base64Image = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(base64Image);
    }
}
