/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConnectorAddressValidatorTests {
    private static final String TEST_HOST = "api.openai.com";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final boolean PRIVATE_IP_DISABLED = false;
    private static final boolean PRIVATE_IP_ENABLED = true;

    private final ConnectorAddressValidator withPrivateIpDisabled = new ConnectorAddressValidator(
        PRIVATE_IP_DISABLED,
        Collections.emptyList(),
        Collections.emptyList()
    );
    private final ConnectorAddressValidator withPrivateIpEnabled = new ConnectorAddressValidator(
        PRIVATE_IP_ENABLED,
        Collections.emptyList(),
        Collections.emptyList()
    );

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_invalidIP_localHost_privateIPDisabled() {
        IllegalArgumentException e1 = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled.validate("http://127.0.0.1", HTTP, "127.0.0.1", 80)
        );
        assertEquals("Remote inference host name has private ip address: 127.0.0.1", e1.getMessage());

        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled.validate("http://127.0.0.1", HTTP, "192.168.0.1", 80)
        );
        assertEquals("Remote inference host name has private ip address: 192.168.0.1", e2.getMessage());

        IllegalArgumentException e3 = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled.validate("http://127.0.0.1", HTTP, "169.254.0.1", 80)
        );
        assertEquals("Remote inference host name has restricted ip address: 169.254.0.1", e3.getMessage());

        IllegalArgumentException e4 = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled.validate("http://127.0.0.1", HTTP, "172.16.0.1", 80)
        );
        assertEquals("Remote inference host name has private ip address: 172.16.0.1", e4.getMessage());

        IllegalArgumentException e5 = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled.validate("http://127.0.0.1", HTTP, "172.31.0.1", 80)
        );
        assertEquals("Remote inference host name has private ip address: 172.31.0.1", e5.getMessage());
    }

    @Test
    public void test_validateIp_validIp_noException() throws Exception {
        withPrivateIpDisabled.validate("http://api.openai.com", HTTP, TEST_HOST, 80);
        withPrivateIpDisabled.validate("http://api.openai.com", HTTPS, TEST_HOST, 443);
        withPrivateIpEnabled.validate("http://127.0.0.1", HTTP, "127.0.0.1", 80);
        withPrivateIpEnabled.validate("http://127.0.0.1", HTTPS, "127.0.0.1", 443);
        withPrivateIpDisabled.validate("http://177.16.0.1", HTTP, "177.16.0.1", 80);
        withPrivateIpDisabled.validate("http://177.0.1.1", HTTP, "177.0.1.1", 80);
        withPrivateIpDisabled.validate("http://177.0.0.2", HTTP, "177.0.0.2", 80);
        withPrivateIpDisabled.validate("http://::ffff", HTTP, "::ffff", 80);
        withPrivateIpEnabled.validate("http://172.32.0.1", HTTP, "172.32.0.1", 80);
        withPrivateIpEnabled.validate("http://172.2097152", HTTP, "172.2097152", 80);
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws Exception {
        try {
            withPrivateIpDisabled.validate("http://0254.020.00.01", HTTP, "0254.020.00.01", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://172.1048577", HTTP, "172.1048577", 80);
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://2886729729", HTTP, "2886729729", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://192.11010049", HTTP, "192.11010049", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://3232300545", HTTP, "3232300545", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://0:0:0:0:0:ffff:127.0.0.1", HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://153.24.76.232", HTTP, "153.24.76.232", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://177.0.0.1", HTTP, "177.0.0.1", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            withPrivateIpDisabled.validate("http://12.16.2.3", HTTP, "12.16.2.3", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateIp_rarePrivateIp_NotThrowException() throws Exception {
        withPrivateIpEnabled.validate("http://0254.020.00.01", HTTP, "0254.020.00.01", 80);
        withPrivateIpEnabled.validate("https://0254.020.00.01", HTTPS, "0254.020.00.01", 443);
        withPrivateIpEnabled.validate("http://172.1048577", HTTP, "172.1048577", 80);
        withPrivateIpEnabled.validate("http://2886729729", HTTP, "2886729729", 80);
        withPrivateIpEnabled.validate("http://192.11010049", HTTP, "192.11010049", 80);
        withPrivateIpEnabled.validate("http://3232300545", HTTP, "3232300545", 80);
        withPrivateIpEnabled.validate("http://0:0:0:0:0:ffff:127.0.0.1", HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80);
        withPrivateIpEnabled.validate("https://0:0:0:0:0:ffff:127.0.0.1", HTTPS, "0:0:0:0:0:ffff:127.0.0.1", 443);
        withPrivateIpEnabled.validate("http://153.24.76.232", HTTP, "153.24.76.232", 80);
        withPrivateIpEnabled.validate("http://10.24.76.186", HTTP, "10.24.76.186", 80);
        withPrivateIpEnabled.validate("https://10.24.76.186", HTTPS, "10.24.76.186", 443);
    }

    @Test
    public void test_validateSchemaAndPort_success() throws Exception {
        withPrivateIpDisabled.validate("http://api.openai.com", HTTP, TEST_HOST, 80);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        withPrivateIpDisabled.validate("ftp://api.openai.com", "ftp", TEST_HOST, 80);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange1_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: 65537");
        withPrivateIpDisabled.validate("https://api.openai.com", HTTPS, TEST_HOST, 65537);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange2_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: -10");
        withPrivateIpDisabled.validate("http://api.openai.com", HTTP, TEST_HOST, -10);
    }

    @Test
    public void test_validatePort_boundaries_success() throws Exception {
        withPrivateIpDisabled.validate("http://api.openai.com", HTTP, TEST_HOST, 65536);
        withPrivateIpDisabled.validate("http://api.openai.com", HTTP, TEST_HOST, 0);
        withPrivateIpDisabled.validate("http://api.openai.com", HTTP, TEST_HOST, -1);
        withPrivateIpDisabled.validate("http://api.openai.com", HTTPS, TEST_HOST, -1);
        withPrivateIpDisabled.validate(null, null, TEST_HOST, -1);
    }

    @Test
    public void test_restrictedIpPattern_throwException() {
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("192\\.168\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, Collections.emptyList(), restrictedPatterns);

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://192.168.1.1", HTTP, "192.168.1.1", 80)
        );
        assertEquals("Remote inference host name has restricted ip address: 192.168.1.1", e.getMessage());
    }

    @Test
    public void test_trustedPrivateEndpoint_success() throws Exception {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://10\\.0\\.0\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, trustedEndpoints, Collections.emptyList());

        client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80);
    }

    @Test
    public void test_untrustedPrivateEndpoint_throwException() {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://10\\.0\\.0\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, trustedEndpoints, Collections.emptyList());

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://10.0.1.1", HTTP, "10.0.1.1", 80)
        );
        assertEquals("Connector URL is not matching the trusted connector private endpoint regex", e.getMessage());
    }

    @Test
    public void test_restrictedIp_loopback_throwException() {
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("127\\..*"), Pattern.compile("::1"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, Collections.emptyList(), restrictedPatterns);

        assertThrows(IllegalArgumentException.class, () -> client.validate("http://127.0.0.1", HTTP, "127.0.0.1", 80));
    }

    @Test
    public void test_restrictedIp_imds_throwException() {
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("169\\.254\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, Collections.emptyList(), restrictedPatterns);

        assertThrows(IllegalArgumentException.class, () -> client.validate("http://169.254.169.254", HTTP, "169.254.169.254", 80));
    }

    @Test
    public void test_restrictedIp_takePrecedenceOverPrivateIpEnabled() {
        // Test that restricted IPs are blocked even when private_ip_enabled=true
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("10\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, Collections.emptyList(), restrictedPatterns);

        assertThrows(IllegalArgumentException.class, () -> client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80));
    }

    @Test
    public void test_emptyAllowlist_allowsAllPrivateIps() throws Exception {
        ConnectorAddressValidator client = new ConnectorAddressValidator(
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            Collections.emptyList()
        );

        client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80);
    }

    @Test
    public void test_allowlistWithPublicIp_success() throws Exception {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://10\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_DISABLED, trustedEndpoints, Collections.emptyList());

        client.validate("http://api.openai.com", HTTP, "api.openai.com", 443);
    }

    @Test
    public void test_nullPatterns_noException() throws Exception {
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, null, null);
        client.validate("http://10.0.0.1", HTTP, "10.0.0.1", 80);
    }

    private static final String[] IPV6_PRIVATE = { "fd00::1", "fc00::1", "fec0::1", "::1", "::" };
    private static final String[] ALWAYS_BLOCKED = {
        "fe80::1",
        "fd00:ec2::254",
        "fd00:ec2::253",
        "169.254.169.254",
        "169.254.1.1",
        "fd20:ce::254",
        "100.100.100.200" };

    private static String url(String ip) {
        return ip.indexOf(':') >= 0 ? "http://[" + ip + "]/" : "http://" + ip + "/";
    }

    @Test
    public void test_ipv6PrivateRanges_rejectedWhenPrivateIpDisabled() {
        for (String ip : IPV6_PRIVATE) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> withPrivateIpDisabled.validate(url(ip), HTTP, ip, 80)
            );
            assertEquals("Remote inference host name has private ip address: " + ip, e.getMessage());
            // bracketed literal, as the SDK hands it over
            assertThrows(ip, IllegalArgumentException.class, () -> withPrivateIpDisabled.validate(url(ip), HTTP, "[" + ip + "]", 80));
        }
    }

    @Test
    public void test_ipv6PrivateRanges_allowedWhenPrivateIpEnabled() throws Exception {
        for (String ip : IPV6_PRIVATE) {
            withPrivateIpEnabled.validate(url(ip), HTTP, ip, 80);
        }
    }

    @Test
    public void test_linkLocalAndMetadata_rejectedRegardlessOfPrivateIpEnabled() {
        for (String ip : ALWAYS_BLOCKED) {
            for (ConnectorAddressValidator validator : new ConnectorAddressValidator[] { withPrivateIpDisabled, withPrivateIpEnabled }) {
                IllegalArgumentException e = assertThrows(
                    ip,
                    IllegalArgumentException.class,
                    () -> validator.validate(url(ip), HTTP, ip, 80)
                );
                assertEquals("Remote inference host name has restricted ip address: " + ip, e.getMessage());
            }
        }
    }

    @Test
    public void test_localUseNat64_atLeastPrivateForAnyPrefixLength() throws Exception {
        // 203.0.113.1 under the /48, /56, /64 and /96 local-use layouts: whatever the layout, the /48 itself is not public
        for (String ip : new String[] {
            "64:ff9b:1:cb00:71:100::",
            "64:ff9b:1:cb:0:7101::",
            "64:ff9b:1:0:cb:71:100:0",
            "64:ff9b:1::cb00:7101" }) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> withPrivateIpDisabled.validate(url(ip), HTTP, ip, 80)
            );
            assertEquals("Remote inference host name has private ip address: " + ip, e.getMessage());
            withPrivateIpEnabled.validate(url(ip), HTTP, ip, 80);
        }
        // the well-known /96 prefix carrying a public address stays public
        withPrivateIpDisabled.validate(url("64:ff9b::203.0.113.1"), HTTP, "64:ff9b::203.0.113.1", 80);
    }

    @Test
    public void test_localUseNat64_metadataTargetsBlockedUnderEveryLayout() {
        // RFC 6052 section 2.2 layouts inside 64:ff9b:1::/48, in the order /48, /56, /64, /96
        String[] linkLocalMetadata = {
            "64:ff9b:1:a9fe:a9:fe00::",
            "64:ff9b:1:a9:fe:a9fe::",
            "64:ff9b:1:0:a9:fea9:fe00:0",
            "64:ff9b:1::a9fe:a9fe" };
        String[] alibabaMetadata = {
            "64:ff9b:1:6464:64:c800::",
            "64:ff9b:1:64:64:64c8::",
            "64:ff9b:1:0:64:6464:c800:0",
            "64:ff9b:1::6464:64c8" };
        for (String[] layouts : new String[][] { linkLocalMetadata, alibabaMetadata }) {
            for (String ip : layouts) {
                for (ConnectorAddressValidator validator : new ConnectorAddressValidator[] {
                    withPrivateIpDisabled,
                    withPrivateIpEnabled }) {
                    IllegalArgumentException e = assertThrows(
                        ip,
                        IllegalArgumentException.class,
                        () -> validator.validate(url(ip), HTTP, ip, 80)
                    );
                    assertEquals("Remote inference host name has restricted ip address: " + ip, e.getMessage());
                }
            }
        }
    }

    @Test
    public void test_subnetBoundaries() throws Exception {
        String[] publicAddresses = {
            "9.255.255.255",
            "11.0.0.0",
            "126.255.255.255",
            "128.0.0.0",
            "169.253.255.255",
            "169.255.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "192.167.255.255",
            "192.169.0.0",
            "fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fe00::",
            "fe7f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "64:ff9b:0:ffff:ffff:ffff:ffff:ffff",
            "64:ff9b:2::",
            "64:ff9b:101::",
            "64:ff9b:101:a9fe::",
            "0.0.0.1",
            "::2",
            "100.100.100.199",
            "100.100.100.201",
            "ff00::",
            "ffc0::",
            "ffff:ffff::",
            "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            // a block only matches its own family: these share leading bytes with 169.254/16, 10/8 and fe80::/10
            "a9fe:a9fe::",
            "a00:1::",
            "254.128.0.1" };
        for (String ip : publicAddresses) {
            assertEquals(ip, ConnectorAddressValidator.AddressClass.PUBLIC, classify(ip));
        }
        String[] privateAddresses = {
            "10.0.0.0",
            "10.255.255.255",
            "127.0.0.0",
            "127.255.255.255",
            "172.16.0.0",
            "172.31.255.255",
            "192.168.0.0",
            "192.168.255.255",
            "fc00::",
            "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fd00:ec1:ffff:ffff:ffff:ffff:ffff:ffff",
            "fd00:ec3::",
            "fec0::",
            "feff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "64:ff9b:1::",
            "64:ff9b:1:ffff:ffff:ffff:ffff:ffff",
            "0.0.0.0",
            "::",
            "::1",
            "fd20:ce::253",
            "fd20:ce::255" };
        for (String ip : privateAddresses) {
            assertEquals(ip, ConnectorAddressValidator.AddressClass.PRIVATE, classify(ip));
        }
        String[] blockedAddresses = {
            "169.254.0.0",
            "169.254.255.255",
            "fe80::",
            "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fd00:ec2::",
            "fd00:ec2:ffff:ffff:ffff:ffff:ffff:ffff",
            "fd20:ce::254",
            "100.100.100.200" };
        for (String ip : blockedAddresses) {
            assertEquals(ip, ConnectorAddressValidator.AddressClass.BLOCKED, classify(ip));
        }
    }

    @Test
    public void test_deliveryAddresses_unwrapsOnlyTranslatedForms() throws Exception {
        byte[] mapped = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff };
        java.net.InetAddress target = java.net.InetAddress.getByName("10.0.5.7");
        // translated or mapped forms: the address itself, then the IPv4 address it carries
        for (java.net.InetAddress address : new java.net.InetAddress[] {
            mappedIpv6(mapped, new byte[] { 10, 0, 5, 7 }),
            java.net.InetAddress.getByName("::10.0.5.7"),
            java.net.InetAddress.getByName("64:ff9b::10.0.5.7") }) {
            assertEquals(address.toString(), Arrays.asList(address, target), ConnectorAddressValidator.deliveryAddresses(address));
        }
        // local-use NAT64: one IPv4 address per RFC 6052 layout, in the order /48, /56, /64, /96
        java.net.InetAddress localUse = java.net.InetAddress.getByName("64:ff9b:1:0:a:5:700:0");
        List<java.net.InetAddress> decoded = ConnectorAddressValidator.deliveryAddresses(localUse);
        assertEquals(5, decoded.size());
        assertEquals(localUse, decoded.get(0));
        assertEquals(target, decoded.get(3));
        // left alone: plain addresses, :: and ::1, near misses of each prefix, and tunnelling or SIIT formats
        String[] untouched = {
            "203.0.113.1",
            "2001:db8::1",
            "::",
            "::1",
            "::1:a9fe:a9fe",
            "::1:ffff:a9fe:a9fe",
            "64:ff9b::1:a9fe:a9fe",
            "164:ff9b::a9fe:a9fe",
            "64:ff9b:101:a9fe::",
            "::ffff:0:a9fe:a9fe",
            "2002:a9fe:a9fe::1",
            "2001:0:a9fe:a9fe::1" };
        for (String ip : untouched) {
            java.net.InetAddress address = java.net.InetAddress.getByName(ip);
            assertEquals(ip, List.of(address), ConnectorAddressValidator.deliveryAddresses(address));
        }
    }

    private static ConnectorAddressValidator.AddressClass classify(String ip) throws Exception {
        return ConnectorAddressValidator.classify(java.net.InetAddress.getAllByName(ip));
    }

    @Test
    public void test_restrictedPattern_matchesEmbeddedIpv4() throws Exception {
        List<Pattern> restricted = Collections.singletonList(Pattern.compile("10\\.0\\.5\\..*"));
        ConnectorAddressValidator client = new ConnectorAddressValidator(PRIVATE_IP_ENABLED, Collections.emptyList(), restricted);
        // well-known NAT64, IPv4-compatible and local-use NAT64 (/48, /56, /64, /96 layouts) forms of 10.0.5.7
        for (String ip : new String[] {
            "64:ff9b::10.0.5.7",
            "::10.0.5.7",
            "64:ff9b:1:a00:5:700::",
            "64:ff9b:1:a:0:507::",
            "64:ff9b:1:0:a:5:700:0",
            "64:ff9b:1::a00:507" }) {
            IllegalArgumentException e = assertThrows(ip, IllegalArgumentException.class, () -> client.validate(url(ip), HTTP, ip, 80));
            assertEquals("Remote inference host name has restricted ip address: " + ip, e.getMessage());
        }
        client.validate(url("64:ff9b::10.0.6.7"), HTTP, "64:ff9b::10.0.6.7", 80);
        // A resolved AAAA record for ::ffff:10.0.5.7 is an Inet6Address that prints as 0:0:0:0:0:ffff:a00:507
        byte[] mapped = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff };
        java.net.InetAddress restrictedMapped = mappedIpv6(mapped, new byte[] { 10, 0, 5, 7 });
        assertEquals("0:0:0:0:0:ffff:a00:507", restrictedMapped.getHostAddress());
        assertTrue(ConnectorAddressValidator.matchesRestrictedPattern(restrictedMapped, restricted));
        assertFalse(ConnectorAddressValidator.matchesRestrictedPattern(mappedIpv6(mapped, new byte[] { 10, 0, 6, 7 }), restricted));
        // patterns written for the printed IPv6 form keep matching it
        assertTrue(
            ConnectorAddressValidator
                .matchesRestrictedPattern(restrictedMapped, Collections.singletonList(Pattern.compile("0:0:0:0:0:ffff:.*")))
        );
    }

    @Test
    public void test_neighbouringAddressesOfMetadataEndpointsNotBlocked() throws Exception {
        for (String ip : new String[] { "fd20:ce::253", "fd20:ce:0:1::254" }) {
            withPrivateIpEnabled.validate(url(ip), HTTP, ip, 80);
        }
        for (String ip : new String[] { "100.100.100.199", "100.100.100.201" }) {
            withPrivateIpDisabled.validate(url(ip), HTTP, ip, 80);
        }
    }

    @Test
    public void test_unspecifiedAddress_gatedLikeLoopback() throws Exception {
        for (String ip : new String[] { "0.0.0.0", "::" }) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> withPrivateIpDisabled.validate(url(ip), HTTP, ip, 80)
            );
            assertEquals("Remote inference host name has private ip address: " + ip, e.getMessage());
            withPrivateIpEnabled.validate(url(ip), HTTP, ip, 80);
        }
    }

    @Test
    public void test_embeddedIpv4_classifiedByEmbeddedAddress() throws Exception {
        for (String ip : new String[] { "::10.0.0.1", "64:ff9b::10.0.0.1", "64:ff9b:1::10.0.0.1", "::ffff:127.0.0.1" }) {
            assertThrows(ip, IllegalArgumentException.class, () -> withPrivateIpDisabled.validate(url(ip), HTTP, ip, 80));
            withPrivateIpEnabled.validate(url(ip), HTTP, ip, 80);
        }
        for (String ip : new String[] { "64:ff9b::169.254.169.254", "::169.254.169.254" }) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> withPrivateIpEnabled.validate(url(ip), HTTP, ip, 80)
            );
            assertEquals("Remote inference host name has restricted ip address: " + ip, e.getMessage());
        }
        withPrivateIpDisabled.validate(url("64:ff9b::203.0.113.1"), HTTP, "64:ff9b::203.0.113.1", 80);
    }

    @Test
    public void test_ipv4MappedIpv6_classifiedByEmbeddedAddress() throws Exception {
        // ::ffff:a.b.c.d carried by an Inet6Address. InetAddress.getByName normalises the mapped form to an
        // Inet4Address, but an Inet6Address built directly keeps the 16 mapped bytes and must still be unwrapped.
        byte[] prefix = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff };
        java.net.InetAddress privateMapped = mappedIpv6(prefix, new byte[] { 10, 0, 0, 1 });
        java.net.InetAddress metadataMapped = mappedIpv6(prefix, new byte[] { (byte) 169, (byte) 254, (byte) 169, (byte) 254 });
        java.net.InetAddress publicMapped = mappedIpv6(prefix, new byte[] { (byte) 203, 0, (byte) 113, 1 });
        assertEquals(
            ConnectorAddressValidator.AddressClass.PRIVATE,
            ConnectorAddressValidator.classify(new java.net.InetAddress[] { privateMapped })
        );
        assertEquals(
            ConnectorAddressValidator.AddressClass.BLOCKED,
            ConnectorAddressValidator.classify(new java.net.InetAddress[] { metadataMapped })
        );
        assertEquals(
            ConnectorAddressValidator.AddressClass.PUBLIC,
            ConnectorAddressValidator.classify(new java.net.InetAddress[] { publicMapped })
        );
    }

    private static java.net.InetAddress mappedIpv6(byte[] prefix, byte[] ipv4) throws Exception {
        byte[] bytes = new byte[16];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        System.arraycopy(ipv4, 0, bytes, 12, 4);
        return java.net.Inet6Address.getByAddress(null, bytes, -1);
    }

    @Test
    public void test_publicAddresses_allowedWhenPrivateIpDisabled() throws Exception {
        for (String ip : new String[] { "2001:db8::1", "2606:4700::1111", "203.0.113.1", "64:ff9b:2::10.0.0.1" }) {
            withPrivateIpDisabled.validate(url(ip), HTTP, ip, 80);
        }
    }

    @Test
    public void test_classify_takesStrictestClassAcrossResolvedAddresses() throws Exception {
        java.net.InetAddress pub = java.net.InetAddress.getByName("203.0.113.1");
        java.net.InetAddress ula = java.net.InetAddress.getByName("fd00::1");
        java.net.InetAddress link = java.net.InetAddress.getByName("fe80::1");
        assertEquals(ConnectorAddressValidator.AddressClass.PUBLIC, ConnectorAddressValidator.classify(new java.net.InetAddress[] { pub }));
        assertEquals(
            ConnectorAddressValidator.AddressClass.PRIVATE,
            ConnectorAddressValidator.classify(new java.net.InetAddress[] { pub, ula })
        );
        assertEquals(
            ConnectorAddressValidator.AddressClass.BLOCKED,
            ConnectorAddressValidator.classify(new java.net.InetAddress[] { ula, link, pub })
        );
    }

    @Test
    public void test_ipv6UniqueLocal_subjectToPrivateEndpointAllowlist() throws Exception {
        trustingPrivate("^http://\\[fd00::1\\]/.*$").validate("http://[fd00::1]/predict", HTTP, "fd00::1", 80);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> trustingPrivate("^http://10\\.0\\.0\\.1/.*$").validate("http://[fd00::1]/predict", HTTP, "fd00::1", 80)
        );
        assertEquals("Connector URL is not matching the trusted connector private endpoint regex", e.getMessage());
    }

    @Test
    public void test_restrictedPattern_stillCheckedFirstForIpv6() {
        ConnectorAddressValidator client = new ConnectorAddressValidator(
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            Collections.singletonList(Pattern.compile("fd00:1:.*"))
        );
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.validate("http://[fd00:1::5]/", HTTP, "fd00:1::5", 80)
        );
        assertEquals("Remote inference host name has restricted ip address: fd00:1::5", e.getMessage());
        // before the private-IP gate
        ConnectorAddressValidator refusingPrivate = new ConnectorAddressValidator(
            PRIVATE_IP_DISABLED,
            Collections.emptyList(),
            Collections.singletonList(Pattern.compile("fd00:1:.*"))
        );
        e = assertThrows(IllegalArgumentException.class, () -> refusingPrivate.validate("http://[fd00:1::5]/", HTTP, "fd00:1::5", 80));
        assertEquals("Remote inference host name has restricted ip address: fd00:1::5", e.getMessage());
        // and before the private-endpoint allowlist
        ConnectorAddressValidator allowlisting = new ConnectorAddressValidator(
            PRIVATE_IP_ENABLED,
            Collections.singletonList(Pattern.compile("^http://10\\.0\\.0\\.1/.*$")),
            Collections.singletonList(Pattern.compile("fd00:1:.*"))
        );
        e = assertThrows(IllegalArgumentException.class, () -> allowlisting.validate("http://[fd00:1::5]/", HTTP, "fd00:1::5", 80));
        assertEquals("Remote inference host name has restricted ip address: fd00:1::5", e.getMessage());
    }

    @Test
    public void test_validateAddresses_checksEveryResolvedAddress() throws Exception {
        // a host name can resolve to several addresses; the strictest one decides, and every one meets the patterns
        java.net.InetAddress publicAddress = java.net.InetAddress.getByName("203.0.113.1");
        byte[] mapped = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff };
        java.net.InetAddress mappedMetadata = mappedIpv6(mapped, new byte[] { (byte) 169, (byte) 254, (byte) 169, (byte) 254 });
        java.net.InetAddress mappedRestricted = mappedIpv6(mapped, new byte[] { 10, 0, 5, 7 });
        java.net.InetAddress[][] refusedWithFlag = {
            { publicAddress, java.net.InetAddress.getByName("169.254.169.254") },
            { publicAddress, mappedMetadata } };
        for (java.net.InetAddress[] addresses : refusedWithFlag) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> withPrivateIpEnabled.validateAddresses("http://multi.example/", "multi.example", addresses)
            );
            assertEquals("Remote inference host name has restricted ip address: multi.example", e.getMessage());
        }
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled
                .validateAddresses(
                    "http://multi.example/",
                    "multi.example",
                    new java.net.InetAddress[] { publicAddress, java.net.InetAddress.getByName("10.0.0.1") }
                )
        );
        assertEquals("Remote inference host name has private ip address: multi.example", e.getMessage());
        ConnectorAddressValidator restricting = new ConnectorAddressValidator(
            PRIVATE_IP_ENABLED,
            Collections.emptyList(),
            Collections.singletonList(Pattern.compile("10\\.0\\.5\\..*"))
        );
        // the reported case end to end: an AAAA answer of ::ffff:10.0.5.7 next to a public address
        for (java.net.InetAddress restricted : new java.net.InetAddress[] {
            java.net.InetAddress.getByName("10.0.5.7"),
            mappedRestricted }) {
            e = assertThrows(
                IllegalArgumentException.class,
                () -> restricting
                    .validateAddresses("http://multi.example/", "multi.example", new java.net.InetAddress[] { publicAddress, restricted })
            );
            assertEquals("Remote inference host name has restricted ip address: multi.example", e.getMessage());
        }
        restricting
            .validateAddresses(
                "http://multi.example/",
                "multi.example",
                new java.net.InetAddress[] { publicAddress, java.net.InetAddress.getByName("10.0.6.7") }
            );
    }

    private static ConnectorAddressValidator trustingPrivate(String... patterns) {
        return new ConnectorAddressValidator(
            PRIVATE_IP_ENABLED,
            Arrays.stream(patterns).map(Pattern::compile).collect(java.util.stream.Collectors.toList()),
            Collections.emptyList()
        );
    }

    @Test
    public void test_trustedPrivateEndpoint_matchesCanonicalForm() throws Exception {
        ConnectorAddressValidator client = trustingPrivate("^http://10\\.0\\.0\\.1/v1/.*$");
        client.validate("http://10.0.0.1/v1/chat", HTTP, "10.0.0.1", 80);
        client.validate("HTTP://10.0.0.1/v1/chat", HTTP, "10.0.0.1", 80);
    }

    @Test
    public void test_trustedPrivateEndpoint_rejectsUserInfoFragmentAndUnparseable() {
        ConnectorAddressValidator client = trustingPrivate("^https?://.*$");
        String message = "Connector URL is not matching the trusted connector private endpoint regex";
        for (String endpoint : new String[] { "http://user@10.0.0.1/v1", "http://10.0.0.1/v1#frag", "http://10.0.0.1/a b" }) {
            IllegalArgumentException e = assertThrows(
                endpoint,
                IllegalArgumentException.class,
                () -> client.validate(endpoint, HTTP, "10.0.0.1", 80)
            );
            assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void test_trustedPrivateEndpoint_publicHostNotParsed() throws Exception {
        // Public hosts skip the private-endpoint list entirely, even when the endpoint string would not parse.
        ConnectorAddressValidator client = new ConnectorAddressValidator(
            PRIVATE_IP_DISABLED,
            Arrays.asList(Pattern.compile("^http://10\\..*$")),
            Collections.emptyList()
        );
        client.validate("http://177.16.0.1/a b", HTTP, "177.16.0.1", 80);
    }

    @Test
    public void test_trustedPrivateEndpoint_hostWildcardPatternStillAdmitsPathTrick() throws Exception {
        // A '.*' in the host position spans into the path even on the canonical form; the settings validator
        // warns about such patterns (see MLCommonsSettingsTests). Documented here so the behaviour is explicit.
        ConnectorAddressValidator client = trustingPrivate("^https://.*\\.internal\\.example/.*$");
        client.validate("https://127.0.0.1/x.internal.example/y", HTTPS, "127.0.0.1", 443);
    }

    // IPv6 classification cases contributed by Tony Piazza (@tonypiazza).
    @Test
    public void validate_rejectsIpv4LoopbackPrivateAndMetadata() {
        for (String ip : new String[] { "127.0.0.1", "10.0.0.1" }) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> withPrivateIpDisabled.validate("http://" + ip + "/", "http", ip, 80)
            );
            assertEquals("Remote inference host name has private ip address: " + ip, e.getMessage());
        }
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> withPrivateIpDisabled.validate("http://169.254.169.254/", "http", "169.254.169.254", 80)
        );
        assertEquals("Remote inference host name has restricted ip address: 169.254.169.254", e.getMessage());
    }

    @Test
    public void validate_rejectsIpv6LoopbackAndDeprecatedSiteLocal() {
        for (String ip : new String[] { "::1", "fec0::1" }) {
            assertThrows(ip, IllegalArgumentException.class, () -> withPrivateIpDisabled.validate("http://[" + ip + "]/", "http", ip, 80));
        }
    }

    @Test
    public void validate_rejectsIpv6UniqueLocalLinkLocalAndMetadata() {
        for (String ip : new String[] { "fd00::1", "fc00::1", "fe80::1", "fd00:ec2::254" }) {
            assertThrows(ip, IllegalArgumentException.class, () -> withPrivateIpDisabled.validate("http://[" + ip + "]/", "http", ip, 80));
            assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> withPrivateIpDisabled.validate("http://[" + ip + "]/", "http", "[" + ip + "]", 80)
            );
        }
    }
}
