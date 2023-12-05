package org.opensearch.ml.engine.credentials.aws;

import com.amazonaws.auth.AWSCredentialsProvider;

/**
 * Interface which enables to plug in multiple Credentials Providers
 */
public interface CredentialsProviderFactory {
    public AWSCredentialsProvider getProvider(String roleArn);
}
