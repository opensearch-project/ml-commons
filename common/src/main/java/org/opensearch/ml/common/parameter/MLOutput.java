/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.parameter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.MLCommonsClassLoader;

import java.io.IOException;

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
        MLOutput output = MLCommonsClassLoader.initInstance(outputType, in, StreamInput.class);
        return output;
    }

    abstract MLOutputType getType();

}
