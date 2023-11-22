/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.MLCommonsClassLoader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * ML output data. Must specify output type and
 */
@RequiredArgsConstructor
public abstract class MLOutput implements Output {
    @NonNull
    MLOutputType outputType;

    public MLOutput() {

    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(outputType);
    }

    public static MLOutput fromStream(StreamInput in) throws IOException {
        MLOutputType outputType = in.readEnum(MLOutputType.class);
        MLOutput output = MLCommonsClassLoader.initMLInstance(outputType, in, StreamInput.class);
        return output;
    }

    protected abstract MLOutputType getType();

}
