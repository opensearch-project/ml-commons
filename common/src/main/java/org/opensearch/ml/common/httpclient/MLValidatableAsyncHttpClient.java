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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.opensearch.ml.common.connector.Connector;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Log4j2
public class MLValidatableAsyncHttpClient implements SdkAsyncHttpClient {
    private final SdkAsyncHttpClient delegate;
    private final boolean connectorPrivateIpEnabled;
    private final List<Pattern> connectorTrustedPrivateEndpoints;
    private final List<Pattern> connectorRestrictedIpPatterns;

    protected MLValidatableAsyncHttpClient(
        SdkAsyncHttpClient client,
        boolean connectorPrivateIpEnabled,
        List<Pattern> connectorTrustedPrivateEndpoints,
        List<Pattern> connectorRestrictedIpPatterns
    ) {
        this.delegate = client;
        this.connectorPrivateIpEnabled = connectorPrivateIpEnabled;
        this.connectorTrustedPrivateEndpoints = connectorTrustedPrivateEndpoints;
        this.connectorRestrictedIpPatterns = connectorRestrictedIpPatterns;
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
        String protocol = request.request().protocol();
        String host = request.request().host();
        int port = request.request().port();
        String endpoint = request.request().getUri().toString();
        try {
            validate(endpoint, protocol, host, port, connectorPrivateIpEnabled);
            return delegate.execute(request);
        } catch (Exception e) {
            log.error("Failed to validate request!", e);
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * Validate the input parameters, such as protocol, host and port.
     * @param protocol The protocol supported in remote inference, currently only http and https are supported.
     * @param host The host name of the remote inference server, host must be a valid ip address or domain name and must not be localhost.
     * @param port The port number of the remote inference server, port number must be in range [0, 65536].
     * @param connectorPrivateIpEnabled The port number of the remote inference server, port number must be in range [0, 65536].
     * @throws UnknownHostException Allow to use private IP or not.
     */
    public void validate(String endpoint, String protocol, String host, int port, boolean connectorPrivateIpEnabled)
        throws UnknownHostException {
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
        validateIp(endpoint, host, connectorPrivateIpEnabled);
    }

    private void validateIp(String endpoint, String hostName, boolean connectorPrivateIpEnabled) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(hostName);
        AddressClass addressClass = classify(addresses);

        if (connectorRestrictedIpPatterns != null && !connectorRestrictedIpPatterns.isEmpty()) {
            boolean hasRestrictedAddress = Arrays
                .stream(addresses)
                .anyMatch(
                    addr -> connectorRestrictedIpPatterns.stream().anyMatch(pattern -> pattern.matcher(addr.getHostAddress()).matches())
                );
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
     * How a resolved address is treated. {@code BLOCKED} addresses are refused regardless of
     * {@code connector.private_ip_enabled}: link-local ranges, the unspecified address and the EC2 instance-service
     * prefix that carries the IPv6 metadata endpoint. {@code PRIVATE} addresses need that setting and, when one is
     * configured, a match in {@code trusted_connector_private_endpoints_regex}.
     */
    enum AddressClass {
        PUBLIC,
        PRIVATE,
        BLOCKED
    }

    /** The strictest class among the resolved addresses. */
    static AddressClass classify(InetAddress[] addresses) {
        AddressClass strictest = AddressClass.PUBLIC;
        for (InetAddress address : addresses) {
            AddressClass current = classify(address);
            if (current.ordinal() > strictest.ordinal()) {
                strictest = current;
            }
        }
        return strictest;
    }

    static AddressClass classify(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return classifyIpv4(bytes);
        }
        if (bytes.length != 16) {
            return AddressClass.BLOCKED;
        }
        AddressClass ipv6Class = classifyIpv6(bytes);
        // ::a.b.c.d and the NAT64 prefixes carry an IPv4 address that the network may deliver to; the stricter of
        // the two classifications wins. ::ffff:a.b.c.d never gets here: the JDK returns it as an Inet4Address.
        byte[] embedded = embeddedIpv4(bytes);
        if (embedded != null) {
            AddressClass ipv4Class = classifyIpv4(embedded);
            return ipv4Class.ordinal() > ipv6Class.ordinal() ? ipv4Class : ipv6Class;
        }
        return ipv6Class;
    }

    private static AddressClass classifyIpv4(byte[] bytes) {
        if (bytes.length != 4) {
            return AddressClass.BLOCKED;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if (first == 0 && second == 0 && bytes[2] == 0 && bytes[3] == 0) {
            return AddressClass.BLOCKED; // 0.0.0.0
        }
        if (first == 169 && second == 254) {
            return AddressClass.BLOCKED; // link-local, including the instance metadata service
        }
        if (first == 127 || first == 10 || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168)) {
            return AddressClass.PRIVATE;
        }
        return AddressClass.PUBLIC;
    }

    private static AddressClass classifyIpv6(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        boolean leadingZero = true;
        for (int i = 0; i < 15; i++) {
            if (bytes[i] != 0) {
                leadingZero = false;
                break;
            }
        }
        if (leadingZero && bytes[15] == 0) {
            return AddressClass.BLOCKED; // ::
        }
        if (leadingZero && bytes[15] == 1) {
            return AddressClass.PRIVATE; // ::1
        }
        if (first == 0xfe && (second & 0xc0) == 0x80) {
            return AddressClass.BLOCKED; // fe80::/10 link-local
        }
        if (first == 0xfe && (second & 0xc0) == 0xc0) {
            return AddressClass.PRIVATE; // fec0::/10, deprecated site-local
        }
        if ((first & 0xfe) == 0xfc) {
            // fc00::/7 unique-local. fd00:ec2::/32 is the EC2 instance-service prefix (metadata at fd00:ec2::254).
            if (first == 0xfd && second == 0x00 && (bytes[2] & 0xff) == 0x0e && (bytes[3] & 0xff) == 0xc2) {
                return AddressClass.BLOCKED;
            }
            return AddressClass.PRIVATE;
        }
        return AddressClass.PUBLIC;
    }

    /** The IPv4 address embedded in an IPv4-compatible (::a.b.c.d) or NAT64 (64:ff9b::/96, 64:ff9b:1::/48) address, or null. */
    private static byte[] embeddedIpv4(byte[] bytes) {
        boolean compatible = true;
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) {
                compatible = false;
                break;
            }
        }
        if (compatible) {
            boolean unspecifiedOrLoopback = bytes[12] == 0 && bytes[13] == 0 && bytes[14] == 0 && (bytes[15] == 0 || bytes[15] == 1);
            return unspecifiedOrLoopback ? null : Arrays.copyOfRange(bytes, 12, 16);
        }
        boolean nat64Prefix = bytes[0] == 0x00 && (bytes[1] & 0xff) == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b;
        if (nat64Prefix) {
            boolean wellKnown = true;
            for (int i = 4; i < 12; i++) {
                if (bytes[i] != 0) {
                    wellKnown = false;
                    break;
                }
            }
            boolean localUse = bytes[4] == 0x00 && bytes[5] == 0x01;
            if (wellKnown || localUse) {
                return Arrays.copyOfRange(bytes, 12, 16);
            }
        }
        return null;
    }
}
