/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.roundTrip;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.toJson;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.Version;

public class TokenResponseSpecTests {

    private static final TokenResponseSpec CUSTOM = TokenResponseSpec
        .builder()
        .accessTokenField("token")
        .expiresInField("ttl")
        .expiresAtField("expiration")
        .defaultTtlSeconds(1800)
        .build();

    @Test
    public void defaults() {
        TokenResponseSpec spec = TokenResponseSpec.defaults();
        assertEquals("access_token", spec.getAccessTokenField());
        assertEquals("expires_in", spec.getExpiresInField());
        assertNull(spec.getExpiresAtField());
        assertEquals(300, spec.getDefaultTtlSeconds());
        assertEquals(spec, TokenResponseSpec.builder().build());
    }

    @Test
    public void defaultTtlBounds() {
        assertEquals(1, TokenResponseSpec.builder().defaultTtlSeconds(1).build().getDefaultTtlSeconds());
        assertEquals(3600, TokenResponseSpec.builder().defaultTtlSeconds(3600).build().getDefaultTtlSeconds());
        for (int ttl : new int[] { 0, -1, 3601 }) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> TokenResponseSpec.builder().defaultTtlSeconds(ttl).build()
            );
            assertEquals("auth_config.token_response.default_ttl_seconds must be an integer between 1 and 3600", e.getMessage());
        }
    }

    @Test
    public void accessTokenFieldMustNotBeEmpty() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> TokenResponseSpec.builder().accessTokenField("").build()
        );
        assertEquals("auth_config.token_response.access_token_field must not be empty", e.getMessage());
    }

    @Test
    public void toXContent_emitsEveryMemberAndExpiresAtOnlyWhenSet() throws IOException {
        assertEquals(
            "{\"access_token_field\":\"access_token\",\"expires_in_field\":\"expires_in\",\"default_ttl_seconds\":300}",
            toJson(TokenResponseSpec.defaults())
        );
        assertEquals(
            "{\"access_token_field\":\"token\",\"expires_in_field\":\"ttl\",\"expires_at_field\":\"expiration\",\"default_ttl_seconds\":1800}",
            toJson(CUSTOM)
        );
    }

    @Test
    public void stream_roundTripIsEqual() throws IOException {
        assertEquals(TokenResponseSpec.defaults(), roundTrip(TokenResponseSpec.defaults(), Version.CURRENT, TokenResponseSpec::new));
        assertEquals(CUSTOM, roundTrip(CUSTOM, Version.CURRENT, TokenResponseSpec::new));
    }
}
