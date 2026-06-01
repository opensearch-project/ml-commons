/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

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
        boolean hasPrivateIpAddress = hasPrivateIpAddress(addresses);

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

        if (!connectorPrivateIpEnabled && hasPrivateIpAddress) {
            log.error("Remote inference host name has private ip address: {}", hostName);
            throw new IllegalArgumentException("Remote inference host name has private ip address: " + hostName);
        }

        if (connectorTrustedPrivateEndpoints != null && !connectorTrustedPrivateEndpoints.isEmpty()) {
            boolean hasMatchedUrl = connectorTrustedPrivateEndpoints.stream().anyMatch(pattern -> pattern.matcher(endpoint).matches());
            if (hasPrivateIpAddress && !hasMatchedUrl) {
                throw new IllegalArgumentException("Connector URL is not matching the trusted connector private endpoint regex");
            }
        }
    }

    private boolean hasPrivateIpAddress(InetAddress[] ipAddress) {
        for (InetAddress ip : ipAddress) {
            if (ip instanceof Inet4Address) {
                byte[] bytes = ip.getAddress();
                if (bytes.length != 4) {
                    return true;
                } else {
                    if (isPrivateIPv4(bytes)) {
                        return true;
                    }
                }
            }
        }
        return Arrays.stream(ipAddress).anyMatch(x -> x.isSiteLocalAddress() || x.isLoopbackAddress() || x.isAnyLocalAddress());
    }

    private boolean isPrivateIPv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;

        // 127.0.0.1, 10.x.x.x, 172.16-31.x.x, 192.168.x.x, 169.254.x.x
        return (first == 10)
            || (first == 172 && second >= 16 && second <= 31)
            || (first == 192 && second == 168)
            || (first == 169 && second == 254);
    }
}
