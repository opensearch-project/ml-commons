package org.opensearch.ml.engine.httpclient;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.util.Strings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Log4j2
public class MLHttpClientFactory {

    public static CloseableHttpClient getCloseableHttpClient() {
       return createHttpClient();
    }

    private static CloseableHttpClient createHttpClient() {
        HttpClientBuilder builder = HttpClientBuilder.create();

        // Only allow HTTP and HTTPS schemes
        builder.setSchemePortResolver(new DefaultSchemePortResolver() {
            @Override
            public int resolve(HttpHost host) throws UnsupportedSchemeException {
                validateSchemaAndPort(host);
                return super.resolve(host);
            }
        });

        builder.setDnsResolver(hostName -> {
            validateIp(hostName);
            return InetAddress.getAllByName(hostName);
        });

        builder.setRedirectStrategy(new LaxRedirectStrategy() {
            @Override
            public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                // Do not follow redirects
                return false;
            }
        });
        return builder.build();
    }

    @VisibleForTesting
    protected static void validateSchemaAndPort(HttpHost host) {
        if (Strings.isBlank(host.getHostName())) {
            log.error("Remote inference host name is empty!");
            throw new IllegalArgumentException("Host name is empty!");
        }
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

    protected static void validateIp(String hostName) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(hostName);
        if (hasPrivateIpAddress(addresses)) {
            log.error("Remote inference host name has private ip address: " + hostName);
            throw new IllegalArgumentException(hostName);
        }
    }

    private static boolean hasPrivateIpAddress(InetAddress[] ipAddress) {
        return Arrays.stream(ipAddress).anyMatch(x -> x.isSiteLocalAddress() || x.isLoopbackAddress());
    }
}
