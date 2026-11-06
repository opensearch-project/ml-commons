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
        PRIVATE_IP_DISABLED,
        Collections.emptyList(),
        Collections.emptyList()
    );

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
        assertEquals("Remote inference host name has restricted ip address: 169.254.0.1", e3.getMessage());

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
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        validatingHttpClient.validate("ftp://api.openai.com", "ftp", TEST_HOST, 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange1_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: 65537");
        validatingHttpClient.validate("https://api.openai.com", HTTPS, TEST_HOST, 65537, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange2_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: -10");
        validatingHttpClient.validate("http://api.openai.com", HTTP, TEST_HOST, -10, PRIVATE_IP_DISABLED);
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

    private static final String[] IPV6_PRIVATE = { "fd00::1", "fc00::1", "fec0::1", "::1" };
    private static final String[] ALWAYS_BLOCKED = {
        "fe80::1",
        "fd00:ec2::254",
        "fd00:ec2::253",
        "169.254.169.254",
        "169.254.1.1",
        "::",
        "0.0.0.0" };

    private static String url(String ip) {
        return ip.indexOf(':') >= 0 ? "http://[" + ip + "]/" : "http://" + ip + "/";
    }

    @Test
    public void test_ipv6PrivateRanges_rejectedWhenPrivateIpDisabled() {
        for (String ip : IPV6_PRIVATE) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> validatingHttpClient.validate(url(ip), HTTP, ip, 80, PRIVATE_IP_DISABLED)
            );
            assertEquals("Remote inference host name has private ip address: " + ip, e.getMessage());
            // bracketed literal, as the SDK hands it over
            assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> validatingHttpClient.validate(url(ip), HTTP, "[" + ip + "]", 80, PRIVATE_IP_DISABLED)
            );
        }
    }

    @Test
    public void test_ipv6PrivateRanges_allowedWhenPrivateIpEnabled() throws Exception {
        for (String ip : IPV6_PRIVATE) {
            validatingHttpClient.validate(url(ip), HTTP, ip, 80, PRIVATE_IP_ENABLED);
        }
    }

    @Test
    public void test_linkLocalAndMetadata_rejectedRegardlessOfPrivateIpEnabled() {
        for (String ip : ALWAYS_BLOCKED) {
            for (boolean enabled : new boolean[] { PRIVATE_IP_DISABLED, PRIVATE_IP_ENABLED }) {
                IllegalArgumentException e = assertThrows(
                    ip,
                    IllegalArgumentException.class,
                    () -> validatingHttpClient.validate(url(ip), HTTP, ip, 80, enabled)
                );
                assertEquals("Remote inference host name has restricted ip address: " + ip, e.getMessage());
            }
        }
    }

    @Test
    public void test_embeddedIpv4_classifiedByEmbeddedAddress() throws Exception {
        for (String ip : new String[] { "::10.0.0.1", "64:ff9b::10.0.0.1", "64:ff9b:1::10.0.0.1", "::ffff:127.0.0.1" }) {
            assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> validatingHttpClient.validate(url(ip), HTTP, ip, 80, PRIVATE_IP_DISABLED)
            );
            validatingHttpClient.validate(url(ip), HTTP, ip, 80, PRIVATE_IP_ENABLED);
        }
        for (String ip : new String[] { "64:ff9b::169.254.169.254", "::169.254.169.254" }) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> validatingHttpClient.validate(url(ip), HTTP, ip, 80, PRIVATE_IP_ENABLED)
            );
            assertEquals("Remote inference host name has restricted ip address: " + ip, e.getMessage());
        }
        validatingHttpClient.validate(url("64:ff9b::203.0.113.1"), HTTP, "64:ff9b::203.0.113.1", 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_publicAddresses_allowedWhenPrivateIpDisabled() throws Exception {
        for (String ip : new String[] { "2001:db8::1", "2606:4700::1111", "203.0.113.1", "64:ff9b:2::10.0.0.1" }) {
            validatingHttpClient.validate(url(ip), HTTP, ip, 80, PRIVATE_IP_DISABLED);
        }
    }

    @Test
    public void test_classify_takesStrictestClassAcrossResolvedAddresses() throws Exception {
        java.net.InetAddress pub = java.net.InetAddress.getByName("203.0.113.1");
        java.net.InetAddress ula = java.net.InetAddress.getByName("fd00::1");
        java.net.InetAddress link = java.net.InetAddress.getByName("fe80::1");
        assertEquals(
            MLValidatableAsyncHttpClient.AddressClass.PUBLIC,
            MLValidatableAsyncHttpClient.classify(new java.net.InetAddress[] { pub })
        );
        assertEquals(
            MLValidatableAsyncHttpClient.AddressClass.PRIVATE,
            MLValidatableAsyncHttpClient.classify(new java.net.InetAddress[] { pub, ula })
        );
        assertEquals(
            MLValidatableAsyncHttpClient.AddressClass.BLOCKED,
            MLValidatableAsyncHttpClient.classify(new java.net.InetAddress[] { ula, link, pub })
        );
    }

    @Test
    public void test_ipv6UniqueLocal_subjectToPrivateEndpointAllowlist() throws Exception {
        trustingPrivate("^http://\\[fd00::1\\]/.*$").validate("http://[fd00::1]/predict", HTTP, "fd00::1", 80, PRIVATE_IP_ENABLED);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> trustingPrivate("^http://10\\.0\\.0\\.1/.*$")
                .validate("http://[fd00::1]/predict", HTTP, "fd00::1", 80, PRIVATE_IP_ENABLED)
        );
        assertEquals("Connector URL is not matching the trusted connector private endpoint regex", e.getMessage());
    }

    @Test
    public void test_restrictedPattern_stillCheckedFirstForIpv6() {
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            Collections.singletonList(Pattern.compile("fd00:1:.*"))
        );
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://[fd00:1::5]/", HTTP, "fd00:1::5", 80, PRIVATE_IP_ENABLED)
        );
        assertEquals("Remote inference host name has restricted ip address: fd00:1::5", e.getMessage());
    }

    private static MLValidatableAsyncHttpClient trustingPrivate(String... patterns) {
        return new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_ENABLED,
            Arrays.stream(patterns).map(Pattern::compile).collect(java.util.stream.Collectors.toList()),
            Collections.emptyList()
        );
    }

    @Test
    public void test_trustedPrivateEndpoint_matchesCanonicalForm() throws Exception {
        MLValidatableAsyncHttpClient client = trustingPrivate("^http://10\\.0\\.0\\.1/v1/.*$");
        client.validate("http://10.0.0.1/v1/chat", HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED);
        client.validate("HTTP://10.0.0.1/v1/chat", HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_trustedPrivateEndpoint_rejectsUserInfoFragmentAndUnparseable() {
        MLValidatableAsyncHttpClient client = trustingPrivate("^https?://.*$");
        String message = "Connector URL is not matching the trusted connector private endpoint regex";
        for (String endpoint : new String[] { "http://user@10.0.0.1/v1", "http://10.0.0.1/v1#frag", "http://10.0.0.1/a b" }) {
            IllegalArgumentException e = assertThrows(
                endpoint,
                IllegalArgumentException.class,
                () -> client.validate(endpoint, HTTP, "10.0.0.1", 80, PRIVATE_IP_ENABLED)
            );
            assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void test_trustedPrivateEndpoint_publicHostNotParsed() throws Exception {
        // Public hosts skip the private-endpoint list entirely, even when the endpoint string would not parse.
        MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
            mock(SdkAsyncHttpClient.class),
            PRIVATE_IP_DISABLED,
            Arrays.asList(Pattern.compile("^http://10\\..*$")),
            Collections.emptyList()
        );
        client.validate("http://177.16.0.1/a b", HTTP, "177.16.0.1", 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_trustedPrivateEndpoint_hostWildcardPatternStillAdmitsPathTrick() throws Exception {
        // A '.*' in the host position spans into the path even on the canonical form; the settings validator
        // warns about such patterns (see MLCommonsSettingsTests). Documented here so the behaviour is explicit.
        MLValidatableAsyncHttpClient client = trustingPrivate("^https://.*\\.internal\\.example/.*$");
        client.validate("https://127.0.0.1/x.internal.example/y", HTTPS, "127.0.0.1", 443, PRIVATE_IP_ENABLED);
    }
}
