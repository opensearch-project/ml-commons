/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.opensearch.common.network.InetAddresses;
import org.opensearch.ml.common.connector.Connector;

import lombok.extern.log4j.Log4j2;

/**
 * Decides which addresses a connector request may connect to, from the connector address settings:
 * {@code plugins.ml_commons.connector.private_ip_enabled}, {@code plugins.ml_commons.trusted_connector_private_endpoints_regex}
 * and {@code plugins.ml_commons.connector.restricted_ip_patterns}. The pattern lists are the setting-backed lists and
 * may change under this object when the settings change.
 */
@Log4j2
public final class ConnectorAddressValidator {
    /** The settings' defaults: private addresses refused, no private endpoint patterns, no restricted patterns. */
    public static final ConnectorAddressValidator DEFAULT = new ConnectorAddressValidator(false, null, null);

    private final boolean connectorPrivateIpEnabled;
    private final List<Pattern> connectorTrustedPrivateEndpoints;
    private final List<Pattern> connectorRestrictedIpPatterns;

    public ConnectorAddressValidator(
        boolean connectorPrivateIpEnabled,
        List<Pattern> connectorTrustedPrivateEndpoints,
        List<Pattern> connectorRestrictedIpPatterns
    ) {
        this.connectorPrivateIpEnabled = connectorPrivateIpEnabled;
        this.connectorTrustedPrivateEndpoints = connectorTrustedPrivateEndpoints;
        this.connectorRestrictedIpPatterns = connectorRestrictedIpPatterns;
    }

