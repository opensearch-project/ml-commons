/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.OAUTH2_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.TOKEN_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.document;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.json;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parseMessage;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parser;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.roundTrip;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.toJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;

/** The {@code auth_config} field of {@link MLCreateConnectorInput}, for create and update. */
public class MLCreateConnectorInputAuthConfigTests {

    private static final String HEAD = "{'name':'c','version':'1','protocol':'http','credential':{'s':'v'},";
    private static final String ACTIONS = "'actions':[{'action_type':'PREDICT','method':'POST','url':'https://api.openai.com/v1/chat'}]";
    /** An oauth2 request block, without the {@code grant_type} the parse defaults. */
    private static final String OAUTH2_REQUEST = "'auth_config':{'type':'oauth2','token_url':'"
        + TOKEN_URL
        + "','client_auth_method':'client_secret_basic','client_id':'app','client_secret':'${credential.s}'}";

    private static MLCreateConnectorInput parse(String singleQuoted, boolean updateConnector) throws IOException {
        return MLCreateConnectorInput.parse(parser(json(singleQuoted)), updateConnector);
    }

    /** A create request with {@code fields} between the head and the actions. */
    private static String create(String fields) {
        return HEAD + fields + (fields.isEmpty() ? "" : ",") + ACTIONS + "}";
    }

    private static String createFails(String fields) {
        return assertThrows(IllegalArgumentException.class, () -> parse(create(fields), false)).getMessage();
    }

    private static String updateFails(String fields) {
        return assertThrows(IllegalArgumentException.class, () -> parse("{" + fields + "}", true)).getMessage();
    }

    @Test
    public void parse_readsTheObjectIntoTheField() throws IOException {
        MLCreateConnectorInput input = parse(create("'parameters':{'model':'x'}," + OAUTH2_REQUEST), false);
        assertEquals(oauth2(), input.getAuthConfig());
        assertEquals(Map.of("model", "x"), input.getParameters());
        assertNull(parse(create("'parameters':{'model':'x'}"), false).getAuthConfig());
    }

    @Test
    public void parse_authConfigMustBeAnObject() {
        assertEquals(OAuth2AuthConfig.NOT_AN_OBJECT_MESSAGE, createFails("'auth_config':[{'type':'none'}]"));
    }

    @Test
    public void parse_runsTheStrictChecksOfTheObject() {
        assertEquals(parseMessage(document("{'type':'none','scopes':'a'}")), updateFails("'auth_config':{'type':'none','scopes':'a'}"));
    }

    @Test
    public void parse_updateForm() throws IOException {
        MLCreateConnectorInput update = parse("{'auth_config':{'type':'none'}}", true);
        assertTrue(update.isUpdateConnector());
        assertEquals(OAuth2AuthConfig.NONE, update.getAuthConfig());
        assertEquals(Map.of(), update.getParameters());
        assertNull(parse("{'description':'d'}", true).getAuthConfig());
    }

    @Test
    public void toXContent_writesTheObjectRightAfterParameters() throws IOException {
        MLCreateConnectorInput input = parse(create("'parameters':{'model':'x'}," + OAUTH2_REQUEST), false);
        String content = toJson(input);
        List<String> keys = new ArrayList<>(document(content).keySet());
        assertEquals(content, keys.indexOf("parameters") + 1, keys.indexOf("auth_config"));
        assertEquals(document(OAUTH2_JSON), document(content).get("auth_config"));
        assertEquals(Map.of("model", "x"), document(content).get("parameters"));
    }

    @Test
    public void stream_roundTripKeepsTheFieldAtTheCurrentVersionAndDropsItBefore() throws IOException {
        MLCreateConnectorInput input = parse(create("'parameters':{'model':'x'}," + OAUTH2_REQUEST), false);
        for (Version version : new Version[] { Version.CURRENT, CommonValue.VERSION_3_9_0 }) {
            MLCreateConnectorInput read = roundTrip(input, version, MLCreateConnectorInput::new);
            assertEquals(input.getName(), read.getName());
            assertEquals(input.getParameters(), read.getParameters());
            assertEquals(input.getActions(), read.getActions());
            assertEquals(version.toString(), version == Version.CURRENT ? oauth2() : null, read.getAuthConfig());
        }
    }

    @Test
    public void parametersAuth_isAnOrdinaryParameter() throws IOException {
        MLCreateConnectorInput input = parse(create("'parameters':{'model':'x','auth':'API_Key'}," + OAUTH2_REQUEST), false);
        assertEquals(Map.of("model", "x", "auth", "API_Key"), input.getParameters());
        assertEquals(oauth2(), input.getAuthConfig());
        // An object value is stringified like any other parameter and is not a configuration.
        MLCreateConnectorInput objectValued = parse(create("'parameters':{'auth':{'type':'none'}}"), false);
        assertEquals(Map.of("auth", json("{'type':'none'}")), objectValued.getParameters());
        assertNull(objectValued.getAuthConfig());
    }
}
