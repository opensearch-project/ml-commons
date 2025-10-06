/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

import org.opensearch.core.action.ActionListener;

public interface Encryptor {

    /**
     * Takes plaintext and returns encrypted text.
     *
     * @param plainText plainText.
     * @param tenantId id of the tenant
     * @return String encryptedText.
     */
    void encrypt(String plainText, String tenantId, ActionListener<String> listener);

    /**
     * Takes encryptedText and returns plain text.
     *
     * @param encryptedText encryptedText.
     * @param tenantId id of the tenant
     * @return String plainText.
     */
    void decrypt(String encryptedText, String tenantId, ActionListener<String> listener);

    /**
     * Set up the masterKey for dynamic updating
     * @param tenantId ID of the tenant
     * @param masterKey masterKey to be set.
     */
    void setMasterKey(String tenantId, String masterKey);

    /**
     * Get the masterKey
     * @param tenantId ID of the tenant
     */
    String getMasterKey(String tenantId);

    String generateMasterKey();

}
