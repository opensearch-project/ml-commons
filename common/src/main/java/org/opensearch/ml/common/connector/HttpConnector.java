/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.ConnectorNames.HTTP_V1;

@Log4j2
@NoArgsConstructor
@org.opensearch.ml.common.annotation.Connector(HTTP_V1)
public class HttpConnector implements Connector {
    public static final String HTTP_METHOD_FIELD = "http_method";
    public static final String ENDPOINT_FIELD = "endpoint";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String HEADERS_FIELD = "headers";
    public static final String BODY_TEMPLATE_FIELD = "body_template";
    public static final String RESPONSE_FILTER_FIELD = "response_filter";
    public static final String RESPONSE_TYPE_FIELD = "response_type";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String PRE_PROCESS_FUNCTION_FIELD = "pre_process_function";
    public static final String POST_PROCESS_FUNCTION_FIELD = "post_process_function";
    @Getter
    protected String name;
    @Getter
    protected String httpMethod;
    @Getter
    protected String endpoint;

    protected Map<String, String> headers ;
    protected Map<String, String> credential ;
    protected Map<String, String> decryptedCredential;
    protected Map<String, String> decryptedHeaders;
    protected Map<String, String> parameters;

    protected String bodyTemplate;
    protected String responseFilter;
    protected String responseType;
    @Getter
    protected String preProcessFunction;
    @Getter
    protected String postProcessFunction;

    protected static Gson gson;
    static {
        gson = new Gson();
    }
    //TODO: add RequestConfig like request time out,

    public HttpConnector(String name, XContentParser parser) throws IOException {
        this.name = name;
        headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case HTTP_METHOD_FIELD:
                    httpMethod = parser.text();
                    break;
                case ENDPOINT_FIELD:
                    endpoint = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    parameters = new HashMap<>();
                    parameters.putAll(parser.mapStrings());
                    break;
                case CREDENTIAL_FIELD:
                    credential = new HashMap<>();
                    credential.putAll(parser.mapStrings());
                    break;
                case HEADERS_FIELD:
                    headers.putAll(parser.mapStrings());
                    break;
                case BODY_TEMPLATE_FIELD:
                    bodyTemplate = parser.text();
                    break;
                case RESPONSE_FILTER_FIELD:
                    responseFilter = parser.text();
                    break;
                case RESPONSE_TYPE_FIELD:
                    responseType = parser.text();
                    break;
                case PRE_PROCESS_FUNCTION_FIELD:
                    preProcessFunction = parser.text();
                    break;
                case POST_PROCESS_FUNCTION_FIELD:
                    postProcessFunction = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("wrong input");
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(this.name);
        builder.startObject();
        if (httpMethod != null) {
            builder.field(HTTP_METHOD_FIELD, httpMethod);
        }
        if (endpoint != null) {
            builder.field(ENDPOINT_FIELD, endpoint);
        }
        if (parameters != null) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CREDENTIAL_FIELD, credential);
        }
        if (headers != null) {
            builder.field(HEADERS_FIELD, headers);
        }
        if (bodyTemplate != null) {
            builder.field(BODY_TEMPLATE_FIELD, bodyTemplate);
        }
        if (responseFilter != null) {
            builder.field(RESPONSE_FILTER_FIELD, responseFilter);
        }
        if (responseType != null) {
            builder.field(RESPONSE_TYPE_FIELD, responseType);
        }
        if (preProcessFunction != null) {
            builder.field(PRE_PROCESS_FUNCTION_FIELD, preProcessFunction);
        }
        if (postProcessFunction != null) {
            builder.field(POST_PROCESS_FUNCTION_FIELD, postProcessFunction);
        }
        builder.endObject();
        builder.endObject();
        return builder;
    }

    public HttpConnector(String name, StreamInput input) throws IOException {
        this.name = name;
        endpoint = input.readOptionalString();
        httpMethod = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            headers = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        bodyTemplate = input.readOptionalString();
        responseFilter = input.readOptionalString();
        responseType = input.readOptionalString();
        preProcessFunction = input.readOptionalString();
        postProcessFunction = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Connector.super.writeTo(out);
        out.writeOptionalString(httpMethod);
        if (parameters != null) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (credential != null) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (headers != null) {
            out.writeBoolean(true);
            out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(bodyTemplate);
        out.writeOptionalString(responseFilter);
        out.writeOptionalString(responseType);
        out.writeOptionalString(preProcessFunction);
        out.writeOptionalString(postProcessFunction);
    }

    public Map<String, String> createHeaders() {
        return decryptedHeaders;
    }

    @Override
    public  <T> T createPayload(Map<String, ?> parameters) {
        if (this.bodyTemplate != null) {
            String payload = bodyTemplate;
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, ?> entry : parameters.entrySet()) {
                values.put(entry.getKey(), (String)entry.getValue());
            }
            StringSubstitutor substitutor = new StringSubstitutor(values);
            payload = substitutor.replace(payload);
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                if (payload.contains("%(" + entry.getKey() + ")")) {
                    values.put(entry.getKey(), StringEscapeUtils.escapeJson(gson.fromJson((String)entry.getValue(), String.class)));
                }
            }
            StringSubstitutor innerSubstitutor = new StringSubstitutor(values, "%(", ")");
            payload = innerSubstitutor.replace(payload);
            return (T) payload;
        }
        return (T) parameters.get("http_body");
    }

    @Override
    public void decrypt(Function<String, String> function) {
        Map<String, String> decrypted = new HashMap<>();
        for (String key : credential.keySet()) {
            decrypted.put(key, function.apply(credential.get(key)));
        }
        this.decryptedCredential = decrypted;

        Map<String, String> decryptedHeaders = new HashMap<>();
        StringSubstitutor substitutor = new StringSubstitutor(decryptedCredential);
        for (String key : headers.keySet()) {
            decryptedHeaders.put(key, substitutor.replace(headers.get(key)));
        }
        this.decryptedHeaders = decryptedHeaders;
    }

    @Override
    public void encrypt(Function<String, String> function) {
        for (String key : credential.keySet()) {
            String encrypted = function.apply(credential.get(key));
            credential.put(key, encrypted);
        }
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
        if (responseFilter == null) {
            Map<String, Object> data = gson.fromJson((String) response, Map.class);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(data).build());
            return;
        }
        Object result = JsonPath.parse((String)response).read(responseFilter);

        if ("embedding".equals(responseType)) {
            long m = 0;
            long n = 0;
            boolean number = false;
            if (result instanceof List) {
                m++;
                Object item = ((List<?>) result).get(0);
                while(item instanceof List){
                    m++;
                    n = ((List<?>) item).size();
                    item = ((List<?>) item).get(0);
                }
                if(item instanceof Number){
                    number = true;
                }
            }
            if (number == true) {
                List<Object> data = flattenList((List<?>) result);
                modelTensors.add(ModelTensor.builder().name("sentence_embedding").dataType(MLResultDataType.FLOAT32).shape(new long[]{m-1, n })
                        .data(data.toArray(new Number[0])).build());
            } else {
                throw new IllegalArgumentException("wrong embedding result");
            }
        } else {
            Map<String, Object> map = new HashMap<>();
            map.put("filtered_response", result);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(map).build());
        }
    }

    @Override
    public Connector clone() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            String name = streamInput.readString();
            return new HttpConnector(name, streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Object> flattenList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof List<?>) {
                result.addAll(flattenList((List<?>) element));
            } else {
                result.add(element);
            }
        }
        return result;
    }

}