    /**
     * Validate the input parameters, such as protocol, host and port.
     * @param endpoint The request URL, matched against the trusted private endpoint patterns when the host is private.
     * @param protocol The protocol supported in remote inference, currently only http and https are supported.
     * @param host The host name of the remote inference server, host must be a valid ip address or domain name.
     * @param port The port number of the remote inference server, port number must be in range [0, 65536].
     * @return the addresses {@code host} resolved to, every one of them allowed by the settings
     * @throws UnknownHostException if {@code host} does not resolve
     */
    public InetAddress[] validate(String endpoint, String protocol, String host, int port) throws UnknownHostException {
        if (protocol != null && !"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            log.error("Remote inference protocol is not http or https: {}", protocol);
            throw new IllegalArgumentException("Protocol is not http or https: " + protocol);
        }
        // When port is not specified, the default port is -1, and we need to set it to 80 or 443 based on protocol.
        if (port == -1) {
            if (protocol == null || "http".equals(protocol.toLowerCase(Locale.getDefault()))) {
                port = 80;
            } else {
                port = 443;
            }
        }
        if (port < 0 || port > 65536) {
            log.error("Remote inference port out of range: {}", port);
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        validateAddresses(endpoint, host, addresses);
        return addresses;
    }

    /** Checks every address {@code hostName} resolved to; any one of them can be the address the request connects to. */
    void validateAddresses(String endpoint, String hostName, InetAddress[] addresses) {
        AddressClass addressClass = classify(addresses);

        if (connectorRestrictedIpPatterns != null && !connectorRestrictedIpPatterns.isEmpty()) {
            boolean hasRestrictedAddress = Arrays
                .stream(addresses)
                .anyMatch(addr -> matchesRestrictedPattern(addr, connectorRestrictedIpPatterns));
            if (hasRestrictedAddress) {
                log.error("Remote inference host name has restricted ip address: {}", hostName);
                throw new IllegalArgumentException("Remote inference host name has restricted ip address: " + hostName);
            }
        }

        if (addressClass == AddressClass.BLOCKED) {
            log.error("Remote inference host name has restricted ip address: {}", hostName);
            throw new IllegalArgumentException("Remote inference host name has restricted ip address: " + hostName);
        }

        boolean hasPrivateIpAddress = addressClass == AddressClass.PRIVATE;
        if (!connectorPrivateIpEnabled && hasPrivateIpAddress) {
            log.error("Remote inference host name has private ip address: {}", hostName);
            throw new IllegalArgumentException("Remote inference host name has private ip address: " + hostName);
        }

        if (hasPrivateIpAddress && connectorTrustedPrivateEndpoints != null && !connectorTrustedPrivateEndpoints.isEmpty()) {
            String canonical = canonicalPrivateEndpoint(endpoint);
            boolean hasMatchedUrl = canonical != null
                && connectorTrustedPrivateEndpoints.stream().anyMatch(pattern -> pattern.matcher(canonical).matches());
            if (!hasMatchedUrl) {
                throw new IllegalArgumentException("Connector URL is not matching the trusted connector private endpoint regex");
            }
        }
    }

    /** The canonical form of {@code endpoint}, or null when it cannot be parsed into scheme and host alone. */
    private static String canonicalPrivateEndpoint(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return null;
        }
        if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            return null;
        }
        return Connector.canonicalUrl(uri);
    }

    /**
     * Whether a restricted pattern matches the address or an IPv4 address it carries, each in the form
     * {@link InetAddress#getHostAddress()} prints. A resolved AAAA record such as ::ffff:10.0.5.7 prints as
     * 0:0:0:0:0:ffff:a00:507, so matching the printed IPv6 form alone would let it past a pattern written for 10.0.5.7.
     */
    static boolean matchesRestrictedPattern(InetAddress address, List<Pattern> patterns) {
        return deliveryAddresses(address)
            .stream()
            .map(InetAddress::getHostAddress)
            .anyMatch(text -> patterns.stream().anyMatch(pattern -> pattern.matcher(text).matches()));
    }

    /**
     * How a resolved address is treated. {@code BLOCKED} addresses are refused regardless of
     * {@code connector.private_ip_enabled}; {@code PRIVATE} addresses need that setting and, when one is configured,
     * a match in {@code trusted_connector_private_endpoints_regex}. The ranges are {@link #BLOCKED_SUBNETS} and
     * {@link #PRIVATE_SUBNETS}.
     */
    enum AddressClass {
        PUBLIC,
        PRIVATE,
        BLOCKED
    }

    /**
     * Link-local ranges, the EC2 instance-service prefix that carries the IPv6 metadata endpoint, and the Compute
     * Engine IPv6 and Alibaba Cloud metadata addresses. Checked before {@link #PRIVATE_SUBNETS}, which contain some of
     * them.
     */
    private static final List<Subnet> BLOCKED_SUBNETS = List
        .of(
            new Subnet("169.254.0.0/16"), // IPv4 link-local, including the instance metadata service
            new Subnet("100.100.100.200/32"), // Alibaba Cloud instance metadata
            new Subnet("fe80::/10"), // IPv6 link-local
            new Subnet("fd00:ec2::/32"), // EC2 instance-service prefix, metadata at fd00:ec2::254
            new Subnet("fd20:ce::254/128") // Compute Engine metadata on IPv6-only VMs
        );

    /**
     * RFC 1918, loopback, the unspecified addresses (which reach loopback on connect), unique-local, deprecated site-local
     * and local-use NAT64.
     */
    private static final List<Subnet> PRIVATE_SUBNETS = List
        .of(
            new Subnet("0.0.0.0/32"),
            new Subnet("127.0.0.0/8"),
            new Subnet("10.0.0.0/8"),
            new Subnet("172.16.0.0/12"),
            new Subnet("192.168.0.0/16"),
            new Subnet("::/128"),
            new Subnet("::1/128"),
            new Subnet("fc00::/7"), // unique-local
            new Subnet("fec0::/10"), // deprecated site-local
            new Subnet("64:ff9b:1::/48") // local-use NAT64 (RFC 8215)
        );

    /**
     * A CIDR block. An address only matches blocks of its own family: an IPv4 block never matches an IPv6 address, even
     * one that carries an IPv4 address, which is why {@link #deliveryAddresses} unwraps those first.
     */
    private static final class Subnet {
        private final byte[] network;
        private final int prefixLength;

        Subnet(String cidr) {
            int slash = cidr.indexOf('/');
            this.network = InetAddresses.forString(cidr.substring(0, slash)).getAddress();
            this.prefixLength = Integer.parseInt(cidr.substring(slash + 1));
            if (prefixLength < 0 || prefixLength > network.length * 8) {
                throw new IllegalArgumentException("invalid prefix length in " + cidr);
            }
        }

        boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xff << (8 - remainingBits)) & 0xff;
            return ((bytes[fullBytes] ^ network[fullBytes]) & mask) == 0;
        }
    }

    /** The strictest class among the resolved addresses. */
    static AddressClass classify(InetAddress[] addresses) {
        AddressClass strictest = AddressClass.PUBLIC;
        for (InetAddress address : addresses) {
            strictest = stricter(strictest, classify(address));
        }
        return strictest;
    }

    /** The strictest class among the address and the IPv4 addresses it carries (see {@link #deliveryAddresses}). */
    static AddressClass classify(InetAddress address) {
        AddressClass strictest = AddressClass.PUBLIC;
        for (InetAddress candidate : deliveryAddresses(address)) {
            if (BLOCKED_SUBNETS.stream().anyMatch(subnet -> subnet.contains(candidate))) {
                strictest = stricter(strictest, AddressClass.BLOCKED);
            } else if (PRIVATE_SUBNETS.stream().anyMatch(subnet -> subnet.contains(candidate))) {
                strictest = stricter(strictest, AddressClass.PRIVATE);
            }
        }
        return strictest;
    }

    private static AddressClass stricter(AddressClass a, AddressClass b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /**
     * The address, followed by every IPv4 address a connection to it may be delivered to: the IPv4 address in an
     * IPv4-mapped (::ffff:a.b.c.d) or IPv4-compatible (::a.b.c.d) address or in the well-known NAT64 prefix
     * (64:ff9b::/96), and in the local-use NAT64 prefix (64:ff9b:1::/48) the IPv4 address under each prefix length
     * RFC 6052 allows inside it (/48, /56, /64 and /96), since the operator's choice is not visible here. The IPv4 blocks
     * in the subnet tables only match an {@link Inet4Address}, so these are unwrapped before matching. Tunnelling formats such
     * as 6to4 and Teredo are not unwrapped: they carry the IPv6 packet to a tunnel endpoint instead of translating the
     * connection to the IPv4 address.
     */
    static List<InetAddress> deliveryAddresses(InetAddress address) {
        List<InetAddress> addresses = new ArrayList<>(5);
        addresses.add(address);
        byte[] bytes = address.getAddress();
        if (bytes.length != 16) {
            return addresses;
        }
        if (allZero(bytes, 0, 10) && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
            // ::ffff:a.b.c.d. getByName turns a literal into an Inet4Address, but a resolved AAAA record (or
            // Inet6Address.getByAddress) keeps the mapped bytes in an Inet6Address.
            addresses.add(ipv4(bytes, 12, 13, 14, 15));
        } else if (allZero(bytes, 0, 12)) {
            // ::a.b.c.d; :: and ::1 are not IPv4-compatible addresses
            if (!allZero(bytes, 12, 15) || (bytes[15] != 0 && bytes[15] != 1)) {
                addresses.add(ipv4(bytes, 12, 13, 14, 15));
            }
        } else if (isNat64Prefix(bytes) && allZero(bytes, 4, 12)) {
            addresses.add(ipv4(bytes, 12, 13, 14, 15)); // 64:ff9b::/96
        } else if (isNat64Prefix(bytes) && bytes[4] == 0x00 && bytes[5] == 0x01) {
            // 64:ff9b:1::/48. RFC 6052 section 2.2 skips bits 64-71 (byte 8) in every layout but /96.
            addresses.add(ipv4(bytes, 6, 7, 9, 10)); // /48
            addresses.add(ipv4(bytes, 7, 9, 10, 11)); // /56
            addresses.add(ipv4(bytes, 9, 10, 11, 12)); // /64
            addresses.add(ipv4(bytes, 12, 13, 14, 15)); // /96
        }
        return addresses;
    }

    private static boolean isNat64Prefix(byte[] bytes) {
        return bytes[0] == 0x00 && (bytes[1] & 0xff) == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b;
    }

    /** Whether {@code bytes[from]} up to, but not including, {@code bytes[to]} are all zero. */
    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress ipv4(byte[] bytes, int first, int second, int third, int fourth) {
        try {
            return InetAddress.getByAddress(new byte[] { bytes[first], bytes[second], bytes[third], bytes[fourth] });
        } catch (UnknownHostException e) {
            throw new IllegalStateException("four bytes are always an IPv4 address", e);
        }
    }
}
