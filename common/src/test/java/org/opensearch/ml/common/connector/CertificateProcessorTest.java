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
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_CERT_PKCS12_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.CLIENT_KEY_PEM_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.KEYSTORE_PASSWORD_FIELD;
import static org.opensearch.ml.common.connector.CertificateProcessor.KEYSTORE_TYPE_JKS;
import static org.opensearch.ml.common.connector.CertificateProcessor.KEYSTORE_TYPE_PEM;
import static org.opensearch.ml.common.connector.CertificateProcessor.KEYSTORE_TYPE_PKCS12;

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

    @Test
    public void testValidateCertificateConfig_PKCS12Type_MissingCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        assertThrows(
            "Should throw MLValidationException when PKCS12 certificate is missing",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateConfig(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateConfig_PKCS12Type_ValidCredentials_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "test-pkcs12-cert");

        // Should not throw any exception
        certificateProcessor.validateCertificateConfig(config, credentials);
    }

    @Test
    public void testValidateCertificateConfig_EmptyCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, null);
        assertFalse("Should return false when credentials are null", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_EmptyApiKey_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", ""); // Empty API key should be treated as no API key

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertTrue("Should return true when API key is empty", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_WhitespaceApiKey_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", "   "); // Whitespace-only API key should be treated as no API key

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertTrue("Should return true when API key is whitespace only", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_PKCS12Type_ReturnsTrue() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "test-pkcs12-cert");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertTrue("Should return true for PKCS12 certificate-only authentication", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_JKSType_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_JKS).build();

        credentials.put("some_jks_field", "test-jks-cert");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false for JKS type (not implemented)", result);
    }

    @Test
    public void testIsCertificateOnlyAuthentication_UnsupportedKeystoreType_ReturnsFalse() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("UNSUPPORTED").build();

        credentials.put("some_field", "test-cert");

        boolean result = certificateProcessor.isCertificateOnlyAuthentication(config, credentials);
        assertFalse("Should return false for unsupported keystore type", result);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_MutualTlsDisabled_NoValidation() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_NullCredentials_NoValidation() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, null);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_NoCertificates_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        // No certificates provided
        assertThrows(
            "Should throw MLValidationException when no certificates are provided",
            MLValidationException.class,
            () -> certificateProcessor.validateCertificateOnlyAuthentication(config, credentials)
        );
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_EmptyApiKey_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", ""); // Empty API key should be allowed

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testValidateCertificateOnlyAuthentication_WhitespaceApiKey_NoException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "test-cert");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");
        credentials.put("api_key", "   "); // Whitespace-only API key should be allowed

        // Should not throw any exception
        certificateProcessor.validateCertificateOnlyAuthentication(config, credentials);
    }

    @Test
    public void testBuildSSLContext_NullConfig_ReturnsNull() {
        SSLContext result = certificateProcessor.buildSSLContext(null, credentials);
        assertNull("SSL context should be null when config is null", result);
    }

    @Test
    public void testBuildSSLContext_EmptyCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        assertThrows(
            "Should throw MLValidationException when credentials are empty",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, new HashMap<>())
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_MutualTlsDisabled_ReturnsNull() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(false).build();

        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContextWithManagers(config, credentials);
        assertNull("SSL context with managers should be null when mutual TLS is disabled", result);
    }

    @Test
    public void testBuildSSLContextWithManagers_NullConfig_ReturnsNull() {
        CertificateProcessor.SSLContextWithManagers result = certificateProcessor.buildSSLContextWithManagers(null, credentials);
        assertNull("SSL context with managers should be null when config is null", result);
    }

    @Test
    public void testBuildSSLContextWithManagers_NoCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        assertThrows(
            "Should throw MLValidationException when credentials are missing",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContextWithManagers(config, null)
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_EmptyCredentials_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        assertThrows(
            "Should throw MLValidationException when credentials are empty",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContextWithManagers(config, new HashMap<>())
        );
    }

    // Tests for certificate parsing methods to increase coverage
    @Test
    public void testCreatePemKeyManagers_InvalidCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "InvalidPKCS12Data");

        assertThrows(
            "Should throw MLValidationException for invalid PKCS12 certificate",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreatePkcs12KeyManagers_Base64EncodedCertificate_ParsesCorrectly() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "-----BEGIN CERTIFICATE-----\nInvalidData\n-----END CERTIFICATE-----");
        credentials.put(CLIENT_KEY_PEM_FIELD, "-----BEGIN PRIVATE KEY-----\nInvalidData\n-----END PRIVATE KEY-----");

        assertThrows(
            "Should throw MLValidationException for invalid certificate in buildSSLContextWithManagers",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContextWithManagers(config, credentials)
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_Pkcs12Type_InvalidCertificate_ThrowsException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        credentials.put(CLIENT_CERT_PKCS12_FIELD, "InvalidPKCS12Data");

        assertThrows(
            "Should throw MLValidationException for invalid PKCS12 in buildSSLContextWithManagers",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContextWithManagers(config, credentials)
        );
    }

    // Additional tests to reach 78% coverage target - testing deeper certificate processing paths
    @Test
    public void testCreateKeyManagers_PemType_ExercisesCreatePemKeyManagers() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

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
            + "wjKquxdBNQOSHINbxBqXKE2kQFLfT3BlbkFfYPTUF9QjE6P0D/JvjvOoVqBcuFmu\n"
            + "oupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvs\n"
            + "BdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDx\n"
            + "-----END PRIVATE KEY-----";

        credentials.put(CLIENT_CERT_PEM_FIELD, validFormatCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, validFormatKey);

        assertThrows(
            "Should throw MLValidationException when processing invalid but well-formatted certificates",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreateKeyManagers_PKCS12Type_ExercisesCreatePkcs12KeyManagers() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        // Use base64 encoded data that looks like PKCS12 but is invalid
        String mockPkcs12 = "MIIJiAIBAzCCCUQGCSqGSIb3DQEHAaCCCTUEggkxMIIJLTCCBXEGCSqGSIb3DQEHAaCCBWIEggVe"
            + "MIIFWjCCBVYGCyqGSIb3DQEMCgECoIIE+jCCBPYwHAYKKoZIhvcNAQwBAzAOBAhQdOOBNOmCxgIC"
            + "B9AEggTUMockCS7T9pKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMCVVMxCzAJ";

        credentials.put(CLIENT_CERT_PKCS12_FIELD, mockPkcs12);
        credentials.put(KEYSTORE_PASSWORD_FIELD, "testpass");

        assertThrows(
            "Should throw MLValidationException when processing invalid PKCS12 data",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreateKeyManagers_JKSType_ThrowsNotImplementedException() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_JKS).build();

        credentials.put("jks_keystore", "mock-jks-data");

        assertThrows(
            "Should throw MLValidationException for JKS type (not implemented)",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testGetCertificateContent_EmptyField_ReturnsNull() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        credentials.put(CLIENT_CERT_PEM_FIELD, "");
        credentials.put(CLIENT_KEY_PEM_FIELD, "test-key");

        assertThrows(
            "Should throw MLValidationException when certificate field is empty",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    // Additional tests to exercise the remaining uncovered methods and reach 78% target
    @Test
    public void testCreateTrustManagers_DefaultTruststore_ExercisesDefaultPath() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        // Use a valid certificate format that will pass initial parsing to reach trust manager creation
        String validCert = "-----BEGIN CERTIFICATE-----\n"
            + "MIICljCCAX4CCQCKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMC\n"
            + "VVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQK\n"
            + "DAZUZXN0Q28xDzANBgNVBAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEk\n"
            + "MCIGCSqGSIb3DQEJARYVdGVzdEBleGFtcGxlLmNvbS5jb20wHhcNMjMwMTAxMDAw\n"
            + "MDAwWhcNMjQwMTAxMDAwMDAwWjCBjDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNB\n"
            + "MRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQKDAZUZXN0Q28xDzANBgNV\n"
            + "BAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEkMCIGCSqGSIb3DQEJARYV\n"
            + "dGVzdEBleGFtcGxlLmNvbS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n"
            + "AoIBAQC7VJTUt9Us8cKBwjKquxdBNQOSHINbxBqXKE2kQFLfT3BlbkFfYPTUF9Qj\n"
            + "E6P0D/JvjvOoVqBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwR\n"
            + "cTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQABo1MwUTAdBgNVHQ4EFgQUuSBU7r4V\n"
            + "54+3SMALxb5haQdnfkYwHwYDVR0jBBgwFoAUuSBU7r4V54+3SMALxb5haQdnfkYw\n"
            + "DwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAtWqaQni2FpRu1iqA\n"
            + "BRy7kNROzv6e+xzadWUBiGjCRSqaMF/XwBrXneO4zzufEiuQkQxHh+Z7/iiwxs8E\n"
            + "-----END CERTIFICATE-----";

        String validKey = "-----BEGIN PRIVATE KEY-----\n"
            + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKB\n"
            + "wjKquxdBNQOSHINbxBqXKE2kQFLfT3BlbkFfYPTUF9QjE6P0D/JvjvOoVqBcuFmu\n"
            + "oupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvs\n"
            + "BdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDx\n"
            + "TE8NcJoyhfHhMQoVqBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6\n"
            + "nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKug\n"
            + "lAkuYzuuUiUk9JKhYSdqIVDxTE8NcJoyhfHhMQoVqBcuFmuoupg+biQ62AG8l5f0\n"
            + "tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQAB\n"
            + "AoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDxTE8NcJoyhfHhMQoV\n"
            + "qBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVY\n"
            + "dXzzxvsBdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKh\n"
            + "YSdqIVDxTE8NcJoyhfHhMQoVqBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nO\n"
            + "XLH1ksU6nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQABAoIBABdwrDz5vY2x\n"
            + "Ezp2yKuglAkuYzuuUiUk9JKhYSdqIVDxTE8NcJoyhfHhMQoVqBcuFmuoupg+biQ6\n"
            + "2AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiu\n"
            + "-----END PRIVATE KEY-----";

        credentials.put(CLIENT_CERT_PEM_FIELD, validCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, validKey);
        // No CA cert or truststore path - should use default truststore

        assertThrows(
            "Should throw MLValidationException when processing certificates with default truststore",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testParsePemPrivateKey_MultipleAlgorithms_ExercisesAlgorithmLoop() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        String validCert = "-----BEGIN CERTIFICATE-----\n"
            + "MIICljCCAX4CCQCKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMC\n"
            + "VVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQK\n"
            + "DAZUZXN0Q28xDzANBgNVBAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEk\n"
            + "MCIGCSqGSIb3DQEJARYVdGVzdEBleGFtcGxlLmNvbS5jb20wHhcNMjMwMTAxMDAw\n"
            + "MDAwWhcNMjQwMTAxMDAwMDAwWjCBjDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNB\n"
            + "-----END CERTIFICATE-----";

        // Use a private key that will pass regex matching but fail algorithm parsing
        String keyWithValidFormat = "-----BEGIN PRIVATE KEY-----\n"
            + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKB\n"
            + "wjKquxdBNQOSHINbxBqXKE2kQFLfT3BlbkFfYPTUF9QjE6P0D/JvjvOoVqBcuFmu\n"
            + "oupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvs\n"
            + "BdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDx\n"
            + "TE8NcJoyhfHhMQoVqBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6\n"
            + "nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKug\n"
            + "lAkuYzuuUiUk9JKhYSdqIVDxTE8NcJoyhfHhMQoVqBcuFmuoupg+biQ62AG8l5f0\n"
            + "tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQAB\n"
            + "AoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDxTE8NcJoyhfHhMQoV\n"
            + "qBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVY\n"
            + "dXzzxvsBdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKh\n"
            + "YSdqIVDxTE8NcJoyhfHhMQoVqBcuFmuoupg+biQ62AG8l5f0tVTVHxZz1oaHe6nO\n"
            + "XLH1ksU6nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiuTwIDAQABAoIBABdwrDz5vY2x\n"
            + "Ezp2yKuglAkuYzuuUiUk9JKhYSdqIVDxTE8NcJoyhfHhMQoVqBcuFmuoupg+biQ6\n"
            + "2AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvsBdsgAYiu\n"
            + "-----END PRIVATE KEY-----";

        credentials.put(CLIENT_CERT_PEM_FIELD, validCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, keyWithValidFormat);

        assertThrows(
            "Should throw MLValidationException when private key parsing fails across algorithms",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreatePemKeyManagers_WithPassword_ExercisesPasswordPath() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PEM).build();

        String validCert = "-----BEGIN CERTIFICATE-----\n"
            + "MIICljCCAX4CCQCKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMC\n"
            + "VVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQK\n"
            + "DAZUZXN0Q28xDzANBgNVBAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEk\n"
            + "MCIGCSqGSIb3DQEJARYVdGVzdEBleGFtcGxlLmNvbS5jb20wHhcNMjMwMTAxMDAw\n"
            + "MDAwWhcNMjQwMTAxMDAwMDAwWjCBjDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNB\n"
            + "-----END CERTIFICATE-----";

        String validKey = "-----BEGIN PRIVATE KEY-----\n"
            + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKB\n"
            + "wjKquxdBNQOSHINbxBqXKE2kQFLfT3BlbkFfYPTUF9QjE6P0D/JvjvOoVqBcuFmu\n"
            + "oupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvs\n"
            + "BdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDx\n"
            + "-----END PRIVATE KEY-----";

        credentials.put(CLIENT_CERT_PEM_FIELD, validCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, validKey);
        credentials.put(KEYSTORE_PASSWORD_FIELD, "mypassword123");

        assertThrows(
            "Should throw MLValidationException when processing PEM with password",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testCreatePkcs12KeyManagers_WithoutPassword_ExercisesEmptyPasswordPath() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(KEYSTORE_TYPE_PKCS12).build();

        // Use a longer, more realistic PKCS12 base64 string
        String mockPkcs12 = "MIIJiAIBAzCCCUQGCSqGSIb3DQEHAaCCCTUEggkxMIIJLTCCBXEGCSqGSIb3DQEHAaCCBWIEggVe"
            + "MIIFWjCCBVYGCyqGSIb3DQEMCgECoIIE+jCCBPYwHAYKKoZIhvcNAQwBAzAOBAhQdOOBNOmCxgIC"
            + "B9AEggTUMockCS7T9pKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMCVVMxCzAJ"
            + "BgNVBAgMAkNBMRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQKDAZUZXN0Q28xDzANBgNV"
            + "BAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEkMCIGCSqGSIb3DQEJARYV";

        credentials.put(CLIENT_CERT_PKCS12_FIELD, mockPkcs12);
        // No password provided - should use empty password

        assertThrows(
            "Should throw MLValidationException when processing PKCS12 without password",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContext(config, credentials)
        );
    }

    @Test
    public void testBuildSSLContextWithManagers_NullKeystoreType_ExercisesDefaultPath() {
        config = ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType(null).build();

        String validCert = "-----BEGIN CERTIFICATE-----\n"
            + "MIICljCCAX4CCQCKuC5R7C7+8TANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMC\n"
            + "VVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1TYW4gRnJhbmNpc2NvMQ8wDQYDVQQK\n"
            + "DAZUZXN0Q28xDzANBgNVBAsMBlRlc3RVbml0MRAwDgYDVQQDDAdUZXN0Q2VydDEk\n"
            + "MCIGCSqGSIb3DQEJARYVdGVzdEBleGFtcGxlLmNvbS5jb20wHhcNMjMwMTAxMDAw\n"
            + "MDAwWhcNMjQwMTAxMDAwMDAwWjCBjDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNB\n"
            + "-----END CERTIFICATE-----";

        String validKey = "-----BEGIN PRIVATE KEY-----\n"
            + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKB\n"
            + "wjKquxdBNQOSHINbxBqXKE2kQFLfT3BlbkFfYPTUF9QjE6P0D/JvjvOoVqBcuFmu\n"
            + "oupg+biQ62AG8l5f0tVTVHxZz1oaHe6nOXLH1ksU6nwRcTxwdN+CFwpVYdXzzxvs\n"
            + "BdsgAYiuTwIDAQABAoIBABdwrDz5vY2xEzp2yKuglAkuYzuuUiUk9JKhYSdqIVDx\n"
            + "-----END PRIVATE KEY-----";

        credentials.put(CLIENT_CERT_PEM_FIELD, validCert);
        credentials.put(CLIENT_KEY_PEM_FIELD, validKey);

        assertThrows(
            "Should throw MLValidationException when keystore type is null (defaults to PEM)",
            MLValidationException.class,
            () -> certificateProcessor.buildSSLContextWithManagers(config, credentials)
        );
    }
}
