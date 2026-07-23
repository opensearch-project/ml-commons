/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.opensearch.ml.common.exception.MLValidationException;

import lombok.extern.log4j.Log4j2;

/**
 * Certificate processor for handling different certificate formats and building SSL contexts
 * for mutual TLS authentication in ML connectors.
 */
@Log4j2
public class CertificateProcessor {

    // Certificate field names in credentials (direct content)
    public static final String CLIENT_CERT_PEM_FIELD = "client_cert_pem";
    public static final String CLIENT_KEY_PEM_FIELD = "client_key_pem";
    public static final String CLIENT_CERT_PKCS12_FIELD = "client_cert_pkcs12";
    public static final String CA_CERT_PEM_FIELD = "ca_cert_pem";
    public static final String KEYSTORE_PASSWORD_FIELD = "keystore_password";

    // Allowed credential fields for certificate-only authentication
    // When mTLS is enabled, only these fields are permitted to prevent mixed authentication methods
    private static final Set<String> CERTIFICATE_ONLY_CREDENTIAL_FIELDS = Set
        .of(CLIENT_CERT_PEM_FIELD, CLIENT_KEY_PEM_FIELD, CLIENT_CERT_PKCS12_FIELD, CA_CERT_PEM_FIELD, KEYSTORE_PASSWORD_FIELD);

    /**
     * Supported keystore types for certificate processing
     */
    public enum KeystoreType {
        PEM("PEM"),
        PKCS12("PKCS12");

        private final String value;

        KeystoreType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Parse keystore type from string input (case-insensitive)
         * @param input The string input to parse
         * @return The corresponding KeystoreType
         * @throws MLValidationException if the input is not a supported keystore type
         */
        public static KeystoreType from(String input) {
            if (input == null) {
                return PEM; // Default to PEM
            }

            String normalized = input.trim().toUpperCase();
            for (KeystoreType type : values()) {
                if (type.getValue().equals(normalized)) {
                    return type;
                }
            }

            throw new MLValidationException("Unsupported keystore type: " + input + ". Supported types are: PEM, PKCS12");
        }

        @Override
        public String toString() {
            return value;
        }
    }

    // PEM patterns - support certificate chains by matching multiple certificate blocks
    // Updated to handle comments after PEM headers (e.g., "-----BEGIN CERTIFICATE----- # comment")
    private static final Pattern CERT_PATTERN = Pattern
        .compile("-----BEGIN CERTIFICATE-----[^\\r\\n]*\\s*([A-Za-z0-9+/\\s=]+?)\\s*-----END CERTIFICATE-----", Pattern.DOTALL);
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern
        .compile("-----BEGIN PRIVATE KEY-----[^\\r\\n]*\\s*([A-Za-z0-9+/\\s=]+?)\\s*-----END PRIVATE KEY-----", Pattern.DOTALL);

    // Patterns used to heuristically detect a filesystem path mistakenly passed in place of certificate
    // content - deliberately narrow (path prefix or known cert/key extension) to avoid misclassifying
    // malformed base64 PEM bodies, which legitimately contain "/" characters, as file paths.
    private static final Pattern FILE_PATH_PREFIX_PATTERN = Pattern.compile("^(?:[A-Za-z]:[\\\\/]|~[/\\\\]|\\.{1,2}[/\\\\]|/).*");
    private static final Pattern FILE_PATH_EXTENSION_PATTERN = Pattern
        .compile(".*\\.(pem|crt|cer|key|p12|pfx|der)$", Pattern.CASE_INSENSITIVE);

