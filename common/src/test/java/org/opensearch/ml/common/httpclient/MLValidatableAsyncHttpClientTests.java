/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLValidatableAsyncHttpClientTests {
    private static final String TEST_HOST = "api.openai.com";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final boolean PRIVATE_IP_DISABLED = false;
    private static final boolean PRIVATE_IP_ENABLED = true;

    private final MLValidatableAsyncHttpClient validatingHttpClient = new MLValidatableAsyncHttpClient(
        mock(SdkAsyncHttpClient.class),
        PRIVATE_IP_DISABLED,
        Collections.emptyList(),
        Collections.emptyList()
    );

    @Test
    public void test_invalidIP_localHost_privateIPDisabled() {
        IllegalArgumentException e1 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("http://127.0.0.1", HTTP, "127.0.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 127.0.0.1", e1.getMessage());

        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("http://127.0.0.1", HTTP, "192.168.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 192.168.0.1", e2.getMessage());

        IllegalArgumentException e3 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("http://127.0.0.1", HTTP, "169.254.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 169.254.0.1", e3.getMessage());

        IllegalArgumentException e4 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("http://127.0.0.1", HTTP, "172.16.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 172.16.0.1", e4.getMessage());

        IllegalArgumentException e5 = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("http://127.0.0.1", HTTP, "172.31.0.1", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has private ip address: 172.31.0.1", e5.getMessage());
    }

    @Test
    public void test_validateIp_validIp_noException() throws Exception {
        validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://api.openai.com", HTTPS, TEST_HOST, 443, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://127.0.0.1", HTTP, "127.0.0.1", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://127.0.0.1", HTTPS, "127.0.0.1", 443, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://177.16.0.1", HTTP, "177.16.0.1", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://177.0.1.1", HTTP, "177.0.1.1", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://177.0.0.2", HTTP, "177.0.0.2", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://::ffff", HTTP, "::ffff", 80, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://172.32.0.1", HTTP, "172.32.0.1", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://172.2097152", HTTP, "172.2097152", 80, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws Exception {
        try {
            validatingHttpClient.validate("http://0254.020.00.01", HTTP, "0254.020.00.01", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://172.1048577", HTTP, "172.1048577", 80, PRIVATE_IP_DISABLED);
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://2886729729", HTTP, "2886729729", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://192.11010049", HTTP, "192.11010049", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://3232300545", HTTP, "3232300545", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://0:0:0:0:0:ffff:127.0.0.1", HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://153.24.76.232", HTTP, "153.24.76.232", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://177.0.0.1", HTTP, "177.0.0.1", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            validatingHttpClient.validate("http://12.16.2.3", HTTP, "12.16.2.3", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateIp_rarePrivateIp_NotThrowException() throws Exception {
        validatingHttpClient.validate("http://0254.020.00.01", HTTP, "0254.020.00.01", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("https://0254.020.00.01", HTTPS, "0254.020.00.01", 443, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://172.1048577", HTTP, "172.1048577", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://2886729729", HTTP, "2886729729", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://192.11010049", HTTP, "192.11010049", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://3232300545", HTTP, "3232300545", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://0:0:0:0:0:ffff:127.0.0.1", HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("https://0:0:0:0:0:ffff:127.0.0.1", HTTPS, "0:0:0:0:0:ffff:127.0.0.1", 443, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://153.24.76.232", HTTP, "153.24.76.232", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("http://10.24.76.186", HTTP, "10.24.76.186", 80, PRIVATE_IP_ENABLED);
        validatingHttpClient.validate("https://10.24.76.186", HTTPS, "10.24.76.186", 443, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_validateSchemaAndPort_success() throws Exception {
        validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("ftp://api.openai.com", "ftp", TEST_HOST, 80, PRIVATE_IP_DISABLED)
        );
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange1_throwException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("https://api.openai.com", HTTPS, TEST_HOST, 65537, PRIVATE_IP_DISABLED)
        );
        assertEquals("Port out of range: 65537", exception.getMessage());
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange2_throwException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, -10, PRIVATE_IP_DISABLED)
        );
        assertEquals("Port out of range: -10", exception.getMessage());
    }

    @Test
    public void test_validatePort_boundaries_success() throws Exception {
        validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, 65536, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, 0, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, -1, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate("http://api.openai.com", HTTPS, TEST_HOST, -1, PRIVATE_IP_DISABLED);
        validatingHttpClient.validate(null, null, TEST_HOST, -1, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_restrictedIpPattern_throwException() {
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("192\\.168\\..*"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            restrictedPatterns
        );

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://192.168.1.1", HTTP, "192.168.1.1", 80, PRIVATE_IP_ENABLED)
        );
        assertEquals("Remote inference host name has restricted ip address: 192.168.1.1", e.getMessage());
    }

    @Test
    public void test_trustedPrivateEndpoint_success() throws Exception {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://10\\.0\\.0\\..*"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            trustedEndpoints,
            Collections.emptyList()
        );

        client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_untrustedPrivateEndpoint_throwException() {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://10\\.0\\.0\\..*"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            trustedEndpoints,
            Collections.emptyList()
        );

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://10.0.1.1", HTTP, "10.0.1.1", 80, PRIVATE_IP_ENABLED)
        );
        assertEquals("Connector URL is not matching the trusted connector private endpoint regex", e.getMessage());
    }

    @Test
    public void test_restrictedIp_loopback_throwException() {
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("127\\..*"), Pattern.compile("::1"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            restrictedPatterns
        );

        assertThrows(IllegalArgumentException.class, () -> client.validate("http://127.0.0.1", HTTP, "127.0.0.1", 80, PRIVATE_IP_ENABLED));
    }

    @Test
    public void test_restrictedIp_imds_throwException() {
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("169\\.254\\..*"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            restrictedPatterns
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://169.254.169.254", HTTP, "169.254.169.254", 80, PRIVATE_IP_ENABLED)
        );
    }

    @Test
    public void test_restrictedIp_takePrecedenceOverPrivateIpEnabled() {
        // Test that restricted IPs are blocked even when private_ip_enabled=true
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("10\\..*"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            restrictedPatterns
        );

        assertThrows(IllegalArgumentException.class, () -> client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED));
    }

    @Test
    public void test_emptyWhitelist_allowsAllPrivateIps() throws Exception {
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            Collections.emptyList()
        );

        client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_whitelistWithPublicIp_success() throws Exception {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://10\\..*"));
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_DISABLED,
            trustedEndpoints,
            Collections.emptyList()
        );

        client.validate("http://api.openai.com", HTTP, "api.openai.com", 443, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_nullPatterns_noException() throws Exception {
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            null,
            null
        );
        client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED);
    }
}
