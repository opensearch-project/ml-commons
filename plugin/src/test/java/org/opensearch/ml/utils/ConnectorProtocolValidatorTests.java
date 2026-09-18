/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.test.OpenSearchTestCase;

public class ConnectorProtocolValidatorTests extends OpenSearchTestCase {

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private static ConnectorClientConfig mtls(Boolean enabled) {
        return ConnectorClientConfig.builder().mutualTlsEnabled(enabled).build();
    }

    // ---- validateProtocolEnabled -------------------------------------------

    public void testProtocolEnabled_nullProtocolIsIgnored() {
        // A request that does not set a protocol cannot move the connector into a gated one.
        ConnectorProtocolValidator.validateProtocolEnabled(null, mlFeatureEnabledSetting);
    }

    public void testProtocolEnabled_ungatedProtocolAlwaysAllowed() {
        ConnectorProtocolValidator.validateProtocolEnabled(ConnectorProtocols.HTTP, mlFeatureEnabledSetting);
        ConnectorProtocolValidator.validateProtocolEnabled(ConnectorProtocols.AWS_SIGV4, mlFeatureEnabledSetting);
    }

    public void testProtocolEnabled_vertexAiRejectedWhenDisabled() {
        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(false);
        OpenSearchStatusException e = expectThrows(
            OpenSearchStatusException.class,
            () -> ConnectorProtocolValidator.validateProtocolEnabled(ConnectorProtocols.GOOGLE_CLOUD, mlFeatureEnabledSetting)
        );
        assertEquals(RestStatus.FORBIDDEN, e.status());
    }

    public void testProtocolEnabled_vertexAiAllowedWhenEnabled() {
        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(true);
        ConnectorProtocolValidator.validateProtocolEnabled(ConnectorProtocols.GOOGLE_CLOUD, mlFeatureEnabledSetting);
    }

    /** Both MCP protocols are gated by the same flag; gating only one of them leaves the flag bypassable. */
    public void testProtocolEnabled_bothMcpProtocolsRejectedWhenDisabled() {
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(false);
        for (String protocol : new String[] { ConnectorProtocols.MCP_SSE, ConnectorProtocols.MCP_STREAMABLE_HTTP }) {
            OpenSearchStatusException e = expectThrows(
                OpenSearchStatusException.class,
                () -> ConnectorProtocolValidator.validateProtocolEnabled(protocol, mlFeatureEnabledSetting)
            );
            assertEquals(RestStatus.FORBIDDEN, e.status());
        }
    }

    public void testProtocolEnabled_bothMcpProtocolsAllowedWhenEnabled() {
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);
        ConnectorProtocolValidator.validateProtocolEnabled(ConnectorProtocols.MCP_SSE, mlFeatureEnabledSetting);
        ConnectorProtocolValidator.validateProtocolEnabled(ConnectorProtocols.MCP_STREAMABLE_HTTP, mlFeatureEnabledSetting);
    }

    // ---- validateMutualTlsSupported ----------------------------------------

    public void testMutualTls_nullAndDisabledInputsAreIgnored() {
        ConnectorProtocolValidator.validateMutualTlsSupported(null, mtls(true));
        ConnectorProtocolValidator.validateMutualTlsSupported(ConnectorProtocols.AWS_SIGV4, null);
        ConnectorProtocolValidator.validateMutualTlsSupported(ConnectorProtocols.AWS_SIGV4, mtls(null));
        ConnectorProtocolValidator.validateMutualTlsSupported(ConnectorProtocols.AWS_SIGV4, mtls(false));
    }

    public void testMutualTls_allowedOnHttp() {
        ConnectorProtocolValidator.validateMutualTlsSupported(ConnectorProtocols.HTTP, mtls(true));
    }

    public void testMutualTls_rejectedOnProtocolsThatNeverApplyIt() {
        for (String protocol : new String[] {
            ConnectorProtocols.AWS_SIGV4,
            ConnectorProtocols.GOOGLE_CLOUD,
            ConnectorProtocols.MCP_SSE,
            ConnectorProtocols.MCP_STREAMABLE_HTTP }) {
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> ConnectorProtocolValidator.validateMutualTlsSupported(protocol, mtls(true))
            );
            assertTrue(e.getMessage().contains("Mutual TLS is not supported"));
        }
    }

    // ---- validateMutualTlsSupportedAfterUpdate -----------------------------

    /**
     * The combination can be created by switching the protocol alone, leaving an already-stored
     * mutual_tls_enabled stranded on a protocol that cannot apply it. Checking only the values the request
     * carries would miss this.
     */
    public void testMutualTlsAfterUpdate_rejectsProtocolSwitchThatStrandsStoredMutualTls() {
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator
                .validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.HTTP, mtls(true), ConnectorProtocols.AWS_SIGV4, null)
        );
        assertTrue(e.getMessage().contains("Mutual TLS is not supported"));
    }

    public void testMutualTlsAfterUpdate_rejectsNewlyEnabledMutualTlsOnStoredUnsupportedProtocol() {
        expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator
                .validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(false), null, mtls(true))
        );
    }

    /** An unrelated edit to a connector that already stores the unsupported combination must not be blocked. */
    public void testMutualTlsAfterUpdate_allowsEditOfPreExistingUnsupportedCombination() {
        ConnectorProtocolValidator.validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(true), null, mtls(true));
        ConnectorProtocolValidator.validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(true), null, null);
    }

    public void testMutualTlsAfterUpdate_allowsSwitchToHttp() {
        ConnectorProtocolValidator
            .validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(false), ConnectorProtocols.HTTP, mtls(true));
    }

    public void testMutualTlsAfterUpdate_noChangeIsAllowed() {
        ConnectorProtocolValidator.validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.HTTP, mtls(true), null, null);
    }
}
