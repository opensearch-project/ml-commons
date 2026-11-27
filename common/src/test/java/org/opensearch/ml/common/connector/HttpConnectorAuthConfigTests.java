/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.FULL_OAUTH2_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.STORED_WITH_MARKERS_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.document;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullOauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.http;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parser;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.roundTrip;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.storedWithMarkersAsRead;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.toJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

public class HttpConnectorAuthConfigTests {

    private static final String ACTIONS = "[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://api.openai.com/v1/chat\","
        + "\"headers\":{\"api_key\":\"${credential.key}\"},\"request_body\":\"{\\\"input\\\":\\\"${parameters.input}\\\"}\"}]";
    private static final String CLIENT_CONFIG = "{\"max_connection\":30,\"connection_timeout\":30,\"read_timeout\":30}";

    /** Every stored read: parser.map(), gson, then the protocol's parser. */
    private static Connector readStored(String json) throws IOException {
        return Connector.createConnector(parser(json));
    }

    /** The stored-document constructor on the bytes as given, so the member order of {@code json} is kept. */
    private static HttpConnector readDirect(String json) throws IOException {
        return new HttpConnector(ConnectorProtocols.HTTP, parser(json));
    }

    private static List<String> keys(String json) {
        return new ArrayList<>(document(json).keySet());
    }

    private static MLCreateConnectorInput update(OAuth2AuthConfig authConfig, Map<String, String> parameters) {
        return MLCreateConnectorInput.builder().updateConnector(true).authConfig(authConfig).parameters(parameters).build();
    }

    private static String storedDocument(String authConfig) {
        return "{\"name\":\"c\",\"version\":\"1\",\"protocol\":\"http\",\"parameters\":{\"model\":\"x\"},\"auth_config\":"
            + authConfig
            + ",\"credential\":{\"key\":\"v\"},\"actions\":"
            + ACTIONS
            + ",\"backend_roles\":[\"r\"],\"access\":\"public\",\"client_config\":"
            + CLIENT_CONFIG
            + ",\"description\":\"after\"}";
    }

    private static void assertOtherFields(Connector connector) {
        assertEquals("c", connector.getName());
        assertEquals("after", ((AbstractConnector) connector).getDescription());
        assertEquals(1, connector.getActions().size());
        assertEquals("https://api.openai.com/v1/chat", connector.getActions().get(0).getUrl());
        assertEquals(Integer.valueOf(30), connector.getConnectorClientConfig().getMaxConnections());
    }

    private static void assertSameApartFromAuthConfig(HttpConnector expected, HttpConnector read) {
        assertEquals(expected.getName(), read.getName());
        assertEquals(expected.getVersion(), read.getVersion());
        assertEquals(expected.getDescription(), read.getDescription());
        assertEquals(expected.getProtocol(), read.getProtocol());
        assertEquals(expected.getParameters(), read.getParameters());
        assertEquals(expected.getCredential(), read.getCredential());
        assertEquals(expected.getActions(), read.getActions());
        assertEquals(expected.getBackendRoles(), read.getBackendRoles());
        assertEquals(expected.getAccess(), read.getAccess());
        assertEquals(expected.getConnectorClientConfig(), read.getConnectorClientConfig());
    }

    @Test
    public void storedReader_readsAConfigurationWithRemovalMarkersAndNumericStrings() throws IOException {
        String stored = storedDocument(STORED_WITH_MARKERS_JSON);
        for (Connector connector : new Connector[] { readStored(stored), readDirect(stored) }) {
            assertOtherFields(connector);
            assertEquals(storedWithMarkersAsRead(), connector.getAuthConfig());
            assertNull("readers never build a replacement", connector.getAuthConfig().getReplaced());
            assertEquals(Map.of("model", "x"), connector.getParameters());
        }
    }

    @Test
    public void storedReader_authConfigBeforeOrAfterParametersReadsTheSame() throws IOException {
        String before = "{\"name\":\"c\",\"auth_config\":{\"type\":\"none\"},\"parameters\":{\"model\":\"x\"}}";
        String after = "{\"name\":\"c\",\"parameters\":{\"model\":\"x\"},\"auth_config\":{\"type\":\"none\"}}";
        for (HttpConnector connector : new HttpConnector[] { readDirect(before), readDirect(after) }) {
            assertEquals(OAuth2AuthConfig.NONE, connector.getAuthConfig());
            assertEquals(Map.of("model", "x"), connector.getParameters());
        }
        // A connector stored without parameters.
        HttpConnector withoutParameters = readDirect("{\"name\":\"c\",\"auth_config\":{\"type\":\"none\"}}");
        assertEquals(OAuth2AuthConfig.NONE, withoutParameters.getAuthConfig());
        assertNull(withoutParameters.getParameters());
        // A value this release cannot read makes the connector unreadable.
        assertThrows(Exception.class, () -> readDirect("{\"name\":\"c\",\"auth_config\":{\"type\":\"custom\"}}"));
    }

