package org.opensearch.ml.engine.credentials.aws;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.util.EC2MetadataUtils;

/**
 * Factory class that provides temporary credentials. It refreshes the credentials on demand.
 */
public class ExpirableCredentialsProviderFactory implements CredentialsProviderFactory {

    public ExpirableCredentialsProviderFactory(InternalAuthCredentialsClient internalAuthCredentialsClient, String[] clusterNameTuple) {
        this.internalAuthCredentialsClient = internalAuthCredentialsClient;
        this.clusterNameTuple = clusterNameTuple;
    }

    /**
     * Provide expirable credentials.
     *
     * @param roleArn IAM role arn
     * @return AWSCredentialsProvider which holds the credentials.
     */
    @Override
    public AWSCredentialsProvider getProvider(String roleArn) {
        return getExpirableCredentialsProvider(roleArn);
    }

    private static final Logger logger = LogManager.getLogger(ExpirableCredentialsProviderFactory.class);

    private final InternalAuthCredentialsClient internalAuthCredentialsClient;
    private final String[] clusterNameTuple;

    private AWSCredentialsProvider getExpirableCredentialsProvider(String roleArn) {
        return findStsAssumeRoleCredentialsProvider(roleArn);
    }

    private AWSCredentialsProvider findStsAssumeRoleCredentialsProvider(String roleArn) {
        AWSCredentialsProvider assumeRoleApiCredentialsProvider = getAssumeRoleApiCredentialsProvider();

        if (assumeRoleApiCredentialsProvider != null) {
            logger.info("Fetching credentials from STS for assumed role");
            return getStsAssumeCustomerRoleProvider(assumeRoleApiCredentialsProvider, roleArn);
        }
        logger.info("Could not fetch credentials from internal service to assume role");
        return null;
    }

    private AWSCredentialsProvider getAssumeRoleApiCredentialsProvider() {
        InternalAuthApiCredentialsProvider internalAuthApiCredentialsProvider = new InternalAuthApiCredentialsProvider(
            internalAuthCredentialsClient,
            InternalAuthApiCredentialsProvider.POLICY_TYPES.get("ASSUME_ROLE")
        );
        return internalAuthApiCredentialsProvider.getCredentials() != null ? internalAuthApiCredentialsProvider : null;
    }

    private AWSCredentialsProvider getStsAssumeCustomerRoleProvider(AWSCredentialsProvider apiCredentialsProvider, String roleArn) {
        String region = "us-east-1";
        try {
            region = EC2MetadataUtils.getEC2InstanceRegion();
        } catch (Exception ex) {
            logger.info("Exception occurred while fetching the region info from EC2 metadata. Defaulting to us-east-1");
        }

        final ClientConfiguration configurationWithConfusedDeputyHeaders = ClientConfigurationHelper
            .getConfusedDeputyConfiguration(clusterNameTuple, region);
        AWSSecurityTokenServiceClientBuilder stsClientBuilder = AWSSecurityTokenServiceClientBuilder
            .standard()
            .withCredentials(apiCredentialsProvider)
            .withClientConfiguration(configurationWithConfusedDeputyHeaders)
            .withRegion(region);
        AWSSecurityTokenService stsClient = stsClientBuilder.build();
        STSAssumeRoleSessionCredentialsProvider.Builder providerBuilder = new STSAssumeRoleSessionCredentialsProvider.Builder(
            roleArn,
            "ml-commons"
        ).withStsClient(stsClient);
        return new PrivilegedCredentialsProvider(providerBuilder.build());
    }
}
