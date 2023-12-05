package org.opensearch.ml.engine.credentials;

import com.amazonaws.auth.AWSCredentialsProvider;

public interface CredentialsProvider {
    AWSCredentialsProvider getCredentialsProvider(String region, String roleArn);
}
