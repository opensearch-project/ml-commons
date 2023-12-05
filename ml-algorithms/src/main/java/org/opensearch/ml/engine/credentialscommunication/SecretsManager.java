package org.opensearch.ml.engine.credentialscommunication;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.opensearch.ml.engine.factory.SecretsManagerFactory;

import com.google.gson.JsonObject;

public class SecretsManager {

    /**
     * Retrieves the secretValue key pair mapping for the given requested secret
     *
     */
    public static JsonObject getSecretValue(SecretManagerCredentials secretManagerCredentials) throws IOException {
        return AccessController.doPrivileged((PrivilegedAction<JsonObject>) () -> {
            SecretsManagerFactory secretsManagerFactory = new SecretsManagerFactory();
            return secretsManagerFactory.getSecrets(secretManagerCredentials);
        });
    }
}
