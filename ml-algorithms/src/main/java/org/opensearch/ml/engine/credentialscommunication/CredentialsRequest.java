package org.opensearch.ml.engine.credentialscommunication;

import org.opensearch.core.common.Strings;

public class CredentialsRequest {
    private String clusterName;
    private String roleArn;

    public CredentialsRequest(final String roleArn, final String clusterName) {
        if (Strings.isNullOrEmpty(roleArn) || !Util.isValidIAMArn(roleArn)) {
            throw new IllegalArgumentException("Role arn is missing/invalid: " + roleArn);
        }
        this.roleArn = roleArn;
        this.clusterName = clusterName;
    }

    @Override
    public String toString() {
        return "RoleArn: " + roleArn + ", ClusterName: " + clusterName;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getClusterName() {
        return clusterName;
    }
}
