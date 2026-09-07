/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opensearch.ml.common.connector.CertificateProcessor.CA_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_KEY_PEM_FIELD;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

/**
 * Runs complete TLS handshakes through {@link SSLEngine} in memory, so no ports or threads are
 * involved. Client engines set {@code endpointIdentificationAlgorithm=HTTPS} as the JDK HTTP client
 * does, which is what makes the hostname-verification assertions meaningful.
 */
public class MLSslContextFactoryTlsHandshakeTest {

    // [^\r\n]* mirrors CertificateProcessor's patterns so fixtures parse exactly as production does.
    private static final Pattern CERT_PATTERN = Pattern
        .compile("-----BEGIN CERTIFICATE-----[^\\r\\n]*\\s*([A-Za-z0-9+/\\s=]+?)\\s*-----END CERTIFICATE-----", Pattern.DOTALL);
    private static final Pattern KEY_PATTERN = Pattern
        .compile("-----BEGIN PRIVATE KEY-----[^\\r\\n]*\\s*([A-Za-z0-9+/\\s=]+?)\\s*-----END PRIVATE KEY-----", Pattern.DOTALL);

    private static final String CLIENT_TARGET_HOST = "localhost";

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private CertificateProcessor certificateProcessor;

    @Before
    public void setUp() {
        certificateProcessor = new CertificateProcessor();
    }

    @Test
    public void testMutualTls_ClientCertificateIsPresentedToServer() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put(CLIENT_CERT_PEM_FIELD, readFixture("mtls-client-cert.pem"));
        credentials.put(CLIENT_KEY_PEM_FIELD, readFixture("mtls-client-key.pem"));
        credentials.put(CA_CERT_PEM_FIELD, readFixture("mtls-ca-cert.pem"));

        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        SSLContext sslContext = MLSslContextFactory.create(config, credentials, certificateProcessor);
        assertNotNull("Mutual TLS configuration must produce an SSLContext", sslContext);

        SSLEngine client = clientEngine(sslContext);
        SSLEngine server = serverEngine("mtls-server-cert.pem", "mtls-server-key.pem", "mtls-ca-cert.pem", true);

        handshake(client, server);

