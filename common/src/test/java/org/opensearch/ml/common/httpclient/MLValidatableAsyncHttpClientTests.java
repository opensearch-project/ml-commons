/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLValidatableAsyncHttpClientTests {
    private static final String TEST_HOST = "api.openai.com";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final boolean PRIVATE_IP_DISABLED = false;
    private static final boolean PRIVATE_IP_ENABLED = true;

    private final MLValidatableAsyncHttpClient validatingHttpClient = new MLValidatableAsyncHttpClient(
        mock(SdkAsyncHttpClient.class),
        PRIVATE_IP_DISABLED
    );

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_invalidIP_localHost_privateIPDisabled() {
        IllegalArgumentException e1 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate(HTTP, "127.0.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 127.0.0.1", e1.getMessage());

        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate(HTTP, "192.168.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 192.168.0.1", e2.getMessage());

        IllegalArgumentException e3 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate(HTTP, "169.254.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 169.254.0.1", e3.getMessage());

        IllegalArgumentException e4 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate(HTTP, "172.16.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 172.16.0.1", e4.getMessage());

        IllegalArgumentException e5 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate(HTTP, "172.31.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 172.31.0.1", e5.getMessage());
    }

    @Test
    public void test_validateIp_validIp_noException() throws Exception {
        validatingHttpClient.validate(HTTP, TEST_HOST, 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTPS, TEST_HOST, 443, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, "127.0.0.1", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTPS, "127.0.0.1", 443, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "177.16.0.1", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, "177.0.1.1", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, "177.0.0.2", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, "::ffff", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, "172.32.0.1", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "172.2097152", 80, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws Exception {
        try {
            validatingHttpClient.validate(HTTP, "0254.020.00.01", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "172.1048577", 80, PRIVATE_IP_DISABLED);
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "2886729729", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "192.11010049", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "3232300545", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "153.24.76.232", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "177.0.0.1", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate(HTTP, "12.16.2.3", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateIp_rarePrivateIp_NotThrowException() throws Exception {
        validatingHttpClient.validate(HTTP, "0254.020.00.01", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTPS, "0254.020.00.01", 443, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "172.1048577", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "2886729729", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "192.11010049", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "3232300545", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTPS, "0:0:0:0:0:ffff:127.0.0.1", 443, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "153.24.76.232", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTP, "10.24.76.186", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate(HTTPS, "10.24.76.186", 443, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_validateSchemaAndPort_success() throws Exception {
        validatingHttpClient.validate(HTTP, TEST_HOST, 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        validatingHttpClient.validate("ftp", TEST_HOST, 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange1_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: 65537");
        validatingHttpClient.validate(HTTPS, TEST_HOST, 65537, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange2_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: -10");
        validatingHttpClient.validate(HTTP, TEST_HOST, -10, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validatePort_boundaries_success() throws Exception {
        validatingHttpClient.validate(HTTP, TEST_HOST, 65536, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, TEST_HOST, 0, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTP, TEST_HOST, -1, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(HTTPS, TEST_HOST, -1, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(null, TEST_HOST, -1, PRIVATE_IP_DISABLED);
    }

}
