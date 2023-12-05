package org.opensearch.ml.engine.credentialscommunication;

import java.util.regex.Pattern;

import org.opensearch.common.ValidationException;
import org.opensearch.core.common.Strings;

public class Util {

    private Util() {}

    public static final Pattern SECRET_ARN_REGEX = Pattern
        .compile("^arn:aws(-[^:]+)?:secretsmanager:([a-zA-Z0-9-]+):([0-9]{12}):secret:([a-zA-Z0-9-/_+=@.,]+)$");
    public static final Pattern IAM_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:iam::([0-9]{12}):([a-zA-Z0-9-/_+=@.,]+)$");

    public static String getRegionFromSecretArn(String secretArn) {
        if (isValidSecretManagerArn(secretArn)) {
            return secretArn.split(":")[3];
        }
        throw new IllegalArgumentException("Unable to retrieve region from secretARN " + secretArn);
    }

    public static boolean isValidIAMArn(String arn) {
        return Strings.hasLength(arn) && IAM_ARN_REGEX.matcher(arn).find();
    }

    public static boolean isValidSecretManagerArn(String secretArn) throws ValidationException {
        return Strings.hasLength(secretArn) && SECRET_ARN_REGEX.matcher(secretArn).find();
    }

    public static boolean isValidContentType(String contentType) {
        return contentType.equals("application/json");
    }

    public static boolean isValidAWSService(String serviceName) {
        return (serviceName.equalsIgnoreCase("sagemaker") || serviceName.equalsIgnoreCase("bedrock"));
    }
}
