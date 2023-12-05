package org.opensearch.ml.engine.credentialscommunication;

import org.opensearch.core.common.Strings;

public class SecretManagerCredentials {

    private String clusterName;
    private String roleArn;
    private String secretArn;

    public SecretManagerCredentials(final String roleArn, final String clusterName, final String secretArn) {

        if (Strings.isNullOrEmpty(roleArn) || !Util.isValidIAMArn(roleArn)) {
            throw new IllegalArgumentException("Role arn is missing/invalid: " + roleArn);
        }

        if (Strings.isNullOrEmpty(secretArn) || !Util.isValidSecretManagerArn(secretArn)) {
            throw new IllegalArgumentException("secret arn is missing/invalid: " + secretArn);
        }

        this.roleArn = roleArn;
        this.clusterName = clusterName;
        this.secretArn = secretArn;
    }

    @Override
    public String toString() {
        return "RoleARn: " + roleArn + ", ClusterName: " + clusterName + ", secretArn: " + secretArn;
    }

    public String getSecretArn() {
        return secretArn;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getClusterName() {
        return clusterName;
    }
}
