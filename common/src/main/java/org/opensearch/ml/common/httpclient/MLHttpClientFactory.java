/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.common.util.concurrent.ThreadContextAccess;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

@Log4j2
public class MLHttpClientFactory {

    public static SdkAsyncHttpClient getAsyncHttpClient(Duration connectionTimeout, Duration readTimeout, int maxConnections) {
        return ThreadContextAccess
            .doPrivileged(
                () -> NettyNioAsyncHttpClient
                    .builder()
                    .connectionTimeout(connectionTimeout)
                    .readTimeout(readTimeout)
                    .maxConcurrency(maxConnections)
                    .build()
            );
    }

    /**
     * Validate the input parameters, such as protocol, host and port.
     * @param protocol The protocol supported in remote inference, currently only http and https are supported.
     * @param host The host name of the remote inference server, host must be a valid ip address or domain name and must not be localhost.
     * @param port The port number of the remote inference server, port number must be in range [0, 65536].
     * @param connectorPrivateIpEnabled The port number of the remote inference server, port number must be in range [0, 65536].
     * @throws UnknownHostException Allow to use private IP or not.
     */
    public static void validate(String protocol, String host, int port, AtomicBoolean connectorPrivateIpEnabled)
        throws UnknownHostException {
        if (protocol != null && !"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            log.error("Remote inference protocol is not http or https: " + protocol);
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
            log.error("Remote inference port out of range: " + port);
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        validateIp(host, connectorPrivateIpEnabled);
    }

    private static void validateIp(String hostName, AtomicBoolean connectorPrivateIpEnabled) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(hostName);
        if ((connectorPrivateIpEnabled == null || !connectorPrivateIpEnabled.get()) && hasPrivateIpAddress(addresses)) {
            log.error("Remote inference host name has private ip address: " + hostName);
            throw new IllegalArgumentException("Remote inference host name has private ip address: " + hostName);
        }
    }

    private static boolean hasPrivateIpAddress(InetAddress[] ipAddress) {
        for (InetAddress ip : ipAddress) {
            if (ip instanceof Inet4Address) {
                byte[] bytes = ip.getAddress();
                if (bytes.length != 4) {
                    return true;
                } else {
                    int firstOctets = bytes[0] & 0xff;
                    int firstInOctal = parseWithOctal(String.valueOf(firstOctets));
                    int firstInHex = Integer.parseInt(String.valueOf(firstOctets), 16);
                    if (firstInOctal == 127 || firstInHex == 127) {
                        return bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 1;
                    } else if (firstInOctal == 10 || firstInHex == 10) {
                        return true;
                    } else if (firstInOctal == 172 || firstInHex == 172) {
                        int secondOctets = bytes[1] & 0xff;
                        int secondInOctal = parseWithOctal(String.valueOf(secondOctets));
                        int secondInHex = Integer.parseInt(String.valueOf(secondOctets), 16);
                        return (secondInOctal >= 16 && secondInOctal <= 32) || (secondInHex >= 16 && secondInHex <= 32);
                    } else if (firstInOctal == 192 || firstInHex == 192) {
                        int secondOctets = bytes[1] & 0xff;
                        int secondInOctal = parseWithOctal(String.valueOf(secondOctets));
                        int secondInHex = Integer.parseInt(String.valueOf(secondOctets), 16);
                        return secondInOctal == 168 || secondInHex == 168;
                    }
                }
            }
        }
        return Arrays.stream(ipAddress).anyMatch(x -> x.isSiteLocalAddress() || x.isLoopbackAddress() || x.isAnyLocalAddress());
    }

    private static int parseWithOctal(String input) {
        try {
            return Integer.parseInt(input, 8);
        } catch (NumberFormatException e) {
            return Integer.parseInt(input);
        }
    }
}