        X509Certificate presented = (X509Certificate) server.getSession().getPeerCertificates()[0];
        assertNotNull("Server must have received a client certificate", presented);
        assertTrue(
            "Server should see our test client identity, but saw: " + presented.getSubjectX500Principal().getName(),
            presented.getSubjectX500Principal().getName().contains("ml-commons-test-client")
        );
    }

    @Test
    public void testMutualTls_WithoutClientCertificateHandshakeFails() throws Exception {
        SSLEngine client = clientEngine(trustOnlyContext());
        SSLEngine server = serverEngine("mtls-server-cert.pem", "mtls-server-key.pem", "mtls-ca-cert.pem", true);

        try {
            handshake(client, server);
            fail("Handshake must fail when no client certificate is presented to a server requiring client auth");
        } catch (SSLException expected) {
            assertRejectedByTls(expected);
        }
    }

    @Test
    public void testMutualTls_UntrustedServerIsRejected() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put(CLIENT_CERT_PEM_FIELD, readFixture("mtls-client-cert.pem"));
        credentials.put(CLIENT_KEY_PEM_FIELD, readFixture("mtls-client-key.pem"));
        credentials.put(CA_CERT_PEM_FIELD, readFixture("mtls-ca-cert.pem"));

        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();
        SSLContext sslContext = MLSslContextFactory.create(config, credentials, certificateProcessor);

        SSLEngine client = clientEngine(sslContext);
        SSLEngine server = serverEngine("mtls-untrusted-server-cert.pem", "mtls-untrusted-server-key.pem", null, false);

        try {
            handshake(client, server);
            fail("A server certificate outside the configured CA must be rejected");
        } catch (SSLException expected) {
            assertRejectedByTls(expected);
        }
    }

    /** Fails if TrustAllX509TrustManager ever stops extending X509ExtendedTrustManager. */
    @Test
    public void testSkipSslVerification_AcceptsUntrustedAndHostnameMismatchedServer() throws Exception {
        ConnectorClientConfig config = ConnectorClientConfig.builder().skipSslVerification(true).build();
        SSLContext sslContext = MLSslContextFactory.create(config, new HashMap<>(), certificateProcessor);
        assertNotNull("skip_ssl_verification must produce an SSLContext", sslContext);

        SSLEngine client = clientEngine(sslContext);
        SSLEngine server = serverEngine("mtls-untrusted-server-cert.pem", "mtls-untrusted-server-key.pem", null, false);

        handshake(client, server);

        assertTrue("Handshake should have completed against the untrusted server", client.getSession().isValid());
        assertEquals(
            "The mismatched hostname must not have prevented the handshake",
            "wrong-host.invalid",
            commonName((X509Certificate) client.getSession().getPeerCertificates()[0])
        );
    }

    @Test
    public void testUntrustedServerIsRejectedWithoutSkipSslVerification() throws Exception {
        SSLEngine client = clientEngine(SSLContext.getDefault());
        SSLEngine server = serverEngine("mtls-untrusted-server-cert.pem", "mtls-untrusted-server-key.pem", null, false);

        try {
            handshake(client, server);
            fail("An untrusted, hostname-mismatched server must be rejected by default");
        } catch (SSLException expected) {
            assertRejectedByTls(expected);
        }
    }

    @Test
    public void testHostnameMismatchIsRejectedEvenWhenChainIsTrusted() throws Exception {
        SSLEngine client = clientEngine(trustOnlyContext(), "not-the-right-host.example");
        SSLEngine server = serverEngine("mtls-server-cert.pem", "mtls-server-key.pem", "mtls-ca-cert.pem", false);

        try {
            handshake(client, server);
            fail("A trusted certificate for the wrong hostname must still be rejected");
        } catch (SSLException expected) {
            assertRejectedByTls(expected);
        }
    }

    private SSLEngine clientEngine(SSLContext sslContext) {
        return clientEngine(sslContext, CLIENT_TARGET_HOST);
    }

    private SSLEngine clientEngine(SSLContext sslContext, String peerHost) {
        SSLEngine engine = sslContext.createSSLEngine(peerHost, 443);
        engine.setUseClientMode(true);

        SSLParameters parameters = engine.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(parameters);

        return engine;
    }

    private SSLEngine serverEngine(String certFixture, String keyFixture, String caFixture, boolean needClientAuth) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore
            .setKeyEntry(
                "server",
                parsePrivateKey(readFixture(keyFixture)),
                new char[0],
                new X509Certificate[] { parseCertificate(readFixture(certFixture)) }
            );

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        TrustManagerFactory tmf = null;
        if (caFixture != null) {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", parseCertificate(readFixture(caFixture)));
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
        }

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf != null ? tmf.getTrustManagers() : null, null);

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(needClientAuth);
        return engine;
    }

    private SSLContext trustOnlyContext() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", parseCertificate(readFixture("mtls-ca-cert.pem")));

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;
    }

    private static final ByteBuffer NO_APP_DATA = ByteBuffer.allocate(0);

    private static final String STALL_MESSAGE = "TLS handshake stalled";
    private static final String NO_CONVERGE_MESSAGE = "TLS handshake did not converge";

    /** Without this, a negative test would pass even if the rejection never happened. */
    private static void assertRejectedByTls(SSLException exception) {
        String message = String.valueOf(exception.getMessage());
        assertTrue(
            "Handshake must fail through TLS validation, not a harness problem: " + message,
            !message.contains(STALL_MESSAGE) && !message.contains(NO_CONVERGE_MESSAGE)
        );
    }

    /**
     * Buffers must persist across rounds: unwrap legitimately consumes nothing when the receiving
     * engine has to wrap a reply first, and compact() re-offers those bytes on the next round.
     */
    private static void handshake(SSLEngine client, SSLEngine server) throws SSLException {
        client.beginHandshake();
        server.beginHandshake();

        int packetSize = Math.max(client.getSession().getPacketBufferSize(), server.getSession().getPacketBufferSize());
        ByteBuffer clientToServer = ByteBuffer.allocate(packetSize * 2);
        ByteBuffer serverToClient = ByteBuffer.allocate(packetSize * 2);

        for (int round = 0; round < 200; round++) {
            boolean progress = produce(client, clientToServer);
            progress |= produce(server, serverToClient);
            progress |= consume(server, clientToServer);
            progress |= consume(client, serverToClient);

            boolean drained = clientToServer.position() == 0 && serverToClient.position() == 0;
            if (isComplete(client) && isComplete(server) && drained) {
                return;
            }
            if (!progress) {
                throw new SSLException(
                    STALL_MESSAGE
                        + ": client="
                        + client.getHandshakeStatus()
                        + ", server="
                        + server.getHandshakeStatus()
                        + ", pending="
                        + clientToServer.position()
                        + "/"
                        + serverToClient.position()
                );
            }
        }
        throw new SSLException(NO_CONVERGE_MESSAGE + " within the expected number of rounds");
    }

    private static boolean produce(SSLEngine from, ByteBuffer outbound) throws SSLException {
        boolean produced = false;
        runDelegatedTasks(from);

        while (from.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            SSLEngineResult result = from.wrap(NO_APP_DATA, outbound);
            runDelegatedTasks(from);

            if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                throw new SSLException("Engine closed during handshake");
            }
            if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                break;
            }
            if (result.bytesProduced() == 0) {
                break;
            }
            produced = true;
        }
        return produced;
    }

    private static boolean consume(SSLEngine to, ByteBuffer inbound) throws SSLException {
        if (inbound.position() == 0) {
            return false;
        }

        boolean consumed = false;
        ByteBuffer app = ByteBuffer.allocate(to.getSession().getApplicationBufferSize());
        inbound.flip();

        try {
            while (inbound.hasRemaining()) {
                int positionBefore = inbound.position();
                SSLEngineResult result = to.unwrap(inbound, app);
                runDelegatedTasks(to);

                if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    app = ByteBuffer.allocate(app.capacity() * 2);
                    continue;
                }
                // Remainder is preserved by compact() below in all three cases.
                if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW
                    || result.getStatus() == SSLEngineResult.Status.CLOSED
                    || (inbound.position() == positionBefore && result.bytesProduced() == 0)) {
                    break;
                }
                consumed = true;
            }
        } finally {
            inbound.compact();
        }
        return consumed;
    }

    private static boolean isComplete(SSLEngine engine) {
        return engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING && engine.getSession().isValid();
    }

    /** Single if, not a loop: while(NEED_TASK) would busy-wait if no task were ever available. */
    private static void runDelegatedTasks(SSLEngine engine) {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable task;
            while ((task = engine.getDelegatedTask()) != null) {
                task.run();
            }
        }
    }

    private static String readFixture(String filename) throws IOException {
        try (InputStream in = MLSslContextFactoryTlsHandshakeTest.class.getResourceAsStream("/certificates/" + filename)) {
            assertNotNull("Missing test fixture on classpath: /certificates/" + filename, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static X509Certificate parseCertificate(String pem) throws Exception {
        Matcher matcher = CERT_PATTERN.matcher(pem);
        assertTrue("Fixture does not contain a certificate block", matcher.find());
        byte[] der = Base64.getMimeDecoder().decode(matcher.group(1).replaceAll("\\s", ""));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        Matcher matcher = KEY_PATTERN.matcher(pem);
        assertTrue("Fixture does not contain a PKCS#8 private key block", matcher.find());
        byte[] der = Base64.getMimeDecoder().decode(matcher.group(1).replaceAll("\\s", ""));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String commonName(X509Certificate certificate) {
        Matcher matcher = Pattern.compile("CN=([^,]+)").matcher(certificate.getSubjectX500Principal().getName());
        assertTrue("Certificate subject has no CN", matcher.find());
        return matcher.group(1);
    }
}
