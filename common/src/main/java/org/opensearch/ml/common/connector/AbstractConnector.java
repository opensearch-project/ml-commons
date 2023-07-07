/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.utils.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

public abstract class AbstractConnector implements Connector {
    public static final String ACCESS_KEY_FIELD = "access_key";
    public static final String SECRET_KEY_FIELD = "secret_key";
    public static final String SESSION_TOKEN_FIELD = "session_token";
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PROTOCOL_FIELD = "protocol";
    public static final String ACTIONS_FIELD = "actions";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_FIELD = "access";

    @Getter
    protected String name;
    protected String description;
    protected String version;
    @Getter
    protected String protocol;

    @Getter
    protected Map<String, String> parameters;
    protected Map<String, String> credential;
    @Getter
    protected Map<String, String> decryptedHeaders;
    @Setter@Getter
    protected Map<String, String> decryptedCredential;

    @Getter
    protected List<ConnectorAction> actions;

    @Setter
    @Getter
    private List<String> backendRoles;
    @Setter
    @Getter
    private User owner;
    @Setter
    @Getter
    private AccessMode access;

    protected Map<String, String> createPredictDecryptedHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
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

    @Override
    public void removeCredential() {
        this.credential = null;
        this.decryptedCredential = null;
    }

    public String getPredictEndpoint(Map<String, String> parameters) {
        String predictEndpoint = getPredictEndpoint();
        if (parameters != null && parameters.size() > 0) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            predictEndpoint = substitutor.replace(predictEndpoint);
        }
        return predictEndpoint;
    }

    public String getAccessKey() {
        return decryptedCredential.get(ACCESS_KEY_FIELD);
    }

    public String getSecretKey() {
        return decryptedCredential.get(SECRET_KEY_FIELD);
    }

    public String getSessionToken() {
        return decryptedCredential.get(SESSION_TOKEN_FIELD);
    }

    public String getServiceName() {
        if (parameters == null) {
            return decryptedCredential.get(SERVICE_NAME_FIELD);
        }
        return Optional.ofNullable(parameters.get(SERVICE_NAME_FIELD)).orElse(decryptedCredential.get(SERVICE_NAME_FIELD));
    }

    public String getRegion() {
        if (parameters == null) {
            return decryptedCredential.get(REGION_FIELD);
        }
        return Optional.ofNullable(parameters.get(REGION_FIELD)).orElse(decryptedCredential.get(REGION_FIELD));
    }
}
