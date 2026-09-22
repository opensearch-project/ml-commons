/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MUTUAL_TLS_ENABLED;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.ConnectorAction;
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

    // ---- validateMutualTlsEnabled ------------------------------------------

    public void testMutualTlsEnabled_nullAndDisabledInputsAreIgnored() {
        // Nothing to gate: these requests do not ask for mTLS, so the flag must not be consulted at all.
        // An update that leaves client_config alone has to keep working while the flag is off.
        ConnectorProtocolValidator.validateMutualTlsEnabled(null, mlFeatureEnabledSetting);
        ConnectorProtocolValidator.validateMutualTlsEnabled(mtls(null), mlFeatureEnabledSetting);
        ConnectorProtocolValidator.validateMutualTlsEnabled(mtls(false), mlFeatureEnabledSetting);
        verify(mlFeatureEnabledSetting, never()).isMutualTlsEnabled();
    }

    public void testMutualTlsEnabled_rejectedWhenFlagOff() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(false);
        OpenSearchStatusException e = expectThrows(
            OpenSearchStatusException.class,
            () -> ConnectorProtocolValidator.validateMutualTlsEnabled(mtls(true), mlFeatureEnabledSetting)
        );
        assertEquals(RestStatus.FORBIDDEN, e.status());
        assertTrue(e.getMessage().contains(ML_COMMONS_MUTUAL_TLS_ENABLED.getKey()));
    }

    public void testMutualTlsEnabled_allowedWhenFlagOn() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(true);
        ConnectorProtocolValidator.validateMutualTlsEnabled(mtls(true), mlFeatureEnabledSetting);
    }

    /** The flag is off by default, so a cluster that never set it rejects mTLS. */
    public void testMutualTlsEnabled_defaultIsDisabled() {
        assertFalse(ML_COMMONS_MUTUAL_TLS_ENABLED.get(Settings.EMPTY));
    }

    /**
     * An update replaces client_config wholesale, so an operator editing an unrelated field has to re-send
     * mutual_tls_enabled to keep it. Rejecting that would make an existing mutual-TLS connector uneditable as
     * soon as the setting is turned off, instead of just preventing new reliance on the control.
     */
    public void testMutualTlsEnabled_storedValueIsCarriedForwardWhileFlagOff() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(false);
        ConnectorProtocolValidator.validateMutualTlsEnabledAfterUpdate(mtls(true), mtls(true), mlFeatureEnabledSetting);
    }

    /** Turning it on for a connector that did not have it is what the setting is there to stop. */
    public void testMutualTlsEnabled_newlyEnablingOnAnExistingConnectorIsRejected() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(false);
        for (ConnectorClientConfig stored : new ConnectorClientConfig[] { null, mtls(null), mtls(false) }) {
            OpenSearchStatusException e = expectThrows(
                OpenSearchStatusException.class,
                () -> ConnectorProtocolValidator.validateMutualTlsEnabledAfterUpdate(stored, mtls(true), mlFeatureEnabledSetting)
            );
            assertEquals(RestStatus.FORBIDDEN, e.status());
        }
    }

    /** Create has no stored connector, so it delegates with a null stored config and cannot be grandfathered. */
    public void testMutualTlsEnabled_createIsNeverGrandfathered() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(false);
        expectThrows(
            OpenSearchStatusException.class,
            () -> ConnectorProtocolValidator.validateMutualTlsEnabled(mtls(true), mlFeatureEnabledSetting)
        );
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

    /**
     * Grandfathering covers edits that leave the protocol alone, not moving an already-bad connector onto a
     * different protocol that also cannot apply mutual TLS - that target state is created by the request.
     */
    public void testMutualTlsAfterUpdate_rejectsMoveBetweenUnsupportedProtocols() {
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator
                .validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(true), ConnectorProtocols.GOOGLE_CLOUD, null)
        );
        assertTrue(e.getMessage().contains(ConnectorProtocols.GOOGLE_CLOUD));
    }

    /** Restating the stored protocol is not a move, so it stays grandfathered. */
    public void testMutualTlsAfterUpdate_allowsRestatingSameUnsupportedProtocol() {
        ConnectorProtocolValidator
            .validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(true), ConnectorProtocols.AWS_SIGV4, null);
    }

    /** A legacy bad connector can still be moved onto http, which does apply mutual TLS. */
    public void testMutualTlsAfterUpdate_allowsMoveFromUnsupportedProtocolToHttp() {
        ConnectorProtocolValidator
            .validateMutualTlsSupportedAfterUpdate(ConnectorProtocols.AWS_SIGV4, mtls(true), ConnectorProtocols.HTTP, null);
    }

    // ---- validateMutualTlsScheme -------------------------------------------

    private static List<ConnectorAction> actions(String... urls) {
        List<ConnectorAction> actions = new ArrayList<>();
        for (String url : urls) {
            actions.add(new ConnectorAction(ConnectorAction.ActionType.PREDICT, null, "POST", url, null, "{}", null, null));
        }
        return actions;
    }

    public void testMutualTlsScheme_nullAndDisabledInputsAreIgnored() {
        ConnectorProtocolValidator.validateMutualTlsScheme(null, mtls(true));
        ConnectorProtocolValidator.validateMutualTlsScheme(actions("http://host/predict"), null);
        // Without mutual TLS the scheme is not this check's business - a cleartext connector is a separate
        // concern, governed by the trusted-endpoint allowlist.
        ConnectorProtocolValidator.validateMutualTlsScheme(actions("http://host/predict"), mtls(false));
        ConnectorProtocolValidator.validateMutualTlsScheme(actions("http://host/predict"), mtls(null));
    }

    public void testMutualTlsScheme_allowedOnHttps() {
        ConnectorProtocolValidator.validateMutualTlsScheme(actions("https://host/predict"), mtls(true));
    }

    public void testMutualTlsScheme_rejectedOnCleartextUrl() {
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator.validateMutualTlsScheme(actions("http://host/predict"), mtls(true))
        );
        // The offending URL is named, so an operator can tell which action to fix.
        assertTrue(e.getMessage().contains("http://host/predict"));
        assertTrue(e.getMessage().contains(ConnectorClientConfig.MUTUAL_TLS_ENABLED_FIELD));
    }

    public void testMutualTlsScheme_schemeMatchIgnoresCaseAndLeadingWhitespace() {
        expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator.validateMutualTlsScheme(actions("  HTTP://host/predict"), mtls(true))
        );
    }

    /** The scheme of a substituted URL is not knowable until predict time, so it is left alone. */
    public void testMutualTlsScheme_unresolvedSubstitutionIsLeftAlone() {
        ConnectorProtocolValidator.validateMutualTlsScheme(actions("${parameters.endpoint}/predict"), mtls(true));
    }

    /** https on predict does not excuse a cleartext hop on another action. */
    public void testMutualTlsScheme_rejectsCleartextInAnyAction() {
        expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator.validateMutualTlsScheme(actions("https://host/predict", "http://host/batch"), mtls(true))
        );
    }

    public void testMutualTlsSchemeAfterUpdate_rejectsNewlyIntroducedCleartextUrl() {
        expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator
                .validateMutualTlsSchemeAfterUpdate(actions("https://host/predict"), mtls(true), actions("http://host/predict"), null)
        );
    }

    /** Turning mutual TLS on over an already-stored cleartext URL is the request creating the state. */
    public void testMutualTlsSchemeAfterUpdate_rejectsTurningMutualTlsOnOverStoredCleartextUrl() {
        expectThrows(
            IllegalArgumentException.class,
            () -> ConnectorProtocolValidator
                .validateMutualTlsSchemeAfterUpdate(actions("http://host/predict"), mtls(false), null, mtls(true))
        );
    }

    /**
     * An unrelated edit to a connector created before this check existed is not blocked: it has to re-send
     * client_config to keep mutual_tls_enabled, and rejecting that would make the connector uneditable.
     */
    public void testMutualTlsSchemeAfterUpdate_allowsEditOfPreExistingCleartextCombination() {
        ConnectorProtocolValidator.validateMutualTlsSchemeAfterUpdate(actions("http://host/predict"), mtls(true), null, mtls(true));
    }

    public void testMutualTlsSchemeAfterUpdate_allowsSwitchToHttps() {
        ConnectorProtocolValidator
            .validateMutualTlsSchemeAfterUpdate(actions("http://host/predict"), mtls(true), actions("https://host/predict"), null);
    }

    public void testMutualTlsSchemeAfterUpdate_noChangeIsAllowed() {
        ConnectorProtocolValidator.validateMutualTlsSchemeAfterUpdate(actions("https://host/predict"), mtls(true), null, null);
    }
}
