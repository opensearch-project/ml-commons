/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.Map;

public class OciConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void constructor_NullCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        OciConnector.ociConnectorBuilder().protocol(ConnectorProtocols.OCI_SIGV1).build();
    }

    @Test
    public void constructor_NullAuthType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing auth type");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(new HashMap<>())
                .build();
    }

    @Test
    public void constructor_WrongAuthType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong OCI client auth type");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "UNKNOWN_PRINCIPAL"))
                .build();
    }

    @Test
    public void validateCredential_MissingTenantIdForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing tenant id");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "USER_PRINCIPAL"))
                .build();
    }

    @Test
    public void validateCredential_MissingUserIdForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing user id");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                                OciConnector.TENANT_ID_FIELD, "tenantId"))
                .build();
    }

    @Test
    public void validateCredential_MissingFingerprintForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing fingerprint");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                                OciConnector.TENANT_ID_FIELD, "tenantId",
                                OciConnector.USER_ID_FIELD, "userId"))
                .build();
    }

    @Test
    public void validateCredential_MissingPemfileForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing pemfile");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                                OciConnector.TENANT_ID_FIELD, "tenantId",
                                OciConnector.USER_ID_FIELD, "userId",
                                OciConnector.FINGERPRINT_FIELD, "fingerprint"))
                .build();
    }

    @Test
    public void validateCredential_MissingRegionForUserPrincipal() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing region");
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                                OciConnector.TENANT_ID_FIELD, "tenantId",
                                OciConnector.USER_ID_FIELD, "userId",
                                OciConnector.FINGERPRINT_FIELD, "fingerprint",
                                OciConnector.PEMFILE_PATH_FIELD, "pemfile"))
                .build();
    }

    @Test
    public void validateCredential_ResourcePrincipal() {
        OciConnector
                .ociConnectorBuilder()
                .protocol(ConnectorProtocols.OCI_SIGV1)
                .parameters(
                        Map.of(
                                OciConnector.AUTH_TYPE_FIELD, "RESOURCE_PRINCIPAL"))
                .build();
    }

    @Test
    public void cloneConnector() {
        final OciConnector ociConnector=
                OciConnector
                        .ociConnectorBuilder()
                        .protocol(ConnectorProtocols.OCI_SIGV1)
                        .parameters(
                                Map.of(
                                        OciConnector.AUTH_TYPE_FIELD, "USER_PRINCIPAL",
                                        OciConnector.TENANT_ID_FIELD, "tenantId",
                                        OciConnector.USER_ID_FIELD, "userId",
                                        OciConnector.FINGERPRINT_FIELD, "fingerprint",
                                        OciConnector.PEMFILE_PATH_FIELD, "pemfile",
                                        OciConnector.REGION_FIELD, "uk-london-1"))
                        .build();

        final OciConnector clonedConnector = (OciConnector) ociConnector.cloneConnector();
        Assert.assertEquals(ConnectorProtocols.OCI_SIGV1, clonedConnector.getProtocol());
    }
}