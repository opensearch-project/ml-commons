/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.opensearch.ml.common.connector.HttpConnector.MINIMAL_SUPPORTED_VERSION_FOR_PRIVATE_KEY_JWT;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The assertion a connector signs for the JWT bearer grant ({@code auth_config.jwt}, RFC 7523): who issues it, on whose
 * behalf, for which audience, with which key and algorithm (RS256, PS256 or ES256), for how long, the literal claims
 * added to it and the literal members added to its JWS header ({@code kid}, {@code x5t}, {@code x5t#S256}). The
 * private key is always a {@code ${credential.<key>}} reference, so the key material never enters the configuration;
 * it is read from the connector credential when an assertion is signed.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class JwtSpec implements ToXContentObject, Writeable {
    public static final String ISSUER_FIELD = "issuer";
    public static final String SUBJECT_FIELD = "subject";
    public static final String AUDIENCE_FIELD = "audience";
    public static final String SIGNING_ALG_FIELD = "signing_alg";
    public static final String LIFETIME_SECONDS_FIELD = "lifetime_seconds";
    public static final String ADDITIONAL_CLAIMS_FIELD = "additional_claims";
    public static final String HEADER_FIELD = "header";
    public static final String PRIVATE_KEY_FIELD = "private_key";
    public static final Set<String> FIELDS = Set
        .of(
            ISSUER_FIELD,
            SUBJECT_FIELD,
            AUDIENCE_FIELD,
            SIGNING_ALG_FIELD,
            LIFETIME_SECONDS_FIELD,
            ADDITIONAL_CLAIMS_FIELD,
            HEADER_FIELD,
            PRIVATE_KEY_FIELD
        );

    /** RSASSA-PKCS1-v1_5 with SHA-256 (RFC 7518 section 3.3). */
    public static final String RS256 = "RS256";
    /** RSASSA-PSS with SHA-256, MGF1 with SHA-256 and a 32-byte salt (RFC 7518 section 3.5). */
    public static final String PS256 = "PS256";
    /** ECDSA on P-256 with SHA-256 (RFC 7518 section 3.4). */
    public static final String ES256 = "ES256";
    public static final String DEFAULT_SIGNING_ALG = RS256;
    public static final Set<String> SIGNING_ALGS = Set.of(RS256, PS256, ES256);
    /** The key identifier of RFC 7515 section 4.1.4, free-form. */
    public static final String KID = "kid";
    /** The SHA-1 certificate thumbprint of RFC 7515 section 4.1.7. */
    public static final String X5T = "x5t";
    /** The SHA-256 certificate thumbprint of RFC 7515 section 4.1.8. */
    public static final String X5T_S256 = "x5t#S256";
    /** The members {@code header} may hold, each added to the JWS header as written. */
    public static final Set<String> HEADER_MEMBERS = Set.of(KID, X5T, X5T_S256);
    public static final int DEFAULT_LIFETIME_SECONDS = 60;
    public static final int MIN_LIFETIME_SECONDS = 1;
    public static final int MAX_LIFETIME_SECONDS = 3600;
    /** The claims the signer sets itself; {@code additional_claims} may not set them. */
    public static final Set<String> RESERVED_CLAIMS = Set.of("iss", "sub", "aud", "exp", "iat", "jti");

    private static final String PATH = "auth_config." + OAuth2AuthConfig.JWT_FIELD + ".";

    /** Thrown where the issuer is required: by the jwt-bearer grant ({@link OAuth2AuthConfig}). */
    public static final String ISSUER_REQUIRED_MESSAGE = PATH + ISSUER_FIELD + " is required";
    public static final String PRIVATE_KEY_REQUIRED_MESSAGE = PATH + PRIVATE_KEY_FIELD + " is required";
    public static final String PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE = PATH
        + PRIVATE_KEY_FIELD
        + " must be a ${credential.<key>} reference, never the key itself";
    public static final String SIGNING_ALG_MESSAGE = PATH + SIGNING_ALG_FIELD + " must be " + RS256 + ", " + PS256 + " or " + ES256;
    public static final String LIFETIME_MESSAGE = PATH
        + LIFETIME_SECONDS_FIELD
        + " must be an integer between "
        + MIN_LIFETIME_SECONDS
        + " and "
        + MAX_LIFETIME_SECONDS;

    /** The decoded length of each certificate thumbprint member: a SHA-1 and a SHA-256 digest. */
    private static final Map<String, Integer> THUMBPRINT_BYTES = Map.of(X5T, 20, X5T_S256, 32);
    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * A literal or a {@code ${credential.<key>}} reference; for a Google service account, its email address. Required by
     * the jwt-bearer grant, which {@link OAuth2AuthConfig} checks.
     */
    private final String issuer;
    /** Optional and never defaulted: a provider that reads {@code sub} gets it only when it is configured. */
    private final String subject;
    /** Optional; an assertion without one is addressed to the token endpoint it is sent to. */
    private final String audience;
    private final String signingAlg;
    private final int lifetimeSeconds;
    /**
     * Never null, in key order, so that equal specifications emit the same document; literal values may be sensitive,
     * so they stay out of {@link #toString()}.
     */
    @ToString.Exclude
    private final Map<String, String> additionalClaims;
    /**
     * The members added to the JWS header after {@code alg} and {@code typ}; never null, in key order ({@code kid},
     * {@code x5t}, {@code x5t#S256}). Empty is the same as absent.
     */
    private final Map<String, String> header;
    /** Always a {@code ${credential.<key>}} reference, never the key itself. */
    private final String privateKey;

    @Builder(toBuilder = true)
    private JwtSpec(
        String issuer,
        String subject,
        String audience,
        String signingAlg,
        Integer lifetimeSeconds,
        Map<String, String> additionalClaims,
        Map<String, String> header,
        String privateKey
    ) {
        this.issuer = issuer;
        this.subject = subject;
        this.audience = audience;
        this.signingAlg = signingAlg == null ? DEFAULT_SIGNING_ALG : signingAlg;
        this.lifetimeSeconds = lifetimeSeconds == null ? DEFAULT_LIFETIME_SECONDS : lifetimeSeconds;
        this.additionalClaims = additionalClaims == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(additionalClaims));
        this.header = header == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(header));
        this.privateKey = privateKey;
        validateMembers();
    }

    public JwtSpec(StreamInput in) throws IOException {
        // A node before the private_key_jwt version writes the issuer as a required string and no header map.
        this(
            in.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_PRIVATE_KEY_JWT) ? in.readOptionalString() : in.readString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readString(),
            in.readInt(),
            in.readMap(StreamInput::readString, StreamInput::readString),
            in.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_PRIVATE_KEY_JWT)
                ? in.readMap(StreamInput::readString, StreamInput::readString)
                : Map.of(),
            in.readString()
        );
    }

    /** The message for a {@code header} member other than {@code kid}, {@code x5t} and {@code x5t#S256}. */
    public static String unknownHeaderFieldMessage(String name) {
        return "Unknown " + PATH + HEADER_FIELD + " field [" + name + "]";
    }

    /** The message for an {@code x5t} or {@code x5t#S256} value that is not a thumbprint of the expected length. */
    public static String thumbprintMessage(String member) {
        return PATH
            + HEADER_FIELD
            + "."
            + member
            + " must be the base64url-encoded "
            + (X5T.equals(member) ? "SHA-1" : "SHA-256")
            + " certificate thumbprint without padding";
    }

    /** The message for an {@code additional_claims} member that names a claim the signer sets itself. */
    public static String reservedClaimMessage(String name) {
        return PATH + ADDITIONAL_CLAIMS_FIELD + " may not set the reserved claim [" + name + "]";
    }

    /**
     * The value rules of the block, run once the members are assigned so that a specification parsed, built or
     * streamed meets the same ones: a private key is present, the issuer, subject and audience are literals or
     * whole-value credential references, the algorithm is RS256, PS256 or ES256, the lifetime is within bounds, the
     * additional claims are literals that name no reserved claim, the header members are known non-empty literals
     * whose thumbprints have the length of their digest, and the private key is a reference.
     */
    private void validateMembers() {
        OAuth2AuthConfig.checkNotEmpty(PATH + ISSUER_FIELD, issuer);
        OAuth2AuthConfig.checkNotEmpty(PATH + SUBJECT_FIELD, subject);
        OAuth2AuthConfig.checkNotEmpty(PATH + AUDIENCE_FIELD, audience);
        OAuth2AuthConfig.checkPlaceholders(PATH + ISSUER_FIELD, issuer);
        OAuth2AuthConfig.checkPlaceholders(PATH + SUBJECT_FIELD, subject);
        OAuth2AuthConfig.checkPlaceholders(PATH + AUDIENCE_FIELD, audience);
        if (!SIGNING_ALGS.contains(signingAlg)) {
            throw new IllegalArgumentException(SIGNING_ALG_MESSAGE);
        }
        if (lifetimeSeconds < MIN_LIFETIME_SECONDS || lifetimeSeconds > MAX_LIFETIME_SECONDS) {
            throw new IllegalArgumentException(LIFETIME_MESSAGE);
        }
        String claimsPath = PATH + ADDITIONAL_CLAIMS_FIELD;
        for (Map.Entry<String, String> claim : additionalClaims.entrySet()) {
            String name = claim.getKey();
            if (RESERVED_CLAIMS.contains(name)) {
                throw new IllegalArgumentException(reservedClaimMessage(name));
            }
            if (name.contains("${")) {
                throw new IllegalArgumentException("Field names in " + claimsPath + " must not contain ${");
            }
            if (claim.getValue() == null) {
                throw new IllegalArgumentException(claimsPath + "." + name + " must be a string");
            }
            OAuth2AuthConfig.checkLiteral(claimsPath + "." + name, claim.getValue());
        }
        String headerPath = PATH + HEADER_FIELD;
        for (Map.Entry<String, String> member : header.entrySet()) {
            String name = member.getKey();
            String value = member.getValue();
            if (!HEADER_MEMBERS.contains(name)) {
                throw new IllegalArgumentException(unknownHeaderFieldMessage(name));
            }
            if (value == null) {
                throw new IllegalArgumentException(headerPath + "." + name + " must be a string");
            }
            OAuth2AuthConfig.checkNotEmpty(headerPath + "." + name, value);
            OAuth2AuthConfig.checkLiteral(headerPath + "." + name, value);
            Integer digestBytes = THUMBPRINT_BYTES.get(name);
            if (digestBytes != null && !isBase64UrlOfLength(value, digestBytes)) {
                // A hex thumbprint, as consoles show it, decodes to more bytes than the digest has.
                throw new IllegalArgumentException(thumbprintMessage(name));
            }
        }
        if (privateKey == null) {
            throw new IllegalArgumentException(PRIVATE_KEY_REQUIRED_MESSAGE);
        }
        if (!CredentialReference.isReference(privateKey)) {
            throw new IllegalArgumentException(PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE);
        }
    }

    /** Whether {@code value} is base64url without padding and decodes to {@code bytes} bytes. */
    private static boolean isBase64UrlOfLength(String value, int bytes) {
        if (!BASE64URL.matcher(value).matches()) {
            return false;
        }
        try {
            return Base64.getUrlDecoder().decode(value).length == bytes;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        // A node before the private_key_jwt version reads the issuer as a required string and no header map.
        boolean privateKeyJwt = out.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_PRIVATE_KEY_JWT);
        if (privateKeyJwt) {
            out.writeOptionalString(issuer);
        } else {
            out.writeString(issuer == null ? "" : issuer);
        }
        out.writeOptionalString(subject);
        out.writeOptionalString(audience);
        out.writeString(signingAlg);
        out.writeInt(lifetimeSeconds);
        out.writeMap(additionalClaims, StreamOutput::writeString, StreamOutput::writeString);
        if (privateKeyJwt) {
            out.writeMap(header, StreamOutput::writeString, StreamOutput::writeString);
        }
        out.writeString(privateKey);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.map(document());
    }

    /**
     * The members present, in the member order of the block: {@code signing_alg} and {@code lifetime_seconds} always,
     * defaults included; {@code additional_claims} and {@code header} only when there are any, in key order.
     */
    Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        if (issuer != null) {
            document.put(ISSUER_FIELD, issuer);
        }
        if (subject != null) {
            document.put(SUBJECT_FIELD, subject);
        }
        if (audience != null) {
            document.put(AUDIENCE_FIELD, audience);
        }
        document.put(SIGNING_ALG_FIELD, signingAlg);
        document.put(LIFETIME_SECONDS_FIELD, lifetimeSeconds);
        if (!additionalClaims.isEmpty()) {
            document.put(ADDITIONAL_CLAIMS_FIELD, new LinkedHashMap<>(additionalClaims));
        }
        if (!header.isEmpty()) {
            document.put(HEADER_FIELD, new LinkedHashMap<>(header));
        }
        document.put(PRIVATE_KEY_FIELD, privateKey);
        return document;
    }
}
