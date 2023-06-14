/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.utils.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.utils.StringUtils.isJson;

public abstract class AbstractConnector implements Connector {
    protected String httpMethod;
    @Getter
    protected Map<String, String> parameters;
    protected Map<String, String> credential;
    @Getter
    protected Map<String, String> decryptedHeaders;
    @Setter@Getter
    protected Map<String, String> decryptedCredential;

    protected Map<String, String> createPredictDecryptedHeaders(Map<String, String> headers) {
        Map<String, String> decryptedHeaders = new HashMap<>();
        StringSubstitutor substitutor = new StringSubstitutor(getDecryptedCredential(), "${credential.", "}");
        for (String key : headers.keySet()) {
            decryptedHeaders.put(key, substitutor.replace(headers.get(key)));
        }
        if (parameters != null && parameters.size() > 0) {
            substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            for (String key : decryptedHeaders.keySet()) {
                decryptedHeaders.put(key, substitutor.replace(decryptedHeaders.get(key)));
            }
        }
        return decryptedHeaders;
    }

    protected String parseURL(String url) {
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        return substitutor.replace(url);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void parseResponse(T response, List<ModelTensor> modelTensors, boolean modelTensorJson) throws IOException {
        if (modelTensorJson) {
            String modelTensorJsonContent = (String) response;
            XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, modelTensorJsonContent);
            parser.nextToken();
            if (XContentParser.Token.START_ARRAY == parser.currentToken()) {
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    ModelTensor modelTensor = ModelTensor.parser(parser);
                    modelTensors.add(modelTensor);
                }
            } else {
                ModelTensor modelTensor = ModelTensor.parser(parser);
                modelTensors.add(modelTensor);
            }
            return;
        }
        if (response instanceof String && isJson((String)response)) {
            Map<String, Object> data = StringUtils.fromJson((String) response, "response");
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(data).build());
        } else {
            Map<String, Object> map = new HashMap<>();
            map.put("response", response);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(map).build());
        }
    }

    abstract public String getAccessKey();

    abstract public String getSecretKey();

    abstract public String getServiceName();

    abstract public String getRegion();
}