    /**
     * Builds an SSL context for mutual TLS authentication based on the connector configuration
     * and decrypted credentials. Returns both the SSL context and the managers for direct use.
     *
     * @param config The connector client configuration
     * @param decryptedCredentials The decrypted credentials containing certificate data
     * @return SSLContextWithManagers containing SSL context and managers, or null if mTLS is not enabled
     * @throws MLValidationException if certificate processing fails
     */
    public SSLContextWithManagers buildSSLContext(ConnectorClientConfig config, Map<String, String> decryptedCredentials) {
        if (config == null || !Boolean.TRUE.equals(config.getMutualTlsEnabled())) {
            return null;
        }

        if (decryptedCredentials == null || decryptedCredentials.isEmpty()) {
            throw new MLValidationException("Decrypted credentials are required for mutual TLS");
        }

        try {
            KeystoreType keystoreType = KeystoreType.from(config.getKeystoreType());

            KeyManager[] keyManagers = createKeyManagers(keystoreType, decryptedCredentials);
            TrustManager[] trustManagers = createTrustManagers(config, decryptedCredentials);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);

            log.debug("Successfully built SSL context for mutual TLS with keystore type: {}", keystoreType);
            return new SSLContextWithManagers(sslContext, keyManagers, trustManagers);

        } catch (Exception e) {
            log.error("Failed to build SSL context for mutual TLS", e);
            throw new MLValidationException("Failed to build SSL context for mutual TLS: " + e.getMessage(), e);
        }
    }

    /**
     * Container class for SSL context and its managers
     */
    public static class SSLContextWithManagers {
        private final SSLContext sslContext;
        private final KeyManager[] keyManagers;
        private final TrustManager[] trustManagers;

        public SSLContextWithManagers(SSLContext sslContext, KeyManager[] keyManagers, TrustManager[] trustManagers) {
            this.sslContext = sslContext;
            this.keyManagers = keyManagers;
            this.trustManagers = trustManagers;
        }

        public SSLContext getSslContext() {
            return sslContext;
        }

        public KeyManager[] getKeyManagers() {
            return keyManagers;
        }

        public TrustManager[] getTrustManagers() {
            return trustManagers;
        }
    }

    /**
     * Creates key managers based on the keystore type and credentials.
     */
    private KeyManager[] createKeyManagers(KeystoreType keystoreType, Map<String, String> credentials) throws Exception {
        switch (keystoreType) {
            case PEM:
                return createPemKeyManagers(credentials);
            case PKCS12:
                return createPkcs12KeyManagers(credentials);
            default:
                throw new MLValidationException("Unsupported keystore type: " + keystoreType);
        }
    }

    /**
     * Creates key managers from PEM format certificates and private keys.
     */
    private KeyManager[] createPemKeyManagers(Map<String, String> credentials) throws Exception {
        String certPem = getCertificateContent(credentials, CLIENT_CERT_PEM_FIELD);
        String keyPem = getCertificateContent(credentials, CLIENT_KEY_PEM_FIELD);
        String password = credentials.get(KEYSTORE_PASSWORD_FIELD);

        if (certPem == null || keyPem == null) {
            throw new MLValidationException(
                "Both client certificate and private key are required for PEM keystore. " + "Provide client_cert_pem and client_key_pem"
            );
        }

        // Parse the full certificate chain (leaf + any intermediates) so the complete chain is
        // presented to the peer during the TLS handshake, not just the leaf certificate
        X509Certificate[] certificateChain = parsePemCertificateChain(certPem);
        validateChainOrder(certificateChain);

        // Parse private key
        PrivateKey privateKey = parsePemPrivateKey(keyPem);

        // Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        char[] keyPassword = password != null ? password.toCharArray() : new char[0];
        try {
            keyStore.setKeyEntry("client", privateKey, keyPassword, certificateChain);
            // Create key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPassword);

            return kmf.getKeyManagers();
        } finally {
            // Best-effort: Clear password char array from memory
            // Note: Original password String remains in heap until GC due to Java string immutability
            Arrays.fill(keyPassword, '\0');
        }
    }

    /**
     * Creates key managers from PKCS12 format certificates.
     */
    private KeyManager[] createPkcs12KeyManagers(Map<String, String> credentials) throws Exception {
        String pkcs12Data = getCertificateContent(credentials, CLIENT_CERT_PKCS12_FIELD);
        String password = credentials.get(KEYSTORE_PASSWORD_FIELD);

        if (pkcs12Data == null) {
            throw new MLValidationException("PKCS12 certificate is required for PKCS12 keystore. " + "Provide client_cert_pkcs12");
        }

        // Decode the base64 content
        byte[] keystoreBytes = Base64.getMimeDecoder().decode(pkcs12Data);
        char[] keystorePassword = password != null ? password.toCharArray() : new char[0];
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(keystoreBytes), keystorePassword);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword);

            return kmf.getKeyManagers();
        } finally {
            // Best-effort: Clear password char array from memory
            // Note: Original password String remains in heap until GC due to Java string immutability
            Arrays.fill(keystorePassword, '\0');
        }
    }

    /**
     * Creates trust managers for certificate validation.
     */
    private TrustManager[] createTrustManagers(ConnectorClientConfig config, Map<String, String> credentials) throws Exception {
        String caCertPem = getCertificateContent(credentials, CA_CERT_PEM_FIELD);

        // If no custom CA certificate is provided, use system default
        if (caCertPem == null) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // Use system default truststore
            return tmf.getTrustManagers();
        }

        // Create custom truststore with the provided CA certificate(s) - supports bundles with
        // intermediate/root certificates, not just a single CA certificate
        X509Certificate[] caCertificates = parsePemCertificateChain(caCertPem);

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        for (int i = 0; i < caCertificates.length; i++) {
            trustStore.setCertificateEntry("ca-" + i, caCertificates[i]);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    /**
     * Parses all PEM certificate blocks in the input and returns them as an ordered chain
     * (leaf certificate first, followed by any intermediate certificates present).
     */
    private X509Certificate[] parsePemCertificateChain(String pemCert) throws CertificateException {
        Matcher matcher = CERT_PATTERN.matcher(pemCert.trim());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>();

        while (matcher.find()) {
            // Use MIME decoder for better compatibility with various PEM formats
            String base64Cert = matcher.group(1).replaceAll("\\s", "");
            byte[] certBytes = Base64.getMimeDecoder().decode(base64Cert);
            chain.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes)));
        }

        if (chain.isEmpty()) {
            if (looksLikeFilePath(pemCert)) {
                throw new CertificateException(
                    "File paths are not supported for certificate fields. Provide the certificate content itself "
                        + "(PEM text or base64-encoded PEM), not a file path."
                );
            }
            throw new CertificateException("Invalid PEM certificate format. Expected -----BEGIN CERTIFICATE----- block.");
        }

        return chain.toArray(new X509Certificate[0]);
    }

    /**
     * Validates that a client certificate chain is ordered leaf-first: each certificate's issuer
     * must match the subject of the next certificate in the array. Without this check, a misordered
     * chain fails later with an opaque "java.security.KeyStoreException: Certificate chain is not
     * valid" from KeyStore#setKeyEntry, which gives no indication that ordering is the problem.
     */
    private void validateChainOrder(X509Certificate[] chain) throws CertificateException {
        for (int i = 0; i < chain.length - 1; i++) {
            if (!chain[i].getIssuerX500Principal().equals(chain[i + 1].getSubjectX500Principal())) {
                throw new CertificateException(
                    "Client certificate chain is not ordered correctly. Certificate at index "
                        + i
                        + " (subject: "
                        + chain[i].getSubjectX500Principal()
                        + ") is not issued by the certificate at index "
                        + (i + 1)
                        + " (subject: "
                        + chain[i + 1].getSubjectX500Principal()
                        + "). List the leaf certificate first, followed by its issuing intermediate "
                        + "certificates in order."
                );
            }
        }
    }

    /**
     * Heuristic check for whether certificate/key field content is actually a filesystem path rather
     * than PEM content - file-based certificate loading is not supported, so this exists only to give
     * a clearer error message for that specific mistake. Requires a path-like prefix or a known
     * certificate/key file extension; both patterns are anchored with .matches() and use "." without
     * DOTALL, so multi-line input (e.g. malformed base64 PEM bodies, which may contain "/" characters)
     * can never satisfy them and is not misdiagnosed as a file path.
     */
    private boolean looksLikeFilePath(String content) {
        String trimmed = content.trim();
        if (trimmed.contains("-----BEGIN")) {
            return false;
        }
        return FILE_PATH_PREFIX_PATTERN.matcher(trimmed).matches() || FILE_PATH_EXTENSION_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Parses a PEM format private key.
     * Only supports PKCS#8 format (-----BEGIN PRIVATE KEY-----) for security and compatibility.
     *
     * Note: PKCS#1 RSA format (-----BEGIN RSA PRIVATE KEY-----) is not supported.
     * To convert PKCS#1 to PKCS#8, use:
     * openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in rsa_key.pem -out pkcs8_key.pem
     */
    private PrivateKey parsePemPrivateKey(String pemKey) throws Exception {
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(pemKey.trim());
        if (!matcher.find()) {
            if (looksLikeFilePath(pemKey)) {
                throw new InvalidKeySpecException(
                    "File paths are not supported for certificate fields. Provide the private key content itself "
                        + "(PEM text or base64-encoded PEM), not a file path."
                );
            }
            throw new InvalidKeySpecException(
                "Invalid PEM private key format. Only PKCS#8 format is supported (-----BEGIN PRIVATE KEY-----). "
                    + "If you have a PKCS#1 RSA key (-----BEGIN RSA PRIVATE KEY-----), convert it using: "
                    + "openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in rsa_key.pem -out pkcs8_key.pem"
            );
        }

        String base64Key = matcher.group(1).replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        // Try different key algorithms
        String[] algorithms = { "RSA", "EC", "DSA" };
        Exception lastException = null;
        for (String algorithm : algorithms) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                return keyFactory.generatePrivate(keySpec);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                lastException = e;
                // Try next algorithm
            }
        }

        throw new InvalidKeySpecException("Unable to parse private key with supported algorithms (RSA, EC, DSA)", lastException);
    }

    /**
     * Gets certificate content from direct field with automatic base64 decoding.
     *
     * @param credentials The credentials map
     * @param contentField The field name for direct content
     * @return The certificate content, or null if field is not present
     */
    private String getCertificateContent(Map<String, String> credentials, String contentField) {
        String directContent = credentials.get(contentField);
        if (directContent != null && !directContent.trim().isEmpty()) {
            // For PKCS12 fields, return the base64 content directly without PEM validation
            if (CLIENT_CERT_PKCS12_FIELD.equals(contentField)) {
                // PKCS12 is a binary format, so we just return the base64 content as-is
                // The createPkcs12KeyManagers method will handle the base64 decoding
                return directContent.trim();
            }

            // Check if content is base64 encoded (no PEM headers) for PEM fields
            if (isBase64EncodedContent(directContent)) {
                // Decode base64 and validate it's actually PEM content
                try {
                    byte[] decodedBytes = Base64.getDecoder().decode(directContent.trim());
                    String decodedContent = new String(decodedBytes);

                    // Security: Validate decoded content contains PEM headers
                    if (decodedContent.contains("-----BEGIN")) {
                        log.debug("Successfully decoded and validated base64 certificate content for field: {}", contentField);
                        return decodedContent;
                    } else {
                        // Hard error: base64-detected content that doesn't decode to valid PEM
                        throw new MLValidationException(
                            String
                                .format(
                                    "Certificate field '%s' appears to be base64 encoded but does not contain valid PEM content after decoding. "
                                        + "Expected PEM headers (-----BEGIN...) but found: %s",
                                    contentField,
                                    decodedContent.length() > 100 ? decodedContent.substring(0, 100) + "..." : decodedContent
                                )
                        );
                    }
                } catch (IllegalArgumentException e) {
                    // Hard error: malformed base64
                    throw new MLValidationException(
                        String
                            .format(
                                "Certificate field '%s' appears to be base64 encoded but contains invalid base64 data: %s",
                                contentField,
                                e.getMessage()
                            )
                    );
                } catch (Exception e) {
                    // Hard error: other decoding failures
                    throw new MLValidationException(
                        String.format("Failed to decode base64 certificate content for field '%s': %s", contentField, e.getMessage())
                    );
                }
            }
            return directContent;
        }

        return null;
    }

    /**
     * Checks if the content appears to be base64 encoded (no PEM headers).
     */
    private boolean isBase64EncodedContent(String content) {
        String trimmed = content.trim();

        // If it has PEM headers, it's not base64-only content
        if (trimmed.contains("-----BEGIN") || trimmed.contains("-----END")) {
            return false;
        }

        // Check if it looks like base64 (only base64 characters and reasonable length)
        // Strip whitespace before matching to handle line-wrapped base64
        String strippedContent = trimmed.replaceAll("\\s", "");
        if (strippedContent.length() > 100 && strippedContent.matches("^[A-Za-z0-9+/]*={0,2}$")) {
            return true;
        }

        return false;
    }

    /**
     * Validates certificate configuration before processing.
     */
    public void validateCertificateConfig(ConnectorClientConfig config, Map<String, String> credentials) {
        if (!Boolean.TRUE.equals(config.getMutualTlsEnabled())) {
            return; // No validation needed if mTLS is disabled
        }

        if (Boolean.TRUE.equals(config.getSkipSslVerification())) {
            throw new MLValidationException(
                "skip_ssl_verification cannot be enabled together with mutual_tls_enabled. Disabling server "
                    + "certificate validation while presenting a client certificate defeats the purpose of mutual "
                    + "TLS and allows man-in-the-middle attacks. Provide a trusted CA certificate (ca_cert_pem) "
                    + "instead of skipping SSL verification."
            );
        }

        if (credentials == null || credentials.isEmpty()) {
            throw new MLValidationException("Credentials are required when mutual TLS is enabled");
        }

        KeystoreType keystoreType = KeystoreType.from(config.getKeystoreType());

        switch (keystoreType) {
            case PEM:
                boolean hasPemContent = credentials.containsKey(CLIENT_CERT_PEM_FIELD) && credentials.containsKey(CLIENT_KEY_PEM_FIELD);
                if (!hasPemContent) {
                    throw new MLValidationException("For PEM keystore, provide both client_cert_pem and client_key_pem");
                }
                break;
            case PKCS12:
                boolean hasPkcs12Content = credentials.containsKey(CLIENT_CERT_PKCS12_FIELD);
                if (!hasPkcs12Content) {
                    throw new MLValidationException("For PKCS12 keystore, provide client_cert_pkcs12");
                }
                break;
            default:
                throw new MLValidationException("Unsupported keystore type: " + keystoreType);
        }
    }

    /**
     * Checks if the connector is configured for certificate-only authentication.
     * This means mutual TLS is enabled and no traditional API keys are present.
     *
     * @param config The connector client configuration
     * @param credentials The connector credentials
     * @return true if using certificate-only authentication
     */
    public boolean isCertificateOnlyAuthentication(ConnectorClientConfig config, Map<String, String> credentials) {
        if (!Boolean.TRUE.equals(config.getMutualTlsEnabled())) {
            return false;
        }

        if (credentials == null) {
            return false;
        }

        // Check if certificate credentials are present
        boolean hasCertificates = hasCertificateCredentials(config, credentials);

        // When mTLS is enabled, only certificate-related fields are allowed
        // Any other credential field indicates mixed authentication methods
        for (String credentialKey : credentials.keySet()) {
            if (!CERTIFICATE_ONLY_CREDENTIAL_FIELDS.contains(credentialKey)) {
                // Return false for mixed authentication instead of throwing exception
                // The validateCertificateOnlyAuthentication method should be used for validation with exceptions
                log.debug("Mixed authentication detected: found non-certificate credential '{}' when mTLS is enabled", credentialKey);
                return false;
            }
        }

        return hasCertificates;
    }

    /**
     * Checks if certificate credentials are present based on keystore type.
     */
    private boolean hasCertificateCredentials(ConnectorClientConfig config, Map<String, String> credentials) {
        try {
            KeystoreType keystoreType = KeystoreType.from(config.getKeystoreType());

            switch (keystoreType) {
                case PEM:
                    boolean hasPemContent = credentials.containsKey(CLIENT_CERT_PEM_FIELD) && credentials.containsKey(CLIENT_KEY_PEM_FIELD);
                    return hasPemContent;
                case PKCS12:
                    return credentials.containsKey(CLIENT_CERT_PKCS12_FIELD);
                default:
                    return false;
            }
        } catch (MLValidationException e) {
            // If keystore type is unsupported, return false instead of throwing exception
            // This allows isCertificateOnlyAuthentication to return false gracefully
            log.debug("Unsupported keystore type '{}', returning false for certificate credentials check", config.getKeystoreType());
            return false;
        }
    }

    /**
     * Validates that certificate-only authentication is properly configured.
     * This ensures no conflicting authentication methods are present.
     */
    public void validateCertificateOnlyAuthentication(ConnectorClientConfig config, Map<String, String> credentials) {
        if (!Boolean.TRUE.equals(config.getMutualTlsEnabled())) {
            return;
        }

        if (credentials == null) {
            return;
        }

        boolean hasCertificates = hasCertificateCredentials(config, credentials);
        boolean hasApiKey = credentials.containsKey("api_key")
            && credentials.get("api_key") != null
            && !credentials.get("api_key").trim().isEmpty();

        // Strictly enforce certificate-only authentication - no mixed authentication methods
        if (hasCertificates && hasApiKey) {
            throw new MLValidationException(
                "Mixed authentication methods are not allowed. "
                    + "When using mutual TLS with client certificates, API keys should not be provided. "
                    + "Please remove the 'api_key' from credentials when 'mutual_tls_enabled' is true."
            );
        }

        if (!hasCertificates) {
            throw new MLValidationException("Client certificates are required when mutual TLS is enabled");
        }

        log.debug("Certificate-only authentication validated successfully - no conflicting authentication methods found");
    }

    /**
     * Resolves mutual TLS configuration by consolidating validation and SSL context creation.
     * This method combines validateCertificateConfig, validateCertificateOnlyAuthentication,
     * and buildSSLContext into a single call to reduce credential parsing overhead.
     *
     * @param config The connector client configuration
     * @param credentials The decrypted credentials containing certificate data
     * @return SSLContextWithManagers containing SSL context and managers, or null if mTLS is disabled
     * @throws MLValidationException if certificate processing or validation fails
     */
    public SSLContextWithManagers resolveMtls(ConnectorClientConfig config, Map<String, String> credentials) {
        // Return null early if mTLS is not enabled
        if (config == null || !Boolean.TRUE.equals(config.getMutualTlsEnabled())) {
            return null;
        }

        // Perform all validations in one pass
        validateCertificateConfig(config, credentials);
        validateCertificateOnlyAuthentication(config, credentials);

        // Build and return SSL context with managers
        return buildSSLContext(config, credentials);
    }
}
