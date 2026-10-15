/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.CertificateProcessor.CA_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_KEY_PEM_FIELD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLValidationException;

public class MLSslContextFactoryTest {

    private CertificateProcessor certificateProcessor;
    private Map<String, String> credentials;

    @Before
    public void setUp() {
        certificateProcessor = new CertificateProcessor();
        credentials = new HashMap<>();
    }

    private String loadCertificateFromFile(String filename) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/certificates/" + filename)) {
            assertNotNull("Missing test fixture on classpath: /certificates/" + filename, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void putValidPemCredentials() throws IOException {
        credentials.put(CLIENT_CERT_PEM_FIELD, loadCertificateFromFile("test-client-cert.pem"));
        credentials.put(CLIENT_KEY_PEM_FIELD, loadCertificateFromFile("test-client-key-pkcs8.pem"));
    }

    @Test
    public void testCreate_NullConfig_ReturnsNull() {
        assertNull(
            "A null client config must not produce an SSLContext",
            MLSslContextFactory.create(null, credentials, certificateProcessor)
        );
    }

    @Test
    public void testCreate_DefaultConfig_ReturnsNull() {
        assertNull(
            "A default config requests no TLS customization, so the JDK default SSLContext must be left in place",
            MLSslContextFactory.create(new ConnectorClientConfig(), credentials, certificateProcessor)
        );
    }

    @Test
    public void testCreate_MutualTlsAndSkipSslBothDisabled_ReturnsNull() {
        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(false).skipSslVerification(false).build();

        assertNull(
            "Explicitly disabling both options must not produce an SSLContext",
            MLSslContextFactory.create(config, credentials, certificateProcessor)
        );
    }

    @Test
    public void testCreate_MutualTlsDisabled_CertificatesIgnored() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();
        putValidPemCredentials();

        assertNull(
            "Certificate material present without mutual_tls_enabled must not silently enable mutual TLS",
            MLSslContextFactory.create(config, credentials, certificateProcessor)
        );
    }

    @Test
    public void testCreate_MutualTlsWithValidPemCertificates_ReturnsSslContext() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();
        putValidPemCredentials();

        SSLContext sslContext = MLSslContextFactory.create(config, credentials, certificateProcessor);

        assertNotNull("Mutual TLS must produce an SSLContext", sslContext);
        assertEquals("SSLContext should negotiate TLS", "TLS", sslContext.getProtocol());
        assertNotNull("SSLContext must be initialized and able to produce a socket factory", sslContext.getSocketFactory());
    }

    @Test
    public void testCreate_MutualTlsWithCustomCaCertificate_ReturnsSslContext() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();
        putValidPemCredentials();
        credentials.put(CA_CERT_PEM_FIELD, loadCertificateFromFile("test-ca-cert.pem"));

        SSLContext sslContext = MLSslContextFactory.create(config, credentials, certificateProcessor);

        assertNotNull("A custom CA certificate must still yield a usable SSLContext", sslContext);
        assertNotNull(sslContext.getSocketFactory());
    }

    @Test
    public void testCreate_MutualTlsWithMissingPrivateKey_ThrowsValidationException() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();
        credentials.put(CLIENT_CERT_PEM_FIELD, loadCertificateFromFile("test-client-cert.pem"));

        MLValidationException exception = assertThrows(
            "A missing private key must be reported, not ignored",
            MLValidationException.class,
            () -> MLSslContextFactory.create(config, credentials, certificateProcessor)
        );
        assertTrue(
            "Message should name the missing credential fields: " + exception.getMessage(),
            exception.getMessage().contains(CLIENT_KEY_PEM_FIELD)
        );
    }

    @Test
    public void testCreate_MutualTlsWithNoCredentials_ThrowsValidationException() {
        ConnectorClientConfig config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        assertThrows(
            "Enabling mutual TLS without credentials must fail loudly",
            MLValidationException.class,
            () -> MLSslContextFactory.create(config, new HashMap<>(), certificateProcessor)
        );
    }

    @Test
    public void testCreate_MutualTlsCombinedWithSkipSslVerification_ThrowsValidationException() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig
            .builder()
            .mutualTlsEnabled(true)
            .skipSslVerification(true)
            .keystoreType("PEM")
            .build();
        putValidPemCredentials();

        MLValidationException exception = assertThrows(
            "Presenting a client certificate while disabling server verification must be rejected",
            MLValidationException.class,
            () -> MLSslContextFactory.create(config, credentials, certificateProcessor)
        );
        assertTrue(
            "Message should explain the conflicting combination: " + exception.getMessage(),
            exception.getMessage().contains("skip_ssl_verification")
        );
    }

    @Test
    public void testCreate_SkipSslVerification_ReturnsSslContext() {
        ConnectorClientConfig config = ConnectorClientConfig.builder().skipSslVerification(true).build();

        SSLContext sslContext = MLSslContextFactory.create(config, credentials, certificateProcessor);

        assertNotNull("skip_ssl_verification must produce a trust-all SSLContext", sslContext);
        assertNotNull(sslContext.getSocketFactory());
    }

    @Test
    public void testCreate_SkipSslVerification_NoCredentialsRequired() {
        ConnectorClientConfig config = ConnectorClientConfig.builder().skipSslVerification(true).build();

        assertNotNull(
            "skip_ssl_verification must not require certificate credentials",
            MLSslContextFactory.create(config, null, certificateProcessor)
        );
    }

}
