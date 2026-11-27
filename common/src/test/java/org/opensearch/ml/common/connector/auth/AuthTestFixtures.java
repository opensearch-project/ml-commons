/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
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
import org.opensearch.ml.common.connector.ConnectorClientConfig;
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

    /** A token URL the default token endpoint patterns admit, for the jwt-bearer configurations. */
    public static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String ISSUER = "svc@project.iam.gserviceaccount.com";
    /** The credential key {@link #jwt()} references for its private key. */
    public static final String PRIVATE_KEY_KEY = "k";
    /** The {@code x5t} of a certificate: 20 bytes, 27 base64url characters. */
    public static final String X5T = "59KYlIgB4_XlJmRsdXi2FvFVmkY";
    /** The {@code x5t#S256} of a certificate: 32 bytes, 43 base64url characters. */
    public static final String X5T_S256 = "Z7E26IvERGSlI9PxYRR6U4XP1YQ7riAsx0JIHMtn8so";
    /** The document {@link #jwt()} emits: the issuer and the key plus the defaults. */
    public static final String JWT_JSON = json(
        "{'issuer':'" + ISSUER + "','signing_alg':'RS256','lifetime_seconds':60,'private_key':'${credential." + PRIVATE_KEY_KEY + "}'}"
    );
    /** The document {@link #fullJwt()} emits, in schema order. */
    public static final String FULL_JWT_JSON = json(
        "{'issuer':'"
            + ISSUER
            + "','subject':'user@example.com','audience':'https://api.example','signing_alg':'RS256','lifetime_seconds':120,"
            + "'additional_claims':{'scope':'https://www.googleapis.com/auth/cloud-platform','tenant':'t1'},"
            + "'header':{'kid':'k1','x5t#S256':'"
            + X5T_S256
            + "'},'private_key':'${credential."
            + PRIVATE_KEY_KEY
            + "}'}"
    );
    /** The document {@link #jwtBearer()} emits. */
    public static final String JWT_BEARER_JSON = json(
        "{'type':'oauth2','token_url':'"
            + GOOGLE_TOKEN_URL
            + "','grant_type':'urn:ietf:params:oauth:grant-type:jwt-bearer','client_auth_method':'none','jwt':"
            + JWT_JSON
            + "}"
    );

    /** The token endpoint of IBM Cloud IAM, which the default token endpoint patterns admit. */
    public static final String IBM_TOKEN_URL = "https://iam.cloud.ibm.com/identity/token";
    /** The extension grant of IBM Cloud IAM, which exchanges an API key for a token. */
    public static final String APIKEY_GRANT = "urn:ibm:params:oauth:grant-type:apikey";
    /** The document {@link #extensionGrant()} emits. */
    public static final String EXTENSION_GRANT_JSON = json(
        "{'type':'oauth2','token_url':'"
            + IBM_TOKEN_URL
            + "','grant_type':'"
            + APIKEY_GRANT
            + "','client_auth_method':'none','token_request_params':{'apikey':'${credential.apikey}'}}"
    );

    /** The token endpoint patterns of a node with default settings. */
    public static final TokenEndpointAllowlist DEFAULT_TOKEN_ALLOWLIST = new TokenEndpointAllowlist(
        ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getDefault(Settings.EMPTY)
    );
    /** The setting's default: the IBM API key grant alone. */
    public static final ExtensionGrantAllowlist DEFAULT_EXTENSION_GRANTS = new ExtensionGrantAllowlist(
        ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS.getDefault(Settings.EMPTY)
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

    /** The smallest jwt block that builds, as a builder for variants. */
    public static JwtSpec.JwtSpecBuilder jwtBuilder() {
        return JwtSpec.builder().issuer(ISSUER).privateKey("${credential." + PRIVATE_KEY_KEY + "}");
    }

    public static JwtSpec jwt() {
        return jwtBuilder().build();
    }

    /** {@link #jwt()} with every optional member set. */
    public static JwtSpec fullJwt() {
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("tenant", "t1");
        claims.put("scope", "https://www.googleapis.com/auth/cloud-platform");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(JwtSpec.X5T_S256, X5T_S256);
        header.put(JwtSpec.KID, "k1");
        return jwtBuilder()
            .subject("user@example.com")
            .audience("https://api.example")
            .lifetimeSeconds(120)
            .additionalClaims(claims)
            .header(header)
            .build();
    }

    /** The smallest jwt-bearer configuration: the grant, {@code client_auth_method} none and {@link #jwt()}. */
    public static OAuth2AuthConfig.OAuth2AuthConfigBuilder jwtBearerBuilder() {
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(GOOGLE_TOKEN_URL)
            .grantType(OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE)
            .jwt(jwt());
    }

    public static OAuth2AuthConfig jwtBearer() {
        return jwtBearerBuilder().build();
    }

    /** {@link #jwtBearer()} with {@link #fullJwt()}, a {@code token_request_params} member and a {@code token_response}. */
    public static OAuth2AuthConfig fullJwtBearer() {
        return jwtBearerBuilder()
            .tokenRequestParams(Map.of("resource", "r"))
            .tokenResponse(TokenResponseSpec.builder().defaultTtlSeconds(600).build())
            .jwt(fullJwt())
            .build();
    }

    /**
     * The smallest private_key_jwt configuration: the client credentials grant, a client id and a jwt block with the
     * key alone, as a builder for variants.
     */
    public static OAuth2AuthConfig.OAuth2AuthConfigBuilder privateKeyJwtBuilder() {
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(TOKEN_URL)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_PRIVATE_KEY_JWT)
            .clientId("app")
            .jwt(JwtSpec.builder().privateKey("${credential." + PRIVATE_KEY_KEY + "}").build());
    }

    public static OAuth2AuthConfig privateKeyJwt() {
        return privateKeyJwtBuilder().build();
    }

    /**
     * An extension grant configuration as IBM Cloud IAM takes it: {@link #APIKEY_GRANT}, {@code client_auth_method}
     * none and the API key referenced in {@code token_request_params}, as a builder for variants.
     */
    public static OAuth2AuthConfig.OAuth2AuthConfigBuilder extensionGrantBuilder() {
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(IBM_TOKEN_URL)
            .grantType(APIKEY_GRANT)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE)
            .tokenRequestParams(Map.of("apikey", "${credential.apikey}"));
    }

    public static OAuth2AuthConfig extensionGrant() {
        return extensionGrantBuilder().build();
    }

    /** Generated once, since key generation is slow; loaded only by the tests that read a key. */
    private static final class Keys {
        static final KeyPair RSA = generate("RSA", 2048);
        static final KeyPair EC = generate("EC", 256);

        private static KeyPair generate(String algorithm, int keySize) {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
                generator.initialize(keySize);
                return generator.generateKeyPair();
            } catch (GeneralSecurityException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** The PKCS#8 encoding of an RSA private key. */
    public static byte[] rsaPkcs8() {
        return Keys.RSA.getPrivate().getEncoded();
    }

    /** The PKCS#8 encoding of a freshly generated RSA key of {@code bits} bits, for a key shorter than the parser admits. */
    public static byte[] rsaPkcs8(int bits) {
        return Keys.generate("RSA", bits).getPrivate().getEncoded();
    }

    /** The PKCS#8 encoding of an EC private key on P-256, the key ES256 signs with. */
    public static byte[] ecPkcs8() {
        return Keys.EC.getPrivate().getEncoded();
    }

    /** The PKCS#8 encoding of a freshly generated EC key on the curve of {@code bits} bits, for a curve other than P-256. */
    public static byte[] ecPkcs8(int bits) {
        return Keys.generate("EC", bits).getPrivate().getEncoded();
    }

    /** {@link #rsaPkcs8()} as the PEM document a key file holds. */
    public static String rsaPkcs8Pem() {
        return pem("PRIVATE KEY", rsaPkcs8(), true);
    }

    /**
     * {@code encoded} in base64 between {@code -----BEGIN <label>-----} and {@code -----END <label>-----}, with the
     * body in 64-character lines as key files have it or on one line.
     */
    public static String pem(String label, byte[] encoded, boolean lineBreaks) {
        String body = lineBreaks
            ? Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(encoded)
            : Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
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
        return http(parameters, authConfig, List.of(predict), null);
    }

    /** {@link #http(OAuth2AuthConfig)} with the given parameters, actions and client config, each possibly null. */
    public static HttpConnector http(
        Map<String, String> parameters,
        OAuth2AuthConfig authConfig,
        List<ConnectorAction> actions,
        ConnectorClientConfig clientConfig
    ) {
        return HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(parameters)
            .credential(Map.of("s", SECRET_LOOKING))
            .actions(actions)
            .connectorClientConfig(clientConfig)
            .authConfig(authConfig)
            .build();
    }
}
