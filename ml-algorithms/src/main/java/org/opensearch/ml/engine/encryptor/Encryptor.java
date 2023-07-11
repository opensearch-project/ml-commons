/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

import java.security.SecureRandom;
import java.util.Base64;

public interface Encryptor {

    /**
     * Takes plaintext and returns encrypted text.
     *
     * @param plainText plainText.
     * @return String encryptedText.
     */
    String encrypt(String plainText);

    /**
     * Takes encryptedText and returns plain text.
     *
     * @param encryptedText encryptedText.
     * @return String plainText.
     */
    String decrypt(String encryptedText);

    /**
     * Set up the masterKey for dynamic updating
     *
     * @param masterKey masterKey to be set.
     */
    void setMasterKey(String masterKey);

    String generateMasterKey();

}
