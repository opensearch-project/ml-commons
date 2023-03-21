/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transformer;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.input.MLInput;

import java.io.IOException;

public class DataTransformer implements Writeable {

    public DataTransformer() {}
    /**
     * transform the mlInput to the required payload in String
     */
    public String transform(MLInput mlInput) {

        return "";
    }

    public DataTransformer(StreamInput in) throws IOException {

    }

    public void writeTo(StreamOutput out) throws IOException {

    }

    public static DataTransformer parse(XContentParser parser) throws IOException {
        return new DataTransformer();
    }
}
