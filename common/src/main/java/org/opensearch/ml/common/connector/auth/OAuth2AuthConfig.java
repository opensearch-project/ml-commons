/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.opensearch.common.network.InetAddresses;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import tools.jackson.databind.JsonNode;

/**
 * The typed, immutable form of a connector {@code auth_config} block. A request is read with {@link #parse}, which
 * rejects everything the schema does not allow; a stored document is read with {@link #fromStored}, which first drops
 * the removal markers a partial update leaves on stores that merge. The block is emitted as the {@code auth_config}
 * object and carried on the transport behind the connector's version gate.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class OAuth2AuthConfig implements ToXContentObject, Writeable {
    public static final String TYPE_FIELD = "type";
    public static final String TOKEN_URL_FIELD = "token_url";
    public static final String GRANT_TYPE_FIELD = "grant_type";
    public static final String CLIENT_AUTH_METHOD_FIELD = "client_auth_method";
    public static final String CLIENT_ID_FIELD = "client_id";
    public static final String CLIENT_SECRET_FIELD = "client_secret";
    public static final String SCOPE_FIELD = "scope";
    public static final String AUDIENCE_FIELD = "audience";
    public static final String TOKEN_REQUEST_PARAMS_FIELD = "token_request_params";
    public static final String TOKEN_RESPONSE_FIELD = "token_response";
    public static final String JWT_FIELD = "jwt";
    public static final Set<String> FIELDS = Set
        .of(
            TYPE_FIELD,
            TOKEN_URL_FIELD,
            GRANT_TYPE_FIELD,
            CLIENT_AUTH_METHOD_FIELD,
            CLIENT_ID_FIELD,
            CLIENT_SECRET_FIELD,
            SCOPE_FIELD,
            AUDIENCE_FIELD,
            TOKEN_REQUEST_PARAMS_FIELD,
            TOKEN_RESPONSE_FIELD,
            JWT_FIELD
        );

    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    /** Interactive, user-credential and refresh grants are never accepted (RFC 9700 section 2.4 for password). */
    public static final Set<String> DENIED_GRANT_TYPES = Set
        .of("password", "authorization_code", "refresh_token", "urn:ietf:params:oauth:grant-type:device_code");

    public static final String CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC = "client_secret_basic";
    public static final String CLIENT_AUTH_METHOD_CLIENT_SECRET_POST = "client_secret_post";
    public static final String CLIENT_AUTH_METHOD_NONE = "none";
    public static final String CLIENT_AUTH_METHOD_PRIVATE_KEY_JWT = "private_key_jwt";
    public static final String NOT_AN_OBJECT_MESSAGE = "auth_config must be a JSON object";
    public static final String TYPE_REQUIRED_MESSAGE = "auth_config.type is required";
    public static final String JWT_NOT_SUPPORTED_MESSAGE = "auth_config.jwt is not supported in this version";
    public static final String NONE_WITH_FIELDS_MESSAGE = "auth_config of type none must not contain other fields";
    public static final String PRIVATE_KEY_JWT_NOT_SUPPORTED_MESSAGE = "auth_config.client_auth_method ["
        + CLIENT_AUTH_METHOD_PRIVATE_KEY_JWT
        + "] is not supported in this version";
    public static final String CLIENT_SECRET_MUST_BE_REFERENCE_MESSAGE =
        "auth_config.client_secret must be a ${credential.<key>} reference; put the secret itself in the connector credential";
    public static final String TOKEN_URL_MUST_USE_HTTPS_MESSAGE = "auth_config.token_url must use https";
    public static final String TOKEN_URL_MUST_NAME_A_HOST_MESSAGE = "auth_config.token_url must name a host";
    public static final String TOKEN_URL_SCOPED_IPV6_MESSAGE = "auth_config.token_url must not use an IPv6 address with a zone id";
    public static final Set<String> CLIENT_AUTH_METHODS = Set
        .of(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC, CLIENT_AUTH_METHOD_CLIENT_SECRET_POST, CLIENT_AUTH_METHOD_NONE);

    /** Form parameters the token client sets itself; {@code token_request_params} may not set them. */
    public static final Set<String> RESERVED_TOKEN_REQUEST_PARAMS = Set
        .of(GRANT_TYPE_FIELD, CLIENT_ID_FIELD, CLIENT_SECRET_FIELD, "assertion", SCOPE_FIELD, AUDIENCE_FIELD);
    public static final String RESERVED_TOKEN_REQUEST_PARAM_PREFIX = "client_assertion";

    private static final String HTTPS = "https";
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String PARAMETERS_PLACEHOLDER = "${parameters.";
    private static final String PARAMETERS_NOT_ALLOWED =
        " must not use ${parameters.*}: auth_config is read from the stored connector only";

    /** The configuration that attaches nothing: {@code {"type": "none"}}. */
    public static final OAuth2AuthConfig NONE = builder().type(ConnectorAuthType.NONE).build();

    private final ConnectorAuthType type;
    /** A literal URL or a {@code ${credential.<key>}} reference. */
    private final String tokenUrl;
    private final String grantType;
    private final String clientAuthMethod;
    private final String clientId;
    /** Always a {@code ${credential.<key>}} reference when present, never the secret itself. */
    private final String clientSecret;
    private final String scope;
    private final String audience;
    /**
     * Never null, in key order, so that equal configurations emit the same document; literal values may be sensitive,
     * so they stay out of {@link #toString()}.
     */
    @ToString.Exclude
    private final Map<String, String> tokenRequestParams;
    private final TokenResponseSpec tokenResponse;
    /**
     * In a partial update, the stored configuration this one replaces. Its members this one lacks are emitted as
     * empty arrays, so that a store which merges the update drops them. Never streamed, never read back.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final OAuth2AuthConfig replaced;

    @Builder(toBuilder = true)
    private OAuth2AuthConfig(
        ConnectorAuthType type,
        String tokenUrl,
        String grantType,
        String clientAuthMethod,
        String clientId,
        String clientSecret,
        String scope,
        String audience,
        Map<String, String> tokenRequestParams,
        TokenResponseSpec tokenResponse,
        OAuth2AuthConfig replaced
    ) {
        this.type = type;
        this.tokenUrl = tokenUrl;
        this.grantType = grantType == null && type == ConnectorAuthType.OAUTH2 ? GRANT_TYPE_CLIENT_CREDENTIALS : grantType;
        this.clientAuthMethod = clientAuthMethod;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.audience = audience;
        this.tokenRequestParams = tokenRequestParams == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(tokenRequestParams));
        this.tokenResponse = tokenResponse == null ? TokenResponseSpec.defaults() : tokenResponse;
        this.replaced = replaced;
        // The rules parse applies before it reads the members, for a configuration built or streamed instead: stored
        // as it is, a block that fails them would not read back.
        if (this.type == ConnectorAuthType.NONE && document().size() > 1) {
            throw new IllegalArgumentException(NONE_WITH_FIELDS_MESSAGE);
        }
        validateMembers();
    }

    public OAuth2AuthConfig(StreamInput in) throws IOException {
        this(
            ConnectorAuthType.from(in.readString()),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readMap(StreamInput::readString, StreamInput::readString),
            new TokenResponseSpec(in),
            null
        );
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type.getValue());
        out.writeOptionalString(tokenUrl);
        out.writeOptionalString(grantType);
        out.writeOptionalString(clientAuthMethod);
        out.writeOptionalString(clientId);
        out.writeOptionalString(clientSecret);
        out.writeOptionalString(scope);
        out.writeOptionalString(audience);
        out.writeMap(tokenRequestParams, StreamOutput::writeString, StreamOutput::writeString);
        tokenResponse.writeTo(out);
    }

    /**
     * Strict parse of the {@code auth_config} value of a request, which must be a JSON object.
     *
     * @throws IllegalArgumentException when the value is not a valid block
     */
    public static OAuth2AuthConfig parse(XContentParser parser) throws IOException {
        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            throw new IllegalArgumentException(NOT_AN_OBJECT_MESSAGE);
        }
        return parse(parser.mapOrdered());
    }

    /**
     * Strict typed parse of an {@code auth_config} object: the shape is checked here, the values when the configuration
     * is built. Messages name fields, never values.
     *
     * @throws IllegalArgumentException when {@code block} is not a valid block
     */
    public static OAuth2AuthConfig parse(Map<String, Object> block) {
        JsonNode root = StringUtils.MAPPER.valueToTree(without(block, Objects::isNull));

        JsonNode typeNode = root.get(TYPE_FIELD);
        if (isAbsent(typeNode)) {
            throw new IllegalArgumentException(TYPE_REQUIRED_MESSAGE);
        }
        if (!typeNode.isString()) {
            throw new IllegalArgumentException("auth_config.type must be a string");
        }
        ConnectorAuthType type = ConnectorAuthType.from(typeNode.stringValue());

        for (String field : root.propertyNames()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException(unknownFieldMessage(field));
            }
        }
        if (type == ConnectorAuthType.NONE && root.size() > 1) {
            throw new IllegalArgumentException(NONE_WITH_FIELDS_MESSAGE);
        }
        if (!isAbsent(root.get(JWT_FIELD))) {
            throw new IllegalArgumentException(JWT_NOT_SUPPORTED_MESSAGE);
        }

        String tokenUrl = optionalString(root, TOKEN_URL_FIELD);
        String grantType = optionalString(root, GRANT_TYPE_FIELD);
        String clientAuthMethod = optionalString(root, CLIENT_AUTH_METHOD_FIELD);
        String clientId = optionalString(root, CLIENT_ID_FIELD);
        String clientSecret = optionalString(root, CLIENT_SECRET_FIELD);
        String scope = optionalString(root, SCOPE_FIELD);
        String audience = optionalString(root, AUDIENCE_FIELD);
        Map<String, String> tokenRequestParams = parseTokenRequestParams(root.get(TOKEN_REQUEST_PARAMS_FIELD));
        TokenResponseSpec tokenResponse = parseTokenResponse(root.get(TOKEN_RESPONSE_FIELD));

        return OAuth2AuthConfig
            .builder()
            .type(type)
            .tokenUrl(tokenUrl)
            .grantType(grantType)
            .clientAuthMethod(clientAuthMethod)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .scope(scope)
            .audience(audience)
            .tokenRequestParams(tokenRequestParams)
            .tokenResponse(tokenResponse)
            .build();
    }

    /**
     * Reads the {@code auth_config} object of a stored connector document. A partial update marks every member it
     * removed with an empty array, which a store that merges keeps; those members are dropped at every depth before
     * the block is parsed as a request is.
     *
     * @throws IllegalArgumentException when the block is not valid once the markers are removed
     */
    public static OAuth2AuthConfig fromStored(Map<String, Object> stored) {
        return parse(without(stored, value -> value instanceof List && ((List<?>) value).isEmpty()));
    }

    /** A copy of {@code block} without the members, at every depth of nested objects, whose value {@code absent} accepts. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> without(Map<String, Object> block, Predicate<Object> absent) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> member : block.entrySet()) {
            Object value = member.getValue();
            if (absent.test(value)) {
                continue;
            }
            copy.put(member.getKey(), value instanceof Map ? without((Map<String, Object>) value, absent) : value);
        }
        return copy;
    }

    /**
     * The string a literal {@code token_url} is matched against the token-endpoint allowlist with: scheme and host
     * lower-cased (an IPv6 literal in its compressed form), port 443 removed and any other port kept, then the raw
     * path as written, or {@code /} for an empty path. Unlike {@code Connector.canonicalUrl}, which keeps {@code :443}
     * and the query, a query string is rejected.
     *
     * @throws IllegalArgumentException when the URL does not parse, is not {@code https}, carries user information,
     *     a fragment or a query, names no host (including registry authorities such as {@code my_host}) or names an
     *     IPv6 address with a zone id. The message never repeats the URL.
     */
    public static String canonicalTokenUrl(String tokenUrl) {
        if (tokenUrl == null || tokenUrl.isEmpty()) {
            throw new IllegalArgumentException("auth_config.token_url is required");
        }
        URI uri;
        try {
            uri = new URI(tokenUrl);
        } catch (URISyntaxException e) {
            // The cause is dropped on purpose: its message quotes the URL, which may carry user information.
            throw new IllegalArgumentException("auth_config.token_url is not a valid URI");
        }
        if (!HTTPS.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(TOKEN_URL_MUST_USE_HTTPS_MESSAGE);
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("auth_config.token_url must not contain user information");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("auth_config.token_url must not contain a fragment");
        }
        if (uri.getRawQuery() != null) {
            throw new IllegalArgumentException("auth_config.token_url must not contain a query string");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException(TOKEN_URL_MUST_NAME_A_HOST_MESSAGE);
        }
        int port = uri.getPort();
        String path = uri.getRawPath();
        return HTTPS
            + "://"
            + canonicalHost(host)
            + (port == -1 || port == DEFAULT_HTTPS_PORT ? "" : ":" + port)
            + (path == null || path.isEmpty() ? "/" : path);
    }

    /**
     * {@code host} as the token client's URL library prints it: a host name lower-cased; an IPv6 literal in the
     * compressed form of RFC 5952, lower-case groups without leading zeros and the first longest run of two or more
     * zero groups as {@code ::}; an IPv4-mapped literal as the IPv4 address. A literal with a zone id is refused.
     */
    private static String canonicalHost(String host) {
        if (!host.startsWith("[")) {
            return host.toLowerCase(Locale.ROOT);
        }
        if (host.contains("%")) {
            throw new IllegalArgumentException(TOKEN_URL_SCOPED_IPV6_MESSAGE);
        }
        try {
            // A literal is parsed, never resolved.
            return InetAddresses.toUriString(InetAddress.getByName(host.substring(1, host.length() - 1)));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(TOKEN_URL_MUST_NAME_A_HOST_MESSAGE);
        }
    }

    /** Every credential key this configuration references, in sorted order. */
    public Set<String> referencedCredentialKeys() {
        Set<String> keys = new TreeSet<>();
        addKey(keys, tokenUrl);
        addKey(keys, clientId);
        addKey(keys, clientSecret);
        addKey(keys, scope);
        addKey(keys, audience);
        tokenRequestParams.values().forEach(value -> addKey(keys, value));
        return Collections.unmodifiableSet(keys);
    }

    private static void addKey(Set<String> keys, String value) {
        if (CredentialReference.isReference(value)) {
            keys.add(CredentialReference.keyOf(value));
        }
    }

    /** A copy of this configuration that, when emitted, marks the members of {@code stored} it lacks as removed. */
    public OAuth2AuthConfig replacing(OAuth2AuthConfig stored) {
        return toBuilder().replaced(stored).build();
    }

    /** Whether {@code stored} has a member, at any depth of nested objects, that this configuration lacks. */
    public boolean dropsMembers(OAuth2AuthConfig stored) {
        return markRemoved(stored.document(), document());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        Map<String, Object> document = document();
        if (replaced != null) {
            markRemoved(replaced.document(), document);
        }
        return builder.map(document);
    }

    /**
     * The members present, in the member order of the block, with {@code token_request_params} in key order. An empty
     * {@code token_request_params} and a default {@code token_response} are omitted; the {@code grant_type} default of
     * {@code oauth2} is emitted.
     */
    private Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(TYPE_FIELD, type.getValue());
        putIfPresent(document, TOKEN_URL_FIELD, tokenUrl);
        putIfPresent(document, GRANT_TYPE_FIELD, grantType);
        putIfPresent(document, CLIENT_AUTH_METHOD_FIELD, clientAuthMethod);
        putIfPresent(document, CLIENT_ID_FIELD, clientId);
        putIfPresent(document, CLIENT_SECRET_FIELD, clientSecret);
        putIfPresent(document, SCOPE_FIELD, scope);
        putIfPresent(document, AUDIENCE_FIELD, audience);
        if (!tokenRequestParams.isEmpty()) {
            document.put(TOKEN_REQUEST_PARAMS_FIELD, new LinkedHashMap<>(tokenRequestParams));
        }
        if (!tokenResponse.equals(TokenResponseSpec.defaults())) {
            document.put(TOKEN_RESPONSE_FIELD, tokenResponse.document());
        }
        return document;
    }

    private static void putIfPresent(Map<String, Object> document, String field, String value) {
        if (value != null) {
            document.put(field, value);
        }
    }

    /** Marks each member of {@code stored} that {@code document} lacks with an empty array; returns whether any was marked. */
    @SuppressWarnings("unchecked")
    private static boolean markRemoved(Map<String, Object> stored, Map<String, Object> document) {
        boolean added = false;
        for (Map.Entry<String, Object> member : stored.entrySet()) {
            Object value = document.get(member.getKey());
            if (value == null) {
                document.put(member.getKey(), List.of());
                added = true;
            } else if (member.getValue() instanceof Map && value instanceof Map) {
                added |= markRemoved((Map<String, Object>) member.getValue(), (Map<String, Object>) value);
            }
        }
        return added;
    }

    /**
     * The value rules of the block, run once the members are assigned so that a configuration parsed, built or
     * streamed meets the same ones: no empty member, no reserved or referencing {@code token_request_params},
     * placeholders only as whole-value credential references where a reference is allowed, {@code client_secret} a
     * reference, and for {@code oauth2} a well-formed {@code token_url}, a supported {@code client_auth_method} and an
     * allowed grant.
     */
    private void validateMembers() {
        checkNotEmpty("auth_config." + TOKEN_URL_FIELD, tokenUrl);
        checkNotEmpty("auth_config." + GRANT_TYPE_FIELD, grantType);
        checkNotEmpty("auth_config." + CLIENT_AUTH_METHOD_FIELD, clientAuthMethod);
        checkNotEmpty("auth_config." + CLIENT_ID_FIELD, clientId);
        checkNotEmpty("auth_config." + CLIENT_SECRET_FIELD, clientSecret);
        checkNotEmpty("auth_config." + SCOPE_FIELD, scope);
        checkNotEmpty("auth_config." + AUDIENCE_FIELD, audience);

        String paramsPath = "auth_config." + TOKEN_REQUEST_PARAMS_FIELD;
        for (Map.Entry<String, String> param : tokenRequestParams.entrySet()) {
            String name = param.getKey();
            String normalized = name.toLowerCase(Locale.ROOT);
            if (RESERVED_TOKEN_REQUEST_PARAMS.contains(normalized) || normalized.startsWith(RESERVED_TOKEN_REQUEST_PARAM_PREFIX)) {
                throw new IllegalArgumentException(paramsPath + " may not set the reserved parameter [" + name + "]");
            }
            if (CredentialReference.isReference(param.getValue())) {
                throw new IllegalArgumentException(
                    paramsPath + "." + name + ": ${credential.<key>} references in token_request_params are not supported in this version"
                );
            }
        }

        checkPlaceholders("auth_config." + TOKEN_URL_FIELD, tokenUrl);
        checkLiteral("auth_config." + GRANT_TYPE_FIELD, grantType);
        checkLiteral("auth_config." + CLIENT_AUTH_METHOD_FIELD, clientAuthMethod);
        checkPlaceholders("auth_config." + CLIENT_ID_FIELD, clientId);
        checkPlaceholders("auth_config." + CLIENT_SECRET_FIELD, clientSecret);
        checkPlaceholders("auth_config." + SCOPE_FIELD, scope);
        checkPlaceholders("auth_config." + AUDIENCE_FIELD, audience);
        for (Map.Entry<String, String> param : tokenRequestParams.entrySet()) {
            if (param.getKey().contains("${")) {
                throw new IllegalArgumentException("Field names in " + paramsPath + " must not contain ${");
            }
            checkPlaceholders(paramsPath + "." + param.getKey(), param.getValue());
        }
        String responsePath = "auth_config." + TOKEN_RESPONSE_FIELD + ".";
        checkLiteral(responsePath + TokenResponseSpec.ACCESS_TOKEN_FIELD_FIELD, tokenResponse.getAccessTokenField());
        checkLiteral(responsePath + TokenResponseSpec.EXPIRES_IN_FIELD_FIELD, tokenResponse.getExpiresInField());
        checkLiteral(responsePath + TokenResponseSpec.EXPIRES_AT_FIELD_FIELD, tokenResponse.getExpiresAtField());

        if (clientSecret != null && !CredentialReference.isReference(clientSecret)) {
            throw new IllegalArgumentException(CLIENT_SECRET_MUST_BE_REFERENCE_MESSAGE);
        }

        if (type == ConnectorAuthType.OAUTH2) {
            if (tokenUrl == null) {
                throw new IllegalArgumentException("auth_config.token_url is required for type oauth2");
            }
            if (!CredentialReference.isReference(tokenUrl)) {
                // Only the form is checked here; the canonical string is built again where it is matched.
                canonicalTokenUrl(tokenUrl);
            }
            // Required for oauth2.
            validateClientAuthMethod(clientAuthMethod);
        }
        validateGrantType(grantType);
    }

    /** {@code value}, when present, is not the empty string. */
    private static void checkNotEmpty(String path, String value) {
        if (value != null && value.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be empty");
        }
    }

    /** {@code value}, when present, contains no <code>${</code> or is exactly one {@code ${credential.<key>}} reference. */
    private static void checkPlaceholders(String path, String value) {
        if (value == null) {
            return;
        }
        if (value.contains(PARAMETERS_PLACEHOLDER)) {
            throw new IllegalArgumentException(path + PARAMETERS_NOT_ALLOWED);
        }
        if (value.contains("${") && !CredentialReference.isReference(value)) {
            throw new IllegalArgumentException(path + " must be a whole-value ${credential.<key>} reference or contain no ${");
        }
    }

    /** Like {@link #checkPlaceholders}, for members that name a method or a field and so cannot be references. */
    private static void checkLiteral(String path, String value) {
        if (value != null && value.contains("${")) {
            throw new IllegalArgumentException(
                path + (value.contains(PARAMETERS_PLACEHOLDER) ? PARAMETERS_NOT_ALLOWED : " must be a literal value, not a reference")
            );
        }
    }

    private static void validateClientAuthMethod(String clientAuthMethod) {
        if (clientAuthMethod == null) {
            throw new IllegalArgumentException(
                "auth_config.client_auth_method is required for type oauth2; use "
                    + CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC
                    + ", "
                    + CLIENT_AUTH_METHOD_CLIENT_SECRET_POST
                    + " or "
                    + CLIENT_AUTH_METHOD_NONE
            );
        }
        if (CLIENT_AUTH_METHOD_PRIVATE_KEY_JWT.equals(clientAuthMethod)) {
            throw new IllegalArgumentException(PRIVATE_KEY_JWT_NOT_SUPPORTED_MESSAGE);
        }
        if (!CLIENT_AUTH_METHODS.contains(clientAuthMethod)) {
            throw new IllegalArgumentException(
                "auth_config.client_auth_method must be one of "
                    + CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC
                    + ", "
                    + CLIENT_AUTH_METHOD_CLIENT_SECRET_POST
                    + ", "
                    + CLIENT_AUTH_METHOD_NONE
            );
        }
    }

    private static void validateGrantType(String grantType) {
        if (grantType == null) {
            return;
        }
        String normalized = grantType.toLowerCase(Locale.ROOT);
        if (DENIED_GRANT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("auth_config.grant_type [" + normalized + "] is not allowed");
        }
    }

    private static Map<String, String> parseTokenRequestParams(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        String path = "auth_config." + TOKEN_REQUEST_PARAMS_FIELD;
        if (!node.isObject()) {
            throw new IllegalArgumentException(path + " must be a JSON object");
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (!value.isString()) {
                throw new IllegalArgumentException(path + "." + name + " must be a string");
            }
            params.put(name, value.stringValue());
        }
        return params;
    }

    private static TokenResponseSpec parseTokenResponse(JsonNode node) {
        if (isAbsent(node)) {
            return TokenResponseSpec.defaults();
        }
        String path = "auth_config." + TOKEN_RESPONSE_FIELD;
        if (!node.isObject()) {
            throw new IllegalArgumentException(path + " must be a JSON object");
        }
        for (String field : node.propertyNames()) {
            if (!TokenResponseSpec.FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown " + path + " field [" + field + "]");
            }
        }
        return TokenResponseSpec
            .builder()
            .accessTokenField(optionalString(node, TokenResponseSpec.ACCESS_TOKEN_FIELD_FIELD, path))
            .expiresInField(optionalString(node, TokenResponseSpec.EXPIRES_IN_FIELD_FIELD, path))
            .expiresAtField(optionalString(node, TokenResponseSpec.EXPIRES_AT_FIELD_FIELD, path))
            .defaultTtlSeconds(optionalInteger(node, TokenResponseSpec.DEFAULT_TTL_SECONDS_FIELD, path))
            .build();
    }

    private static String optionalString(JsonNode parent, String field) {
        return optionalString(parent, field, "auth_config");
    }

    /** A string, or null when the field is absent. */
    private static String optionalString(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (isAbsent(node)) {
            return null;
        }
        if (!node.isString()) {
            throw new IllegalArgumentException(path + "." + field + " must be a string");
        }
        return node.stringValue();
    }

    /** An exact integer, as a number (1800 or 1800.0) or as an integral string ("1800"), or null when the field is absent. */
    private static Integer optionalInteger(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (isAbsent(node)) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return node.decimalValue().intValueExact();
            }
            if (node.isString()) {
                return new BigDecimal(node.stringValue()).intValueExact();
            }
        } catch (ArithmeticException | NumberFormatException e) {
            // Reported below.
        }
        throw new IllegalArgumentException(path + "." + field + " must be an integer");
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null;
    }

    public static String unknownFieldMessage(String field) {
        return "Unknown auth_config field [" + field + "]";
    }
}
