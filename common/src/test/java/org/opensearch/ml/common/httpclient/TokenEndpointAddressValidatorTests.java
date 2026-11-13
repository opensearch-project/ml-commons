/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.opensearch.common.settings.Settings;

public class TokenEndpointAddressValidatorTests {

    private static final ConnectorAddressValidator PRIVATE_ALLOWED = new ConnectorAddressValidator(true, null, null);
    private static final ConnectorAddressValidator ANY_PRIVATE_ENDPOINT_ALLOWED = new ConnectorAddressValidator(
        true,
        List.of(Pattern.compile(".*")),
        null
    );

    /** A validator over {@code base} that refuses port 9200 and sees {@code own} as this node's interface addresses. */
    private static TokenEndpointAddressValidator validator(ConnectorAddressValidator base, InetAddress... own) {
        return new TokenEndpointAddressValidator(() -> base, Set.of(9200), () -> Set.of(own));
    }

    private static String url(String host, int port) {
        return "https://" + (host.contains(":") ? "[" + host + "]" : host) + (port == 443 ? "" : ":" + port) + "/token";
    }

    private static String refused(TokenEndpointAddressValidator validator, String host, int port) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> validator.validate(url(host, port), host, port));
        assertNull(e.getCause());
        return e.getMessage();
    }

    private static String refused(TokenEndpointAddressValidator validator, InetAddress address) {
        return assertThrows(IllegalArgumentException.class, () -> validator.checkAddresses(new InetAddress[] { address })).getMessage();
    }

    /** An Inet6Address that keeps its bytes as given, as a resolved AAAA record does. */
    private static InetAddress inet6(byte[] bytes) throws UnknownHostException {
        return Inet6Address.getByAddress(null, bytes, null);
    }

    private static InetAddress mapped(int a, int b, int c, int d) throws UnknownHostException {
        return inet6(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) a, (byte) b, (byte) c, (byte) d });
    }

    private static Set<Integer> ports(int from, int to) {
        return IntStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
    }

    @Test
    public void validate_returnsTheAllowedAddresses() throws Exception {
        InetAddress[] addresses = validator(ConnectorAddressValidator.DEFAULT).validate(url("203.0.113.10", 443), "203.0.113.10", 443);

        assertArrayEquals(new InetAddress[] { InetAddress.getByName("203.0.113.10") }, addresses);
    }

    @Test
    public void validate_refusesLoopbackUnspecifiedAndUniqueLocalWhateverTheSettingsAllow() {
        assertEquals(
            "the token endpoint host resolves to a loopback, unspecified, IPv6 unique-local or own-node address",
            refused(validator(PRIVATE_ALLOWED), "127.0.0.1", 443)
        );
        for (ConnectorAddressValidator base : List.of(PRIVATE_ALLOWED, ANY_PRIVATE_ENDPOINT_ALLOWED)) {
            for (String host : List.of("127.0.0.1", "127.9.9.9", "::1", "0.0.0.0", "::", "fc00::1", "fd00::1", "fdff:ffff::1")) {
                assertEquals(host, TokenEndpointAddressValidator.REFUSED_ADDRESS_MESSAGE, refused(validator(base), host, 443));
            }
        }
    }

    @Test
    public void checkAddresses_refusesTheLoopbackAddressAnIpv6AddressDeliversTo() throws Exception {
        TokenEndpointAddressValidator validator = validator(PRIVATE_ALLOWED);
        InetAddress compatible = inet6(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 127, 0, 0, 1 });

        for (InetAddress address : List
            .of(
                mapped(127, 0, 0, 1),
                compatible,
                InetAddress.getByName("64:ff9b::7f00:1"),
                // local-use NAT64, /96 layout
                InetAddress.getByName("64:ff9b:1::7f00:1")
            )) {
            assertTrue(address.toString(), address instanceof Inet6Address);
            assertEquals(address.toString(), TokenEndpointAddressValidator.REFUSED_ADDRESS_MESSAGE, refused(validator, address));
        }
    }

    @Test
    public void validate_refusesAnAddressOfThisNode() throws Exception {
        InetAddress own = InetAddress.getByName("198.51.100.7");

        assertEquals(TokenEndpointAddressValidator.REFUSED_ADDRESS_MESSAGE, refused(validator(PRIVATE_ALLOWED, own), "198.51.100.7", 443));
        assertEquals(
            TokenEndpointAddressValidator.REFUSED_ADDRESS_MESSAGE,
            refused(validator(PRIVATE_ALLOWED, own), mapped(198, 51, 100, 7))
        );
        assertEquals(1, validator(PRIVATE_ALLOWED).validate(url("198.51.100.7", 443), "198.51.100.7", 443).length);
    }

    @Test
    public void validate_failsClosedWhenTheInterfaceAddressesCannotBeRead() {
        TokenEndpointAddressValidator validator = new TokenEndpointAddressValidator(() -> PRIVATE_ALLOWED, Set.of(), () -> {
            throw new SocketException("no interfaces");
        });

        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> validator.validate(url("203.0.113.10", 443), "203.0.113.10", 443)
        );

        assertEquals("The network interface addresses of this node could not be read", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void validate_refusesTheClusterPortsBeforeResolving() {
        TokenEndpointAddressValidator validator = validator(PRIVATE_ALLOWED);

        assertEquals(
            "the token endpoint port is in this node's http.port or transport.port range",
            refused(validator, "unresolvable.invalid", 9200)
        );
        assertThrows(UnknownHostException.class, () -> validator.validate(url("unresolvable.invalid", 443), "unresolvable.invalid", 443));
    }

    @Test
    public void clusterPorts_areTheHttpAndTransportPortRanges() {
        assertEquals(ports(9200, 9400), TokenEndpointAddressValidator.clusterPorts(Settings.EMPTY));

        Set<Integer> https = TokenEndpointAddressValidator.clusterPorts(Settings.builder().put("http.port", 443).build());
        assertTrue(https.contains(443));
        assertFalse(https.contains(9200));
        assertTrue(https.containsAll(ports(9300, 9400)));

        Set<Integer> transport = TokenEndpointAddressValidator
            .clusterPorts(Settings.builder().put("transport.port", "9300-9301,9500").build());
        Set<Integer> expected = ports(9200, 9301);
        expected.add(9500);
        assertEquals(expected, transport);

        Set<Integer> fallback = TokenEndpointAddressValidator.clusterPorts(Settings.builder().put("transport.tcp.port", "9600").build());
        expected = ports(9200, 9300);
        expected.add(9600);
        assertEquals(expected, fallback);
    }

    @Test
    public void validate_replacesTheRefusalOfTheConnectorAddressChecks() {
        assertEquals(
            "the token endpoint host resolves to an address the connector address checks do not allow",
            refused(validator(ConnectorAddressValidator.DEFAULT), "169.254.169.254", 443)
        );
        for (String host : List.of("169.254.169.254", "fe80::1", "10.0.0.1")) {
            String message = refused(validator(ConnectorAddressValidator.DEFAULT), host, 443);
            assertEquals(host, TokenEndpointAddressValidator.ADDRESS_NOT_ALLOWED_MESSAGE, message);
            assertFalse(message, message.contains("Remote inference"));
        }
    }
}
