package org.opensearch.ml.engine.factory;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.engine.credentials.aws.CredentialsProviderFactory;
import org.opensearch.ml.engine.credentials.aws.ExpirableCredentialsProviderFactory;
import org.opensearch.ml.engine.credentials.aws.InternalAuthCredentialsClient;
import org.opensearch.ml.engine.credentials.aws.InternalAuthCredentialsClientPool;
import org.opensearch.ml.engine.credentialscommunication.SecretManagerCredentials;
import org.opensearch.ml.engine.credentialscommunication.Util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final public class SecretsManagerFactory {
    private static final Logger logger = LogManager.getLogger(SecretsManagerFactory.class);

    private final InternalAuthCredentialsClient internalApiCredentialsClient;

    /*
     * Mapping between IAM roleArn and SecretManagerClientHelper. Each role will have its own credentials.
     */
    Map<String, SecretManagerClientHelper> roleClientMap = new HashMap<>();

    public SecretsManagerFactory() {
        this.internalApiCredentialsClient = InternalAuthCredentialsClientPool.getInstance().getInternalAuthClient(getClass().getName());
    }

    public JsonObject getSecrets(SecretManagerCredentials secretCredentials) {
        try {
            AWSSecretsManager secretsManager = getClient(secretCredentials);
            GetSecretValueRequest secretsRequest = new GetSecretValueRequest();
            secretsRequest.setSecretId(secretCredentials.getSecretArn());
            GetSecretValueResult secretValueResponse = secretsManager.getSecretValue(secretsRequest);
            JsonObject jsonObject = JsonParser.parseString(secretValueResponse.getSecretString()).getAsJsonObject();
            return jsonObject;
        } catch (Exception ex) {
            logger.error("Exception getting secrets from SecretManager", ex);
            throw ex;
        }
    }

    /**
     * Fetches the client corresponding to an IAM role
     *
     * @return AWSSecretsManager AWS SecretsManager client
     */
    public AWSSecretsManager getClient(SecretManagerCredentials secretCredentials) {
        AWSCredentialsProvider credentialsProvider;
        String roleArn = secretCredentials.getRoleArn();
        String clusterName = secretCredentials.getClusterName();
        if (!roleClientMap.containsKey(roleArn)) {
            credentialsProvider = getProvider(roleArn, clusterName);
            roleClientMap.put(roleArn, new SecretManagerClientHelper(credentialsProvider));
        }

        AWSSecretsManager secretsManagerClient = roleClientMap
            .get(roleArn)
            .getSecretManagerClient(Util.getRegionFromSecretArn(secretCredentials.getSecretArn()));
        return secretsManagerClient;
    }

    /**
     * @param roleArn
     * @return AWSCredentialsProvider
     * @throws IllegalArgumentException
     */
    public AWSCredentialsProvider getProvider(String roleArn, String clusterName) throws IllegalArgumentException {

        CredentialsProviderFactory providerSource = new ExpirableCredentialsProviderFactory(
            internalApiCredentialsClient,
            clusterName.split(":")
        );
        return providerSource.getProvider(roleArn);
    }
}

/**
 * This helper class caches the credentials for a role and creates client
 * for each AWS region based on the topic ARN
 */
class SecretManagerClientHelper {
    private AWSCredentialsProvider credentialsProvider;
    // Map between Region and client
    private Map<String, AWSSecretsManager> secretManagerClientMap = new HashMap();

    SecretManagerClientHelper(AWSCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public AWSSecretsManager getSecretManagerClient(String region) {
        if (!secretManagerClientMap.containsKey(region)) {
            AWSSecretsManager secretsManagerClient = AWSSecretsManagerClientBuilder
                .standard()
                .withRegion(region)
                .withCredentials(credentialsProvider)
                .build();
            secretManagerClientMap.put(region, secretsManagerClient);
        }
        return secretManagerClientMap.get(region);
    }
}
