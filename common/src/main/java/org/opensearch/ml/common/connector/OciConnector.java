/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_SIGV1;

/**
 * Connector to OCI services
 */
@Log4j2
@NoArgsConstructor
@EqualsAndHashCode
@org.opensearch.ml.common.annotation.Connector(OCI_SIGV1)
public class OciConnector extends HttpConnector {
    public static final String AUTH_TYPE_FIELD = "auth_type";

    public static final String TENANT_ID_FIELD = "tenant_id";

    public static final String USER_ID_FIELD = "user_id";

    public static final String FINGERPRINT_FIELD = "fingerprint";

    public static final String PEMFILE_PATH_FIELD = "pemfile_path";

    public static final String REGION_FIELD = "region";

    @Builder(builderMethodName = "ociConnectorBuilder")
    public OciConnector(String name, String description, String version, String protocol,
                        Map<String, String> parameters, Map<String, String> credential, List<ConnectorAction> actions,
                        List<String> backendRoles, AccessMode accessMode, User owner) {
        super(name, description, version, protocol, parameters, credential, actions, backendRoles, accessMode, owner);
        validate();
    }

    public OciConnector(String protocol, XContentParser parser) throws IOException {
        super(protocol, parser);
        validate();
    }


    public OciConnector(StreamInput input) throws IOException {
        super(input);
        validate();
    }

    public OciConnector(String protocol, StreamInput input) throws IOException {
        super(protocol, input);
        validate();
    }

    private void validate() {
        if (parameters == null) {
            throw new IllegalArgumentException("Missing credential");
        }
        if (!parameters.containsKey(AUTH_TYPE_FIELD)) {
            throw new IllegalArgumentException("Missing auth type");
        }

        final OciClientAuthType authType =
                OciClientAuthType.from(
                        parameters.get(AUTH_TYPE_FIELD).toUpperCase(Locale.ROOT));

        // User principal requires a few additional information provided through
        // parameters
        // For instance principal and resource principal, all required information
        // are provided as part of environment variables and file systems (provided
        // when provisioning OCI compute instances) Hence, they do not require passing
        // more parameters like user principal.
        if (authType == OciClientAuthType.USER_PRINCIPAL) {
            if (!parameters.containsKey(TENANT_ID_FIELD)) {
                throw new IllegalArgumentException("Missing tenant id");
            }

            if (!parameters.containsKey(USER_ID_FIELD)) {
                throw new IllegalArgumentException("Missing user id");
            }

            if (!parameters.containsKey(FINGERPRINT_FIELD)) {
                throw new IllegalArgumentException("Missing fingerprint");
            }

            if (!parameters.containsKey(PEMFILE_PATH_FIELD)) {
                throw new IllegalArgumentException("Missing pemfile");
            }

            if (!parameters.containsKey(REGION_FIELD)) {
                throw new IllegalArgumentException("Missing region");
            }
        }
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            final StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new OciConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The type of authentication supported by OCI. For more details please visit doc
     * https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/Content/API/Concepts/sdk_authentication_methods.htm
     *
     * <p> Session Token-Based Authentication is not supported since it has a short lifetime, and
     * it requires manual refresh through user manual login
     */
    public enum OciClientAuthType {
        RESOURCE_PRINCIPAL,
        INSTANCE_PRINCIPAL,
        USER_PRINCIPAL; // Also known as API Key-based authentication

        public static OciClientAuthType from(String value) {
            try {
                return OciClientAuthType.valueOf(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong OCI client auth type");
            }
        }
    }
}