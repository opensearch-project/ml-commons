/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;

/**
 * Fixtures shared by the {@code auth_config} tests of this module: typed configurations, the documents they emit and
 * are stored as, connectors holding them, and the JSON and stream helpers more than one test class needs.
 */
public final class AuthTestFixtures {

    private AuthTestFixtures() {}

    /** Looks like a secret; no message may echo it. */
    public static final String SECRET_LOOKING = "s3cr3t-value-never-echoed";
    public static final String ACTION_URL = "https://api.openai.com/v1/chat/completions";
    public static final String TOKEN_URL = "https://acme.okta.com/oauth2/default/v1/token";

    /** The document {@link #oauth2()} emits: the request members plus the {@code grant_type} default. */
    public static final String OAUTH2_JSON = json(
        "{'type':'oauth2','token_url':'"
            + TOKEN_URL
            + "','grant_type':'client_credentials','client_auth_method':'client_secret_basic','client_id':'app',"
            + "'client_secret':'${credential.s}'}"
    );
    /** The document {@link #fullOauth2()} emits, in schema order, with the full {@code token_response}. */
    public static final String FULL_OAUTH2_JSON = OAUTH2_JSON.substring(0, OAUTH2_JSON.length() - 1) + json(
        ",'scope':'inference.invoke','audience':'https://api.example','token_request_params':{'resource':'r','x':'y'},"
            + "'token_response':{'access_token_field':'access_token','expires_in_field':'expires_in',"
            + "'expires_at_field':'expiration','default_ttl_seconds':600}}"
    );
    /**
     * A stored configuration as a store that merges partial updates holds it: members in no particular order, the
     * removal markers of an update at two depths, and a number returned as a string.
     */
    public static final String STORED_WITH_MARKERS_JSON = json(
        "{'token_response':{'default_ttl_seconds':'600','expires_at_field':[]},'scope':[],'type':'oauth2','token_url':'"
            + TOKEN_URL
            + "','client_auth_method':'client_secret_basic','client_id':'app','client_secret':'${credential.s}',"
            + "'audience':[],'token_request_params':{'x':[],'resource':'r'}}"
    );

    /** The smallest oauth2 configuration the parse accepts, as a builder for variants. */
    public static OAuth2AuthConfig.OAuth2AuthConfigBuilder oauth2Builder() {
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(TOKEN_URL)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC)
            .clientId("app")
            .clientSecret("${credential.s}");
    }

    public static OAuth2AuthConfig oauth2() {
        return oauth2Builder().build();
    }

    /** {@link #oauth2()} with every optional member set. */
    public static OAuth2AuthConfig fullOauth2() {
        Map<String, String> tokenRequestParams = new LinkedHashMap<>();
        tokenRequestParams.put("resource", "r");
        tokenRequestParams.put("x", "y");
        return oauth2Builder()
            .scope("inference.invoke")
            .audience("https://api.example")
            .tokenRequestParams(tokenRequestParams)
            .tokenResponse(TokenResponseSpec.builder().expiresAtField("expiration").defaultTtlSeconds(600).build())
            .build();
    }

    /** What {@link #STORED_WITH_MARKERS_JSON} reads as. */
    public static OAuth2AuthConfig storedWithMarkersAsRead() {
        return oauth2Builder()
            .tokenRequestParams(Map.of("resource", "r"))
            .tokenResponse(TokenResponseSpec.builder().defaultTtlSeconds(600).build())
            .build();
    }

    /** Writes JSON with single quotes for readability. */
    public static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }

    /** A JSON parser positioned on the first token of {@code json}. */
    public static XContentParser parser(String json) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        return parser;
    }

    /** The JSON object {@code singleQuoted} denotes, as the ordered map a request parser hands over. */
    public static Map<String, Object> document(String singleQuoted) {
        try {
            return parser(json(singleQuoted)).mapOrdered();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String toJson(ToXContentObject object) throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        object.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return TestHelper.xContentBuilderToString(builder);
    }

    /** Writes {@code source} at {@code version} and reads it back at the same version, checking that nothing is left unread. */
    public static <T> T roundTrip(Writeable source, Version version, Writeable.Reader<T> reader) throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(version);
        source.writeTo(output);
        StreamInput input = output.bytes().streamInput();
        input.setVersion(version);
        T read = reader.read(input);
        assertEquals("the reader must consume everything the writer wrote", 0, input.available());
        return read;
    }

    /** The message {@link OAuth2AuthConfig#parse(Map)} rejects {@code block} with. */
    public static String parseMessage(Map<String, Object> block) {
        return assertThrows(IllegalArgumentException.class, () -> OAuth2AuthConfig.parse(block)).getMessage();
    }

    /** An http connector holding {@code authConfig}, with the credential key {@code s} and one predict action. */
    public static HttpConnector http(OAuth2AuthConfig authConfig) {
        return http(new LinkedHashMap<>(Map.of("model", "x")), authConfig);
    }

    /** {@link #http(OAuth2AuthConfig)} with the given parameters (null for a connector stored without them). */
    public static HttpConnector http(Map<String, String> parameters, OAuth2AuthConfig authConfig) {
        ConnectorAction predict = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url(ACTION_URL)
            .requestBody("{}")
            .build();
        return HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(parameters)
            .credential(Map.of("s", SECRET_LOOKING))
            .actions(List.of(predict))
            .authConfig(authConfig)
            .build();
    }
}