    @Test
    public void toXContent_writesParametersAsBeforeAndAuthConfigRightAfterThem() throws IOException {
        HttpConnector connector = HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(Map.of("model", "x"))
            .credential(Map.of("key", "v"))
            .authConfig(fullOauth2())
            .build();
        String emitted = toJson(connector);
        assertEquals(List.of("name", "version", "protocol", "parameters", "auth_config", "credential"), keys(emitted));
        assertEquals(Map.of("model", "x"), document(emitted).get("parameters"));
        assertEquals(document(FULL_OAUTH2_JSON), document(emitted).get("auth_config"));

        // Without a configuration no member is written; HttpConnectorTest holds that document byte for byte.
        assertFalse(toJson(HttpConnectorTest.createHttpConnector()).contains("auth_config"));
        // Without parameters the field is still written.
        assertEquals(
            "{\"name\":\"c\",\"protocol\":\"http\",\"auth_config\":{\"type\":\"none\"}}",
            toJson(HttpConnector.builder().name("c").protocol(ConnectorProtocols.HTTP).authConfig(OAuth2AuthConfig.NONE).build())
        );
    }

    @Test
    public void getThenPutOfTheGetBodyThenGet_isUnchanged() throws IOException {
        Connector stored = readStored(storedDocument(STORED_WITH_MARKERS_JSON));
        String get = toJson(stored);
        assertFalse(get, get.contains("[]"));

        MLCreateConnectorInput put = MLCreateConnectorInput.parse(parser(get), true);
        stored.update(put);
        String getAgain = toJson(stored);

        assertEquals(get, getAgain);
        assertEquals(storedWithMarkersAsRead(), stored.getAuthConfig());
    }

    @Test
    public void update_replacesTheStoredConfigurationAndMarksTheMembersItDrops() throws IOException {
        HttpConnector connector = http(fullOauth2());
        connector.update(update(oauth2(), Map.of("model", "y")));
        assertEquals(oauth2(), connector.getAuthConfig());
        assertEquals(Map.of("model", "y"), connector.getParameters());
        assertEquals(toJson(oauth2().replacing(fullOauth2())), toJson(connector.getAuthConfig()));

        // none over an active configuration marks every member of it.
        connector.update(update(OAuth2AuthConfig.NONE, null));
        assertEquals(OAuth2AuthConfig.NONE, connector.getAuthConfig());
        assertEquals(toJson(OAuth2AuthConfig.NONE.replacing(oauth2())), toJson(connector.getAuthConfig()));
    }

    @Test
    public void update_withoutAuthConfigLeavesTheStoredOne() throws IOException {
        HttpConnector connector = http(fullOauth2());
        connector.update(update(null, Map.of("model", "y", "temperature", "0")));
        assertEquals(fullOauth2(), connector.getAuthConfig());
        assertEquals(FULL_OAUTH2_JSON, toJson(connector.getAuthConfig()));
        assertEquals(Map.of("model", "y", "temperature", "0"), connector.getParameters());
    }

    @Test
    public void update_aConnectorStoredWithoutParameters() {
        HttpConnector declared = http(null, null);
        declared.update(update(OAuth2AuthConfig.NONE, null));
        assertEquals(OAuth2AuthConfig.NONE, declared.getAuthConfig());
        assertNull(declared.getParameters());

        HttpConnector plain = http(null, null);
        plain.update(update(null, Map.of("model", "x")));
        assertEquals(Map.of("model", "x"), plain.getParameters());
        assertNull(plain.getAuthConfig());
    }

    @Test
    public void stream_roundTripKeepsTheFieldAtTheCurrentVersionAndDropsItBefore() throws IOException {
        HttpConnector source = readDirect(storedDocument(FULL_OAUTH2_JSON));
        assertEquals(fullOauth2(), source.getAuthConfig());

        HttpConnector current = roundTrip(source, Version.CURRENT, HttpConnector::new);
        assertSameApartFromAuthConfig(source, current);
        assertEquals(fullOauth2(), current.getAuthConfig());

        HttpConnector older = roundTrip(source, CommonValue.VERSION_3_9_0, HttpConnector::new);
        assertSameApartFromAuthConfig(source, older);
        assertNull(older.getAuthConfig());

        assertNull(roundTrip(http(null), Version.CURRENT, HttpConnector::new).getAuthConfig());
    }
}
