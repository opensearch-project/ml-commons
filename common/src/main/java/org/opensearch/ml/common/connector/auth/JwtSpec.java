/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
 * behalf, for which audience, with which key and algorithm, for how long, and the literal claims added to it. The
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
    public static final String PRIVATE_KEY_FIELD = "private_key";
    public static final Set<String> FIELDS = Set
        .of(
            ISSUER_FIELD,
            SUBJECT_FIELD,
            AUDIENCE_FIELD,
            SIGNING_ALG_FIELD,
            LIFETIME_SECONDS_FIELD,
            ADDITIONAL_CLAIMS_FIELD,
            PRIVATE_KEY_FIELD
        );

    public static final String DEFAULT_SIGNING_ALG = "RS256";
    /** Algorithms of the JOSE registry that this version does not sign with; they are refused by name. */
    public static final Set<String> UNSUPPORTED_SIGNING_ALGS = Set.of("PS256", "ES256");
    public static final int DEFAULT_LIFETIME_SECONDS = 60;
    public static final int MIN_LIFETIME_SECONDS = 1;
    public static final int MAX_LIFETIME_SECONDS = 3600;
    /** The claims the signer sets itself; {@code additional_claims} may not set them. */
    public static final Set<String> RESERVED_CLAIMS = Set.of("iss", "sub", "aud", "exp", "iat", "jti");

    private static final String PATH = "auth_config." + OAuth2AuthConfig.JWT_FIELD + ".";

    public static final String ISSUER_REQUIRED_MESSAGE = PATH + ISSUER_FIELD + " is required";
    public static final String PRIVATE_KEY_REQUIRED_MESSAGE = PATH + PRIVATE_KEY_FIELD + " is required";
    public static final String PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE = PATH
        + PRIVATE_KEY_FIELD
        + " must be a ${credential.<key>} reference, never the key itself";
    public static final String SIGNING_ALG_MESSAGE = PATH + SIGNING_ALG_FIELD + " must be " + DEFAULT_SIGNING_ALG;
    public static final String LIFETIME_MESSAGE = PATH
        + LIFETIME_SECONDS_FIELD
        + " must be an integer between "
        + MIN_LIFETIME_SECONDS
        + " and "
        + MAX_LIFETIME_SECONDS;

    /** A literal or a {@code ${credential.<key>}} reference; for a Google service account, its email address. */
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
        String privateKey
    ) {
        this.issuer = issuer;
        this.subject = subject;
        this.audience = audience;
        this.signingAlg = signingAlg == null ? DEFAULT_SIGNING_ALG : signingAlg;
        this.lifetimeSeconds = lifetimeSeconds == null ? DEFAULT_LIFETIME_SECONDS : lifetimeSeconds;
        this.additionalClaims = additionalClaims == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(additionalClaims));
        this.privateKey = privateKey;
        validateMembers();
    }

    public JwtSpec(StreamInput in) throws IOException {
        this(
            in.readString(),
            in.readOptionalString(),
            in.readOptionalString(),
            in.readString(),
            in.readInt(),
            in.readMap(StreamInput::readString, StreamInput::readString),
            in.readString()
        );
    }

    /** The message for a {@code signing_alg} the schema names but this version does not sign with. */
    public static String signingAlgNotSupportedMessage(String signingAlg) {
        return PATH + SIGNING_ALG_FIELD + " [" + signingAlg + "] is not supported in this version";
    }

    /** The message for an {@code additional_claims} member that names a claim the signer sets itself. */
    public static String reservedClaimMessage(String name) {
        return PATH + ADDITIONAL_CLAIMS_FIELD + " may not set the reserved claim [" + name + "]";
    }

    /**
     * The value rules of the block, run once the members are assigned so that a specification parsed, built or
     * streamed meets the same ones: an issuer and a private key are present, the issuer, subject and audience are
     * literals or whole-value credential references, the algorithm is RS256, the lifetime is within bounds, the
     * additional claims are literals that name no reserved claim, and the private key is a reference.
     */
    private void validateMembers() {
        if (issuer == null) {
            throw new IllegalArgumentException(ISSUER_REQUIRED_MESSAGE);
        }
        OAuth2AuthConfig.checkNotEmpty(PATH + ISSUER_FIELD, issuer);
        OAuth2AuthConfig.checkNotEmpty(PATH + SUBJECT_FIELD, subject);
        OAuth2AuthConfig.checkNotEmpty(PATH + AUDIENCE_FIELD, audience);
        OAuth2AuthConfig.checkPlaceholders(PATH + ISSUER_FIELD, issuer);
        OAuth2AuthConfig.checkPlaceholders(PATH + SUBJECT_FIELD, subject);
        OAuth2AuthConfig.checkPlaceholders(PATH + AUDIENCE_FIELD, audience);
        if (UNSUPPORTED_SIGNING_ALGS.contains(signingAlg)) {
            throw new IllegalArgumentException(signingAlgNotSupportedMessage(signingAlg));
        }
        if (!DEFAULT_SIGNING_ALG.equals(signingAlg)) {
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
        if (privateKey == null) {
            throw new IllegalArgumentException(PRIVATE_KEY_REQUIRED_MESSAGE);
        }
        if (!CredentialReference.isReference(privateKey)) {
            throw new IllegalArgumentException(PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(issuer);
        out.writeOptionalString(subject);
        out.writeOptionalString(audience);
        out.writeString(signingAlg);
        out.writeInt(lifetimeSeconds);
        out.writeMap(additionalClaims, StreamOutput::writeString, StreamOutput::writeString);
        out.writeString(privateKey);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.map(document());
    }

    /**
     * The members present, in the member order of the block: {@code signing_alg} and {@code lifetime_seconds} always,
     * defaults included; {@code additional_claims} only when there are any, in key order.
     */
    Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(ISSUER_FIELD, issuer);
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
        document.put(PRIVATE_KEY_FIELD, privateKey);
        return document;
    }
}
