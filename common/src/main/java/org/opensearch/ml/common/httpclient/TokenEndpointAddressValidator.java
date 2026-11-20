/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.opensearch.common.CheckedSupplier;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.PortsRange;
import org.opensearch.http.HttpTransportSettings;
import org.opensearch.transport.TransportSettings;

/**
 * The addresses a token request may connect to: those the connector address settings allow, minus loopback, the
 * unspecified addresses, IPv6 unique-local addresses and this node's own interface addresses, on a port outside this
 * node's {@code http.port} and {@code transport.port} ranges. No setting relaxes these refusals.
 *
 * <p>Not covered: {@code transport.profiles.*.port}, {@code http.publish_port}, {@code transport.publish_port} and
 * auxiliary transport ports. An {@code http.port} range that contains 443 refuses every token endpoint on the default
 * https port.
 */
public final class TokenEndpointAddressValidator {
    public static final String REFUSED_PORT_MESSAGE = "the token endpoint port is in this node's http.port or transport.port range";
    public static final String REFUSED_ADDRESS_MESSAGE =
        "the token endpoint host resolves to a loopback, unspecified, IPv6 unique-local or own-node address";
    public static final String ADDRESS_NOT_ALLOWED_MESSAGE =
        "the token endpoint host resolves to an address the connector address checks do not allow";
    public static final String INTERFACE_ADDRESSES_UNAVAILABLE_MESSAGE = "The network interface addresses of this node could not be read";

    private final Supplier<ConnectorAddressValidator> base;
    private final Set<Integer> refusedPorts;
    private final CheckedSupplier<Set<InetAddress>, SocketException> interfaceAddresses;

    /**
     * @param base the connector address checks under the current settings, asked once per token request
     * @param refusedPorts the ports no token request may connect to, usually {@link #clusterPorts}
     */
    public TokenEndpointAddressValidator(Supplier<ConnectorAddressValidator> base, Set<Integer> refusedPorts) {
        // Every address of every interface, down interfaces included, read on every call.
        this(
            base,
            refusedPorts,
            () -> NetworkInterface.networkInterfaces().flatMap(NetworkInterface::inetAddresses).collect(Collectors.toSet())
        );
    }

    TokenEndpointAddressValidator(
        Supplier<ConnectorAddressValidator> base,
        Set<Integer> refusedPorts,
        CheckedSupplier<Set<InetAddress>, SocketException> interfaceAddresses
    ) {
        this.base = base;
        this.refusedPorts = refusedPorts;
        this.interfaceAddresses = interfaceAddresses;
    }

    /**
     * Resolves {@code host} once and checks every address it resolved to.
     *
     * @param url the token URL, matched against the trusted private endpoint patterns when the host is private
     * @param port the port as {@code HttpUrl.port()} gives it, never -1
     * @return the addresses {@code host} resolved to, each of them allowed
     * @throws IllegalArgumentException with one of this class's messages when the port or an address is refused
     * @throws IllegalStateException when the node's interface addresses cannot be read
     * @throws UnknownHostException when {@code host} does not resolve
     */
    public InetAddress[] validate(String url, String host, int port) throws UnknownHostException {
        if (refusedPorts.contains(port)) {
            throw new IllegalArgumentException(REFUSED_PORT_MESSAGE);
        }
        InetAddress[] addresses;
        try {
            addresses = base.get().validate(url, "https", host, port);
        } catch (IllegalArgumentException e) {
            // The connector address checks have logged the host and the reason; their message names remote inference.
            throw new IllegalArgumentException(ADDRESS_NOT_ALLOWED_MESSAGE);
        }
        checkAddresses(addresses);
        return addresses;
    }

    /**
     * Refuses the addresses when any of them, or an IPv4 address a connection to it may be delivered to, is loopback,
     * unspecified, IPv6 unique-local or an address of this node's interfaces.
     */
    void checkAddresses(InetAddress[] addresses) {
        Set<InetAddress> own;
        try {
            own = interfaceAddresses.get();
        } catch (SocketException e) {
            throw new IllegalStateException(INTERFACE_ADDRESSES_UNAVAILABLE_MESSAGE);
        }
        for (InetAddress address : addresses) {
            for (InetAddress delivered : ConnectorAddressValidator.deliveryAddresses(address)) {
                if (delivered.isLoopbackAddress() || delivered.isAnyLocalAddress() || isUniqueLocal(delivered) || own.contains(delivered)) {
                    throw new IllegalArgumentException(REFUSED_ADDRESS_MESSAGE);
                }
            }
        }
    }

    /** fc00::/7. */
    private static boolean isUniqueLocal(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    /** The ports of {@code http.port} and of {@code transport.port} (with its {@code transport.tcp.port} fallback). */
    public static Set<Integer> clusterPorts(Settings nodeSettings) {
        int[] http = HttpTransportSettings.SETTING_HTTP_PORT.get(nodeSettings).ports();
        int[] transport = new PortsRange(TransportSettings.PORT.get(nodeSettings)).ports();
        return IntStream.concat(Arrays.stream(http), Arrays.stream(transport)).boxed().collect(Collectors.toUnmodifiableSet());
    }
}
