package org.opensearch.ml.engine.factory;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.engine.credentials.aws.ExpirableCredentialsProviderFactory;
import org.opensearch.ml.engine.credentials.aws.InternalAuthCredentialsClient;
import org.opensearch.ml.engine.credentials.aws.InternalAuthCredentialsClientPool;
import org.opensearch.ml.engine.credentials.aws.InternalAwsCredentials;
import org.opensearch.ml.engine.credentials.aws.PrivilegedCredentialsProvider;
import org.opensearch.ml.engine.credentialscommunication.CredentialsRequest;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;

public class CredentialsFactory {
    private static final Logger logger = LogManager.getLogger(CredentialsFactory.class);

    private final InternalAuthCredentialsClient internalApiCredentialsClient;

    /*
     * Mapping between IAM roleArn and AWSCredentialsProvider. Each role will have its own credentials.
     */
    Map<String, AWSCredentialsProvider> roleClientMap = new HashMap<>();

    public CredentialsFactory() {
        this.internalApiCredentialsClient = InternalAuthCredentialsClientPool.getInstance().getInternalAuthClient(getClass().getName());
    }

    /**
     * Fetches the client corresponding to an IAM role
     *
     * @return AmazonSNS AWS SNS client
     */
    public InternalAwsCredentials getAWSCredentialsProvider(CredentialsRequest credentialsRequest) {
        AWSCredentialsProvider credentialsProvider;
        String roleArn = credentialsRequest.getRoleArn();
        String clusterName = credentialsRequest.getClusterName();
        if (!roleClientMap.containsKey(roleArn)) {
            credentialsProvider = getProvider(roleArn, clusterName);
            roleClientMap.put(roleArn, credentialsProvider);
        }
        AWSCredentialsProvider awsCredentialsProvider = roleClientMap.get(roleArn);
        PrivilegedCredentialsProvider privilegedCredentialsProvider = (PrivilegedCredentialsProvider) awsCredentialsProvider;
        BasicSessionCredentials basic = (BasicSessionCredentials) privilegedCredentialsProvider.getCredentials();
        InternalAwsCredentials apiCredentials = new InternalAwsCredentials(
            basic.getAWSAccessKeyId(),
            basic.getAWSSecretKey(),
            basic.getSessionToken(),
            0
        );
        return apiCredentials;
    }

    /**
     * @param roleArn
     * @return AWSCredentialsProvider
     * @throws IllegalArgumentException
     */
    public AWSCredentialsProvider getProvider(String roleArn, String clusterName) throws IllegalArgumentException {
        org.opensearch.ml.engine.credentials.aws.CredentialsProviderFactory providerSource = new ExpirableCredentialsProviderFactory(
            internalApiCredentialsClient,
            clusterName.split(":")
        );
        return providerSource.getProvider(roleArn);
    }
}
