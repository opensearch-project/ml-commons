/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.jce.JceMasterKey;
import org.opensearch.ml.engine.exceptions.MetaDataException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptorImpl implements Encryptor {

    private volatile String masterKey;

    public EncryptorImpl(String masterKey) {
        this.masterKey = masterKey;
    }

    @Override
    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    @Override
    public String encrypt(String plainText) {
        checkMasterKey();
        final AwsCrypto crypto = AwsCrypto.builder()
                .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
                .build();

        JceMasterKey jceMasterKey
                = JceMasterKey.getInstance(new SecretKeySpec(masterKey.getBytes(), "AES"), "Custom", "",
                "AES/GCM/NoPadding");

        final CryptoResult<byte[], JceMasterKey> encryptResult = crypto.encryptData(jceMasterKey,
                plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptResult.getResult());
    }

    @Override
    public String decrypt(String encryptedText) {
        checkMasterKey();
        final AwsCrypto crypto = AwsCrypto.builder()
                .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
                .build();

        JceMasterKey jceMasterKey
                = JceMasterKey.getInstance(new SecretKeySpec(masterKey.getBytes(), "AES"), "Custom", "",
                "AES/GCM/NoPadding");

        final CryptoResult<byte[], JceMasterKey> decryptedResult
                = crypto.decryptData(jceMasterKey, Base64.getDecoder().decode(encryptedText));
        return new String(decryptedResult.getResult());
    }

    private void checkMasterKey() {
        if (masterKey == "0000000000000000" || masterKey == null) {
            throw new MetaDataException("Please provide a masterKey for credential encryption! Example: PUT /_cluster/settings\n" +
                    "{\n" +
                    "  \"persistent\" : {\n" +
                    "    \"plugins.ml_commons.encryption.master_key\" : \"1234567x\"  \n" +
                    "  }\n" +
                    "}");
        }
    }
}
