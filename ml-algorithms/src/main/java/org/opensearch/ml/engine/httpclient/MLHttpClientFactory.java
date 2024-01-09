/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.httpclient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpHost;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;

@Log4j2
public class MLHttpClientFactory {

    public static SdkAsyncHttpClient getAsyncHttpClient() {
        return AwsCrtAsyncHttpClient.builder().build();
    }

//    private static SdkAsyncHttpClient createHttpClient() {

        // Only allow HTTP and HTTPS schemes
//        builder.setSchemePortResolver(new DefaultSchemePortResolver() {
//            @Override
//            public int resolve(HttpHost host) throws UnsupportedSchemeException {
//                validateSchemaAndPort(host);
//                return super.resolve(host);
//            }
//        });

//        builder.setDnsResolver(MLHttpClientFactory::validateIp);

//        builder.setRedirectStrategy(new LaxRedirectStrategy() {
//            @Override
//            public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
//                // Do not follow redirects
//                return false;
//            }
//        });
//        return builder.build();
//    }

    @VisibleForTesting
    protected static void validateSchemaAndPort(HttpHost host) {
        String scheme = host.getSchemeName();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String[] hostNamePort = host.getHostName().split(":");
            if (hostNamePort.length > 1 && NumberUtils.isDigits(hostNamePort[1])) {
                int port = Integer.parseInt(hostNamePort[1]);
                if (port < 0 || port > 65536) {
                    log.error("Remote inference port out of range: " + port);
                    throw new IllegalArgumentException("Port out of range: " + port);
                }
            }
        } else {
            log.error("Remote inference scheme not supported: " + scheme);
            throw new IllegalArgumentException("Unsupported scheme: " + scheme);
        }
    }

    protected static InetAddress[] validateIp(String hostName) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(hostName);
        if (hasPrivateIpAddress(addresses)) {
            log.error("Remote inference host name has private ip address: " + hostName);
            throw new IllegalArgumentException(hostName);
        }
        return addresses;
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
