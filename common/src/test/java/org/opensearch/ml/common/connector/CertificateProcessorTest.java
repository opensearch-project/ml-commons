/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_KEY_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.KEYSTORE_TYPE_PEM;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.exception.MLValidationException;

public class CertificateProcessorTest {

    private CertificateProcessor certificateProcessor;
    private ConnectorClientConfig config;
    private Map<String, String> credentials;

    @Before
    public void setUp() {
        certificateProcessor = new CertificateProcessor();
        credentials = new HashMap<>();
    }

    @Test
    public void testBuildSSLContext_MutualTlsDisabled_ReturnsNull() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        SSLContext result = certificateProcessor.buildSSLContext(config, credentials);
        assertNull("SSL context should be null when mutual TLS is disabled", result);
    }

    @Test
    public void testBuildSSLContext_MutualTlsEnabled_NoCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        assertThrows(
            "Should throw MLValidationException when credentials are missing",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, null)
        );
    }

    @Test
    public void testValidateCertificateConfig_MutualTlsDisabled_NoValidation() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        // Should not throw any exception
        certificateProcessor.validateCertificateConfig(config, null);
    }

    @Test
    public void testValidateCertificateConfig_PemType_MissingCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        // Missing certificate

        assertThrows(
            "Should throw MLValidationException when PEM certificate is missing",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateConfig_PemType_MissingPrivateKey_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        // Missing private key

        assertThrows(
            "Should throw MLValidationException when PEM private key is missing",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateConfig_PemType_ValidCredentials_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        // Should not throw any exception
        certificateProcessor.validateCertificateConfig(config, credentials);
    }

    @Test
    public void testValidateCertificateConfig_UnsupportedKeystoreType_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("UNSUPPORTED").build();

        assertThrows(
            "Should throw MLValidationException for unsupported keystore type",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateConfig_JksType_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("JKS").build();

        assertThrows(
            "Should throw MLValidationException for JKS keystore type (not implemented)",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_MixedAuth_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", "test-api-key"); // This should cause validation to fail

        assertThrows(
            "Should throw MLValidationException when both certificates and API key are present",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateOnlyAuthentication(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_CertificateOnly_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        // No API key - this should pass validation

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_WithCertificatesOnly_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertTrue("Should return true for certificate-only authentication", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_WithMixedAuth_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", "test-api-key");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false when both certificates and API key are present", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_MutualTlsDisabled_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false when mutual TLS is disabled", result);
    }
}
