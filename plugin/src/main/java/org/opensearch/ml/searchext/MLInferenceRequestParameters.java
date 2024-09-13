/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.ml.searchext;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
public class MLInferenceRequestParameters implements Writeable, ToXContentObject {
    static final String ML_INFERENCE_FIELD = "ml_inference";

    @Setter
    @Getter
    private Map<String, Object> params;

    public MLInferenceRequestParameters(Map<String, Object> params) {
        this.params = params;

    }

    public MLInferenceRequestParameters(StreamInput input) throws IOException {
        this.params = input.readMap();
    }

    /**
     * Write this into the {@linkplain StreamOutput}.
     *
     * @param out
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(this.params);
    }

    public static MLInferenceRequestParameters parse(XContentParser parser) throws IOException {
        return new MLInferenceRequestParameters(parser.map());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(ML_INFERENCE_FIELD);
        return builder.map(this.params);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        MLInferenceRequestParameters config = (MLInferenceRequestParameters) o;

        return params.equals(config.getParams());
    }

    @Override
    public int hashCode() {
        return Objects.hash(params);
    }
}
