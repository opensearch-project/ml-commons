/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import lombok.extern.log4j.Log4j2;

/**
 * Manages AWS credentials for Bedrock AgentCore integration.
 * Follows the same pattern as HttpConnector credential management.
 */
@Log4j2
public class BedrockAgentCoreCredentialManager {

    private Map<String, String> encryptedCredentials;
    private Map<String, String> decryptedCredentials;
    private final BiFunction<String, String, String> encryptFunction;
    private final BiFunction<String, String, String> decryptFunction;

    public BedrockAgentCoreCredentialManager(
        BiFunction<String, String, String> encryptFunction,
        BiFunction<String, String, String> decryptFunction
    ) {
        this.encryptFunction = encryptFunction;
        this.decryptFunction = decryptFunction;
    }

    /**
     * Set credentials and encrypt them for storage
     */
    public void setCredentials(Map<String, String> credentials, String tenantId) {
        Map<String, String> encrypted = new HashMap<>();
        if (credentials != null) {
            // Encrypt credentials following HttpConnector pattern
            for (Map.Entry<String, String> entry : credentials.entrySet()) {
                String encryptedValue = encryptFunction.apply(entry.getValue(), tenantId);
                encrypted.put(entry.getKey(), encryptedValue);
            }
        }
        this.encryptedCredentials = encrypted;
        log.info("Encrypted {} credentials for Bedrock AgentCore", credentials != null ? credentials.size() : 0);
    }

    /**
     * Decrypt credentials for runtime use
     */
    public void decryptCredentials(String tenantId) {
        if (encryptedCredentials == null) {
            this.decryptedCredentials = new HashMap<>();
            return;
        }

        // Decrypt credentials following HttpConnector pattern
        Map<String, String> decrypted = new HashMap<>();
        for (Map.Entry<String, String> entry : encryptedCredentials.entrySet()) {
            String decryptedValue = decryptFunction.apply(entry.getValue(), tenantId);
            decrypted.put(entry.getKey(), decryptedValue);
        }
        this.decryptedCredentials = decrypted;
        log.info("Decrypted {} credentials for Bedrock AgentCore", decrypted.size());
    }

    /**
     * Get decrypted credentials for AWS SDK
     */
    public Map<String, String> getDecryptedCredentials() {
        return decryptedCredentials != null ? decryptedCredentials : new HashMap<>();
    }

    /**
     * Get encrypted credentials for storage
     */
    public Map<String, String> getEncryptedCredentials() {
        return encryptedCredentials != null ? encryptedCredentials : new HashMap<>();
    }

    /**
     * Get AWS access key
     */
    public String getAccessKey() {
        return decryptedCredentials != null ? decryptedCredentials.get("access_key") : null;
    }

    /**
     * Get AWS secret key
     */
    public String getSecretKey() {
        return decryptedCredentials != null ? decryptedCredentials.get("secret_key") : null;
    }

    /**
     * Get AWS session token (for temporary credentials)
     */
    public String getSessionToken() {
        return decryptedCredentials != null ? decryptedCredentials.get("session_token") : null;
    }

    /**
     * Get AWS region
     */
    public String getRegion() {
        return decryptedCredentials != null ? decryptedCredentials.get("region") : "us-west-2";
    }
}
