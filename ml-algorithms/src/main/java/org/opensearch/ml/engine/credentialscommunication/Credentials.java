package org.opensearch.ml.engine.credentialscommunication;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.opensearch.ml.engine.credentials.aws.InternalAwsCredentials;
import org.opensearch.ml.engine.factory.CredentialsFactory;

public class Credentials {
    /**
     * Retrieves the credentials for the given credentialsRequest through ExpirableCredentialsProviderFactory class
     *
     */
    public static InternalAwsCredentials getCredentials(CredentialsRequest credentialsRequest) throws IOException {
        return AccessController.doPrivileged((PrivilegedAction<InternalAwsCredentials>) () -> {
            CredentialsFactory credentialsProviderFactory = new CredentialsFactory();
            return credentialsProviderFactory.getAWSCredentialsProvider(credentialsRequest);
        });
    }
}
