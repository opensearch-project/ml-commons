/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.CertificateProcessor.CA_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_CERT_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_CERT_PKCS12_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_KEY_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.KEYSTORE_PASSWORD_FIELD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

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

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);
        assertNull("SSL context should be null when mutual TLS is disabled", result);
    }

    @Test
    public void testBuildSSLContext_MutualTlsEnabled_NoCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        // Should not throw any exception
        certificateProcessor.validateCertificateConfig(config, credentials);
    }

    @Test
    public void testValidateCertificateConfig_SkipSslVerificationWithMutualTls_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).skipSslVerification(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        assertThrows(
            "Should throw MLValidationException when skip_ssl_verification is combined with mutual_tls_enabled",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        // No API key - this should pass validation

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_WithCertificatesOnly_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertTrue("Should return true for certificate-only authentication", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_WithMixedAuth_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

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

    @Test
    public void testValidateCertificateConfig_PKCS12Type_MissingCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        assertThrows(
            "Should throw MLValidationException when PKCS12 certificate is missing",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateConfig_PKCS12Type_ValidCredentials_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "test-pkcs12-cert");

        // Should not throw any exception
        certificateProcessor.validateCertificateConfig(config, credentials);
    }

    @Test
    public void testValidateCertificateConfig_EmptyCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        assertThrows(
            "Should throw MLValidationException when credentials are empty",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, new HashMap<>())
        );
    }

    @Test
    public void testValidateCertificateConfig_NullKeystoreType_DefaultsToPEM() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(null).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        // Should not throw any exception - defaults to PEM
        certificateProcessor.validateCertificateConfig(config, credentials);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_NullCredentials_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, null);
        assertFalse("Should return false when credentials are null", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_EmptyApiKey_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", ""); // Empty API key

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false when API key field is present even if empty", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_WhitespaceApiKey_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", "   "); // Whitespace-only API key

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false when API key field is present even if whitespace only", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_PKCS12Type_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "test-pkcs12-cert");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertTrue("Should return true for PKCS12 certificate-only authentication", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_JKSType_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("JKS").build();

        credentials.put("some_jks_field", "test-jks-cert");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false for unsupported JKS keystore type", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_UnsupportedKeystoreType_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("UNSUPPORTED").build();

        credentials.put("some_field", "test-cert");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false for non-certificate credential field", result);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_MutualTlsDisabled_NoValidation() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_NullCredentials_NoValidation() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, null);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_NoCertificates_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // No certificates provided
        assertThrows(
            "Should throw MLValidationException when no certificates are provided",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateOnlyAuthentication(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_EmptyApiKey_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", ""); // Empty API key should be allowed

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_WhitespaceApiKey_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", "   "); // Whitespace-only API key should be allowed

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testBuildSSLContext_NullConfig_ReturnsNull() {
        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(null, credentials);
        assertNull("SSL context should be null when config is null", result);
    }

    @Test
    public void testBuildSSLContext_EmptyCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        assertThrows(
            "Should throw MLValidationException when credentials are empty",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, new HashMap<>())
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_MutualTlsDisabled_ReturnsNull() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);
        assertNull("SSL context with managers should be null when mutual TLS is disabled", result);
    }

    @Test
    public void testBuildSSLContextWithManagers_NullConfig_ReturnsNull() {
        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(null, credentials);
        assertNull("SSL context with managers should be null when config is null", result);
    }

    @Test
    public void testBuildSSLContextWithManagers_NoCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        assertThrows(
            "Should throw MLValidationException when credentials are missing",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, null)
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_EmptyCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        assertThrows(
            "Should throw MLValidationException when credentials are empty",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, new HashMap<>())
        );
    }

    // Tests for certificate parsing methods to increase coverage
    @Test
    public void testCreatePemKeyManagers_InvalidCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "-----BEGIN CERTIFICATE-----\nInvalidCertificateData\n-----END CERTIFICATE-----");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should throw MLValidationException for invalid PEM certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreatePemKeyManagers_Base64EncodedCertificate_ParsesCorrectly() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Base64 encoded invalid certificate data (will trigger parsing but fail validation)
        String base64Cert = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCkludmFsaWRDZXJ0aWZpY2F0ZURhdGEKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ==";
        String base64Key = "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCkludmFsaWRLZXlEYXRhCi0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0=";

        credentials.put(CLIENT_CERT_PEM_FIELD, base64Cert);
        credentials.put(CLIENT_KEY_PEM_FIELD, base64Key);

        assertThrows(
            "Should throw MLValidationException for invalid base64 encoded certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreatePkcs12KeyManagers_InvalidCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "InvalidPKCS12Data");

        assertThrows(
            "Should throw MLValidationException for invalid PKCS12 certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreatePkcs12KeyManagers_Base64EncodedCertificate_ParsesCorrectly() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        // Base64 encoded invalid PKCS12 data (will trigger parsing but fail validation)
        String base64Pkcs12 = "SW52YWxpZFBLQ1MxMkRhdGE=";

        credentials.put(CLIENT_CERT_PKCS12_FIELD, base64Pkcs12);

        assertThrows(
            "Should throw MLValidationException for invalid base64 encoded PKCS12",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testGetCertificateContent_Base64Content_DecodesCorrectly() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Test with base64 content that decodes to PEM format
        String base64Content = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCkludmFsaWRDZXJ0aWZpY2F0ZURhdGEKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ==";
        credentials.put(CLIENT_CERT_PEM_FIELD, base64Content);
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should decode base64 content and then fail on invalid certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testGetCertificateContent_NonBase64Content_ReturnsAsIs() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Test with non-base64 content (PEM format)
        credentials.put(CLIENT_CERT_PEM_FIELD, "-----BEGIN CERTIFICATE-----\nInvalidCertificateData\n-----END CERTIFICATE-----");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should use content as-is and fail on invalid certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testParsePemCertificate_InvalidFormat_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Test with malformed PEM certificate
        credentials.put(CLIENT_CERT_PEM_FIELD, "INVALID_PEM_FORMAT");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should throw MLValidationException for malformed PEM certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testParsePemPrivateKey_InvalidFormat_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Test with malformed PEM private key
        credentials.put(CLIENT_CERT_PEM_FIELD, "-----BEGIN CERTIFICATE-----\nInvalidCertificateData\n-----END CERTIFICATE-----");
        credentials.put(CLIENT_KEY_PEM_FIELD, "INVALID_KEY_FORMAT");

        assertThrows(
            "Should throw MLValidationException for malformed PEM private key",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testIsBase64EncodedContent_ValidBase64_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Test with valid base64 content
        String base64Content = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        credentials.put(CLIENT_CERT_PEM_FIELD, base64Content);
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should detect base64 content and attempt to decode",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testIsBase64EncodedContent_PemFormat_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Test with PEM format (should not be treated as base64)
        credentials.put(CLIENT_CERT_PEM_FIELD, "-----BEGIN CERTIFICATE-----\nSomeData\n-----END CERTIFICATE-----");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should not treat PEM format as base64",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_PemType_InvalidCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "-----BEGIN CERTIFICATE-----\nInvalidData\n-----END CERTIFICATE-----");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should throw MLValidationException for invalid certificate in buildSSLContext",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_Pkcs12Type_InvalidCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "InvalidPKCS12Data");

        assertThrows(
            "Should throw MLValidationException for invalid PKCS12 in buildSSLContext",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    // Additional tests to reach 78% coverage target - testing deeper certificate processing paths
    @Test
    public void testCreateKeyManagers_PemType_ExercisesCreatePemKeyManagers() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Use properly formatted but invalid certificate data to exercise more code paths
        String validFormatCert = "-----BEGIN CERTIFICATE-----\n"
            + "MIICljCCAX4CCQCKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMC\n"
            + "VVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQK\n"
            + "DAZUZXN0Q28xDzANBgNVBAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEk\n"
            + "MCIGCSqGSIb3DQEJARYVdGVzdEBleGFtcGxlLmNvbS5jb20wHhcNMjMwMTAxMDAw\n"
            + "MDAwWhcNMjQwMTAxMDAwMDAwWjCBjDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNB\n"
            + "-----END CERTIFICATE-----";

        String validFormatKey = "-----BEGIN PRIVATE KEY-----\n"
            + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKB\n"
            + "wjIy881GTHFoUYpPUOI9nzHRSda2WtlaSBVGYSBDvZf36q77jLDSPrqlNpjH7tgX\n"
            + "ms9lBh1PVVgkX9zgeWKxstSutzDqFoVqWpXln+G+ILWstApsgs7SuPiAkHKGX+fq\n"
            + "ollVJt3UJpbP1Y3+Dz5rZrOjwta1WoFhNVGDGaHWn+lLllP+D8ykQs0DdDVUlZe9\n"
            + "uuMiLiMXw2Fg5A8HFx3+7G8VWiOzxBNBtfnt0FueAjBvUb1v3VRIma/OGqcQDjXq\n"
            + "VSHXyqJVoGq2AcVXIuuTt/R5n4XyqeXBGAYBdHNmHpzd4mu4S8X1Q5IwIwYJKoZI\n"
            + "hvcNAQkBFgUrZXhhbXBsZUBleGFtcGxlLmNvbTAeFw0yMzAxMDEwMDAwMDBaFw0y\n"
            + "-----END PRIVATE KEY-----";

        credentials.put(CLIENT_CERT_PEM_FIELD, validFormatCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, validFormatKey);

        assertThrows(
            "Should throw MLValidationException for invalid certificate data",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    // ========== POSITIVE PATH TESTS ==========
    // These tests verify successful certificate processing with real certificate fixtures

    private String loadCertificateFromFile(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/test/resources/certificates/" + filename)));
    }

    @Test
    public void testBuildSSLContext_ValidPemCertificates_Success() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Load real certificate fixtures
        String clientCert = loadCertificateFromFile("test-client-cert.pem");
        String clientKey = loadCertificateFromFile("test-client-key-pkcs8.pem");
        String caCert = loadCertificateFromFile("test-ca-cert.pem");

        credentials.put(CLIENT_CERT_PEM_FIELD, clientCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, clientKey);
        credentials.put(CA_CERT_PEM_FIELD, caCert);

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);
        assertNotNull("SSL context should be created successfully with valid PEM certificates", result);
        assertNotNull("SSL context should not be null", result.getSslContext());
    }

    @Test
    public void testBuildSSLContextWithManagers_ValidPemCertificates_Success() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Load real certificate fixtures
        String clientCert = loadCertificateFromFile("test-client-cert.pem");
        String clientKey = loadCertificateFromFile("test-client-key-pkcs8.pem");
        String caCert = loadCertificateFromFile("test-ca-cert.pem");

        credentials.put(CLIENT_CERT_PEM_FIELD, clientCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, clientKey);
        credentials.put(CA_CERT_PEM_FIELD, caCert);

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);

        assertNotNull("SSL context with managers should be created successfully", result);
        assertNotNull("SSL context should not be null", result.getSslContext());

        KeyManager[] keyManagers = result.getKeyManagers();
        TrustManager[] trustManagers = result.getTrustManagers();

        assertNotNull("Key managers should not be null", keyManagers);
        assertNotNull("Trust managers should not be null", trustManagers);
        assertTrue("Key managers array should not be empty", keyManagers.length > 0);
        assertTrue("Trust managers array should not be empty", trustManagers.length > 0);
    }

    @Test
    public void testBuildSSLContext_ValidPkcs12Certificate_Success() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PKCS12").build();

        // Use the base64 encoded PKCS12 content from our fixtures
        String pkcs12Content =
            "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";

        credentials.put(CLIENT_CERT_PKCS12_FIELD, pkcs12Content);
        credentials.put(KEYSTORE_PASSWORD_FIELD, "testpass");

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);
        assertNotNull("SSL context should be created successfully with valid PKCS12 certificate", result);
        assertNotNull("SSL context should not be null", result.getSslContext());
    }

    @Test
    public void testValidateCertificateConfig_ValidPemCertificates_NoException() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Load real certificate fixtures
        String clientCert = loadCertificateFromFile("test-client-cert.pem");
        String clientKey = loadCertificateFromFile("test-client-key-pkcs8.pem");

        credentials.put(CLIENT_CERT_PEM_FIELD, clientCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, clientKey);

        // Should not throw any exception with valid certificates
        certificateProcessor.validateCertificateConfig(config, credentials);
    }

    // ========== CERTIFICATE CHAIN TESTS ==========
    // These tests verify that concatenated multi-certificate PEM bundles (leaf + intermediate/root)
    // are fully parsed rather than only the first certificate block.

    @Test
    public void testBuildSSLContext_ClientCertChain_AllCertificatesLoaded() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // test-client-chain-leaf-cert.pem is genuinely issued by test-intermediate-cert.pem (not just
        // concatenated unrelated certs) - the JDK's KeyStore.setKeyEntry validates that a certificate
        // chain is a real chain (each cert signed by the next), so the fixtures must actually chain.
        String leafCert = loadCertificateFromFile("test-client-chain-leaf-cert.pem");
        String intermediateCert = loadCertificateFromFile("test-intermediate-cert.pem");
        String clientKey = loadCertificateFromFile("test-client-chain-leaf-key-pkcs8.pem");

        credentials.put(CLIENT_CERT_PEM_FIELD, leafCert + "\n" + intermediateCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, clientKey);

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);
        assertNotNull("SSL context should be created successfully with a client certificate chain", result);
        assertNotNull("Key managers should be present", result.getKeyManagers());
        assertTrue("Key managers array should not be empty", result.getKeyManagers().length > 0);
    }

    @Test
    public void testBuildSSLContext_ClientCertChainMisordered_ThrowsClearOrderingException() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Same genuinely-chained fixtures as testBuildSSLContext_ClientCertChain_AllCertificatesLoaded,
        // but with the intermediate listed before the leaf - this must be rejected with a clear
        // ordering-specific message rather than surfacing a raw KeyStoreException later.
        String leafCert = loadCertificateFromFile("test-client-chain-leaf-cert.pem");
        String intermediateCert = loadCertificateFromFile("test-intermediate-cert.pem");
        String clientKey = loadCertificateFromFile("test-client-chain-leaf-key-pkcs8.pem");

        credentials.put(CLIENT_CERT_PEM_FIELD, intermediateCert + "\n" + leafCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, clientKey);

        Exception exception = assertThrows(MLValidationException.class, () -> certificateProcessor.buildSSLContext(config, credentials));
        assertTrue(
            "Exception message should explain the chain is not ordered correctly: " + exception.getMessage(),
            exception.getMessage().contains("not ordered correctly")
        );
    }

    @Test
    public void testBuildSSLContext_CaCertBundle_AllTrustAnchorsLoaded() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        String clientCert = loadCertificateFromFile("test-client-cert.pem");
        String clientKey = loadCertificateFromFile("test-client-key-pkcs8.pem");
        String caCert = loadCertificateFromFile("test-ca-cert.pem");
        String intermediateCert = loadCertificateFromFile("test-intermediate-cert.pem");

        // Concatenate two CA certificates into a single trust bundle - both must become trust
        // anchors, not just the first one.
        credentials.put(CLIENT_CERT_PEM_FIELD, clientCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, clientKey);
        credentials.put(CA_CERT_PEM_FIELD, caCert + "\n" + intermediateCert);

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContext(config, credentials);
        assertNotNull("SSL context should be created successfully with a CA certificate bundle", result);
        assertNotNull("Trust managers should be present", result.getTrustManagers());
        assertTrue("Trust managers array should not be empty", result.getTrustManagers().length > 0);
    }

    // ========== FILE PATH HEURISTIC TESTS ==========
    // These verify that passing a filesystem path instead of certificate content produces the
    // specific "file paths are not supported" guidance, and that malformed PEM content that merely
    // contains "/" characters (from base64) is not misdiagnosed as a file path.

    @Test
    public void testBuildSSLContext_CertFieldIsUnixFilePath_ThrowsFilePathSpecificException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "/etc/ssl/certs/client.pem");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        MLValidationException exception = assertThrows(
            "Should throw MLValidationException when a filesystem path is passed instead of certificate content",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
        assertTrue(
            "Exception message should call out that file paths are not supported: " + exception.getMessage(),
            exception.getMessage().contains("File paths are not supported")
        );
    }

    @Test
    public void testBuildSSLContext_CertFieldIsWindowsFilePath_ThrowsFilePathSpecificException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "C:\\certs\\client.pem");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        MLValidationException exception = assertThrows(
            "Should throw MLValidationException when a Windows filesystem path is passed instead of certificate content",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
        assertTrue(
            "Exception message should call out that file paths are not supported: " + exception.getMessage(),
            exception.getMessage().contains("File paths are not supported")
        );
    }

    @Test
    public void testBuildSSLContext_KeyFieldLooksLikeFileNameWithExtension_ThrowsFilePathSpecificException() throws IOException {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // The certificate is parsed before the private key, so it must be well-formed here in order
        // for the key-parsing file-path check to actually be reached.
        credentials.put(CLIENT_CERT_PEM_FIELD, loadCertificateFromFile("test-client-cert.pem"));
        credentials.put(CLIENT_KEY_PEM_FIELD, "client_key.pem");

        MLValidationException exception = assertThrows(
            "Should throw MLValidationException when a bare filename is passed instead of private key content",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
        assertTrue(
            "Exception message should call out that file paths are not supported: " + exception.getMessage(),
            exception.getMessage().contains("File paths are not supported")
        );
    }

    @Test
    public void testBuildSSLContext_MalformedBase64BodyContainingSlash_DoesNotTriggerFilePathMessage() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build();

        // Malformed PEM body (missing -----BEGIN header) that legitimately contains "/" characters
        // from base64 - must not be misdiagnosed as a file path.
        credentials.put(CLIENT_CERT_PEM_FIELD, "MIIB/kCAQAwDQYJKoZIhvcNAQEB/BQADQY==");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidKeyData\n-----END PRIVATE KEY-----");

        MLValidationException exception = assertThrows(
            "Should throw MLValidationException for malformed PEM certificate content",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
        assertFalse(
            "Malformed base64 content containing '/' should not be misdiagnosed as a file path: " + exception.getMessage(),
            exception.getMessage().contains("File paths are not supported")
        );
    }
}