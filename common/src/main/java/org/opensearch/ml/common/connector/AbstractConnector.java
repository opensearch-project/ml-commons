/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
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

    protected String name;
    protected String description;
    protected String version;
    protected String protocol;

    protected Map<String, String> parameters;
    protected Map<String, String> credential;
    protected Map<String, String> decryptedHeaders;
    @Setter
    protected Map<String, String> decryptedCredential;

    protected List<ConnectorAction> actions;

    @Setter
    protected List<String> backendRoles;
    @Setter
    protected User owner;
    @Setter
    protected AccessMode access;
    protected Instant createdTime;
    protected Instant lastUpdateTime;

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
        if (response instanceof String && isJson((String) response)) {
            Map<String, Object> data = StringUtils.fromJson((String) response, ML_MAP_RESPONSE_KEY);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(data).build());
        } else {
            Map<String, Object> map = new HashMap<>();
            map.put("response", response);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(map).build());
        }
    }

    @Override
    public Optional<ConnectorAction> findPredictAction() {
        if (actions != null) {
            return actions.stream().filter(a -> a.getActionType() == ConnectorAction.ActionType.PREDICT).findFirst();
        }
        return Optional.empty();
    }

    @Override
    public void removeCredential() {
        this.credential = null;
        this.decryptedCredential = null;
        this.decryptedHeaders = null;
    }

    @Override
    public String getPredictEndpoint(Map<String, String> parameters) {
        Optional<ConnectorAction> predictAction = findPredictAction();
        if (!predictAction.isPresent()) {
            return null;
        }
        String predictEndpoint = predictAction.get().getUrl();
        if (parameters != null && parameters.size() > 0) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            predictEndpoint = substitutor.replace(predictEndpoint);
        }
        return predictEndpoint;
    }

}
