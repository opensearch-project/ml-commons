/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.searchext;

import static org.opensearch.ml.searchext.MLInferenceRequestParameters.ML_INFERENCE_FIELD;

import java.io.IOException;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchExtBuilder;

public class MLInferenceRequestParametersExtBuilder extends SearchExtBuilder {
    private static final Logger logger = LogManager.getLogger(MLInferenceRequestParametersExtBuilder.class);
    public static final String NAME = ML_INFERENCE_FIELD;
    private MLInferenceRequestParameters requestParameters;

    public MLInferenceRequestParametersExtBuilder() {}

    public MLInferenceRequestParametersExtBuilder(StreamInput input) throws IOException {
        this.requestParameters = new MLInferenceRequestParameters(input);
    }

    public MLInferenceRequestParameters getRequestParameters() {
        return requestParameters;
    }

    public void setRequestParameters(MLInferenceRequestParameters requestParameters) {
        this.requestParameters = requestParameters;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass(), this.requestParameters);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof MLInferenceRequestParametersExtBuilder)) {
            return false;
        }
        MLInferenceRequestParametersExtBuilder o = (MLInferenceRequestParametersExtBuilder) obj;
        return this.requestParameters.equals(o.requestParameters);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {

        requestParameters.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.value(requestParameters);
    }

    public static MLInferenceRequestParametersExtBuilder parse(XContentParser parser) throws IOException {

        MLInferenceRequestParametersExtBuilder extBuilder = new MLInferenceRequestParametersExtBuilder();
        MLInferenceRequestParameters requestParameters = MLInferenceRequestParameters.parse(parser);
        extBuilder.setRequestParameters(requestParameters);
        return extBuilder;
    }
}
