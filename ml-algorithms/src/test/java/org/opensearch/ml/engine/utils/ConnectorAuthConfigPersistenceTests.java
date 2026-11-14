/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.Test;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenResponseSpec;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.remote.metadata.client.impl.DDBJsonTransformer;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Every stored state and update, in connector and in model documents, reads back through the real parsers as the same
 * {@code auth_config} on the local index (a recursive merge), the remote clients (the same, with NON_NULL mappers) and DynamoDB.
 */
public class ConnectorAuthConfigPersistenceTests {

    /** Writes JSON with single quotes for readability. */
    private static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }

    private static final String TOKEN_URL = "https://login.example.com/t";

    /** A stored configuration with a top-level scalar, a nested member and a whole object, so a drop of each kind is covered. */
    private static final OAuth2AuthConfig FULL = OAuth2AuthConfig
        .builder()
        .type(ConnectorAuthType.OAUTH2)
        .tokenUrl(TOKEN_URL)
        .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC)
        .clientId("id")
        .scope("s1")
        .tokenRequestParams(new LinkedHashMap<>(Map.of("resource", "r", "x", "y")))
        .tokenResponse(TokenResponseSpec.builder().defaultTtlSeconds(600).build())
        .build();

    private static XContentParser parser(String json) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        return parser;
    }

    private static String toJson(ToXContentObject object) throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        object.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return builder.toString();
    }

    private static Map<String, Object> toMap(String json) {
        return XContentHelper.convertToMap(XContentType.JSON.xContent(), json, true);
    }

    private static String mapToJson(Map<String, Object> map) throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.map(map);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static Object withoutNullMembers(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            ((Map<String, Object>) value).forEach((k, v) -> {
                if (v != null) {
                    copy.put(k, withoutNullMembers(v));
                }
            });
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object element : (List<Object>) value) {
                copy.add(withoutNullMembers(element));
            }
            return copy;
        }
        return value;
    }

    /** One metadata store: what a put leaves, what a partial update leaves, and the _source a GET returns. */
    private interface Store {
        String name();

        void put(String document) throws IOException;

        void update(String document) throws IOException;

        String read() throws IOException;
    }

    /** The local index; the remote clients when {@code nonNull} is set. */
    private static final class MergingStore implements Store {
        private final boolean nonNull;
        private Map<String, Object> source;

        MergingStore(boolean nonNull) {
            this.nonNull = nonNull;
        }

        @Override
        public String name() {
            return nonNull ? "remote" : "local";
        }

        @Override
        public void put(String document) {
            // A put keeps nulls on every merging store.
            source = toMap(document);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void update(String document) {
            Map<String, Object> doc = toMap(document);
            if (nonNull) {
                doc = (Map<String, Object>) withoutNullMembers(doc);
            }
            XContentHelper.update(source, doc, true);
        }

        @Override
        @SuppressWarnings("unchecked")
        public String read() throws IOException {
            return mapToJson(nonNull ? (Map<String, Object>) withoutNullMembers(source) : source);
        }
    }

    /** DynamoDB through the real attribute conversion: an update replaces each top-level key, and numbers read back as strings. */
    private static final class DynamoDbStore implements Store {
        private Map<String, AttributeValue> source;

        @Override
        public String name() {
            return "ddb";
        }

        @Override
        public void put(String document) {
            source = DDBJsonTransformer.convertJsonObjectToDDBAttributeMap(StringUtils.MAPPER.readTree(document));
        }

        @Override
        public void update(String document) {
            Map<String, AttributeValue> doc = new HashMap<>(
                DDBJsonTransformer.convertJsonObjectToDDBAttributeMap(StringUtils.MAPPER.readTree(document))
            );
            doc.remove("tenant_id");
            doc.remove("_id");
            Map<String, AttributeValue> merged = new HashMap<>(source);
            merged.putAll(doc);
            source = merged;
        }

        @Override
        public String read() {
            return DDBJsonTransformer.convertDDBAttributeValueMapToObjectNode(source).toString();
        }

        /** The stored _source as the store holds it. */
        Map<String, Object> raw() throws IOException {
            return toMap(read());
        }
    }

    private static List<Store> stores() {
        return List.of(new MergingStore(false), new MergingStore(true), new DynamoDbStore());
    }

    /** A connector document, or a model document whose connector is updated whole. */
    private interface Kind {
        String name();

        String putDocument(HttpConnector connector) throws IOException;

        Connector readConnector(String source) throws IOException;

        String updateDocument(Connector updated) throws IOException;

        /** The connector part of a stored or written document. */
        Map<String, Object> connectorPart(Map<String, Object> document);
    }

    private static final Kind CONNECTOR_INDEX = new Kind() {
        @Override
        public String name() {
            return "connector";
        }

        @Override
        public String putDocument(HttpConnector connector) throws IOException {
            return toJson(connector);
        }

        @Override
        public Connector readConnector(String source) throws IOException {
            return Connector.createConnector(parser(source));
        }

        @Override
        public String updateDocument(Connector updated) throws IOException {
            return toJson(updated);
        }

        @Override
        public Map<String, Object> connectorPart(Map<String, Object> document) {
            return document;
        }
    };

    private static final Kind MODEL_INDEX = new Kind() {
        @Override
        public String name() {
            return "model";
        }

        @Override
        public String putDocument(HttpConnector connector) throws IOException {
            return toJson(MLModel.builder().name("m").algorithm(FunctionName.REMOTE).modelId("m1").connector(connector).build());
        }

        @Override
        public Connector readConnector(String source) throws IOException {
            return MLModel.parse(parser(source), FunctionName.REMOTE.name()).getConnector();
        }

        @Override
        public String updateDocument(Connector updated) throws IOException {
            return toJson(MLUpdateModelInput.builder().modelId("m1").updatedConnector(updated).build());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> connectorPart(Map<String, Object> document) {
            return (Map<String, Object>) document.get("connector");
        }
    };

    /** The connector a create or register request yields, before the put. */
    private static HttpConnector created(OAuth2AuthConfig authConfig) {
        return HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(new HashMap<>(Map.of("model", "x")))
            .credential(Map.of("key", "ciphertext"))
            .actions(
                List
                    .of(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/v1/chat")
                            .build()
                    )
            )
            .authConfig(authConfig)
            .build();
    }

    private static MLCreateConnectorInput request(String singleQuoted) throws IOException {
        return MLCreateConnectorInput.parse(parser(json(singleQuoted)), true);
    }

    private static final String OAUTH2_PREFIX = "{'auth_config':{'type':'oauth2','token_url':'"
        + TOKEN_URL
        + "','client_auth_method':'client_secret_basic','client_id':'id'";
    private static final String DECLARE_NONE = "{'auth_config':{'type':'none'}}";
    /** Over {@link #FULL}: drops scope (top level), token_request_params.x (nested) and token_response (a whole object). */
    private static final String FULL_REPLACEMENT = OAUTH2_PREFIX + ",'token_request_params':{'resource':'r'}}}";
    private static final String UNRELATED = "{'description':'d2'}";

    /**
     * A stored state, as the create or register path puts it, and the update applied to it: the states and updates this
     * release writes, none over an active configuration (every member marked), and one replacement that marks at every depth.
     */
    private static final List<Object[]> SCENARIOS = List
        .of(
            new Object[] { null, List.of(DECLARE_NONE) },
            new Object[] { null, List.of(UNRELATED) },
            new Object[] { OAuth2AuthConfig.NONE, List.of(DECLARE_NONE) },
            new Object[] { OAuth2AuthConfig.NONE, List.of(UNRELATED) },
            new Object[] { FULL, List.of(DECLARE_NONE) },
            new Object[] { FULL, List.of(FULL_REPLACEMENT) }
        );

    /** What a reader hands back of a connector: its configuration and its parameters. */
    private record Read(OAuth2AuthConfig authConfig, Map<String, String> parameters) {
        static Read of(Connector connector) {
            return new Read(connector.getAuthConfig(), new HashMap<>(connector.getParameters()));
        }
    }

    private static void assertWrittenDocument(String context, Kind kind, String document, boolean isPut) {
        Map<String, Object> connector = kind.connectorPart(toMap(document));
        Object authConfig = connector.get("auth_config");
        Object parameters = connector.get("parameters");
        for (Object part : new Object[] { authConfig, parameters }) {
            if (part != null) {
                assertFalse(context + " writes a null in " + document, contains(part, Objects::isNull));
            }
        }
        // Removal markers appear only in update documents: a put carries none, and the merging stores yield the
        // replacement only because the updates that drop members carry them.
        if (isPut && authConfig != null) {
            assertFalse(
                context + " puts a removal marker: " + document,
                contains(authConfig, v -> v instanceof List && ((List<?>) v).isEmpty())
            );
        }
    }

    /** Whether {@code value}, or any member or element at any depth, satisfies {@code matches}. */
    @SuppressWarnings("unchecked")
    private static boolean contains(Object value, Predicate<Object> matches) {
        if (matches.test(value)) {
            return true;
        }
        if (value instanceof Map) {
            return ((Map<String, Object>) value).values().stream().anyMatch(v -> contains(v, matches));
        }
        if (value instanceof List) {
            return ((List<Object>) value).stream().anyMatch(v -> contains(v, matches));
        }
        return false;
    }

    /**
     * Runs one scenario on one store and returns what reads back after each step; {@code onWrite} sees each update
     * document as written.
     */
    private static List<Read> run(Kind kind, Store store, OAuth2AuthConfig stored, List<String> requests, Consumer<String> onWrite)
        throws IOException {
        String putDocument = kind.putDocument(created(stored));
        assertWrittenDocument(stored + " put", kind, putDocument, true);
        store.put(putDocument);
        List<Read> reads = new ArrayList<>();
        for (String request : requests) {
            Connector connector = kind.readConnector(store.read());
            connector.update(request(request));
            String document = kind.updateDocument(connector);
            onWrite.accept(document);
            store.update(document);
            reads.add(Read.of(kind.readConnector(store.read())));
        }
        return reads;
    }

    /** What an in-memory connector holds after the same requests. */
    private static List<Read> expected(OAuth2AuthConfig stored, List<String> requests) throws IOException {
        Connector connector = created(stored);
        List<Read> results = new ArrayList<>();
        for (String request : requests) {
            connector.update(request(request));
            results.add(Read.of(connector));
            // The next request starts from what a reader hands back.
            connector = Connector.createConnector(parser(toJson(connector)));
        }
        return results;
    }

    @Test
    public void everyStoreYieldsTheMergeResult() throws IOException {
        for (Kind kind : List.of(CONNECTOR_INDEX, MODEL_INDEX)) {
            for (Object[] scenario : SCENARIOS) {
                OAuth2AuthConfig stored = (OAuth2AuthConfig) scenario[0];
                @SuppressWarnings("unchecked")
                List<String> requests = (List<String>) scenario[1];
                String context = kind.name() + " / " + stored + " / " + requests;
                List<Read> expected = expected(stored, requests);
                for (Store store : stores()) {
                    List<Read> reads = run(
                        kind,
                        store,
                        stored,
                        requests,
                        document -> assertWrittenDocument(context, kind, document, false)
                    );
                    assertEquals(context + " on " + store.name(), expected, reads);
                }
            }
        }
    }

    private static OAuth2AuthConfig applyOnce(Store store, String writtenDocument) throws IOException {
        store.put(CONNECTOR_INDEX.putDocument(created(FULL)));
        store.update(writtenDocument);
        return CONNECTOR_INDEX.readConnector(store.read()).getAuthConfig();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> authConfigOf(Map<String, Object> connectorDocument) {
        return (Map<String, Object>) connectorDocument.get("auth_config");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void controls_nullMarkersDivergeOnRemoteStoresAndNoMarkersDivergeOnMergingStores() throws IOException {
        Connector connector = CONNECTOR_INDEX.readConnector(toJson(created(FULL)));
        connector.update(request(FULL_REPLACEMENT));
        String withMarkers = toJson(connector);
        OAuth2AuthConfig expected = connector.getAuthConfig();
        assertEquals(request(FULL_REPLACEMENT).getAuthConfig(), expected);

        // The written document marks each dropped member at the highest level where it is missing, and [] gives the
        // replacement on every store. DynamoDB stores the markers as written; its reader drops them.
        Map<String, Object> written = authConfigOf(toMap(withMarkers));
        assertEquals(List.of(), written.get("scope"));
        assertEquals(List.of(), written.get("token_response"));
        assertEquals(List.of(), ((Map<String, Object>) written.get("token_request_params")).get("x"));
        for (Store store : stores()) {
            assertEquals("[] on " + store.name(), expected, applyOnce(store, withMarkers));
        }
        DynamoDbStore ddb = new DynamoDbStore();
        ddb.put(CONNECTOR_INDEX.putDocument(created(FULL)));
        assertEquals("600", ((Map<String, Object>) authConfigOf(ddb.raw()).get("token_response")).get("default_ttl_seconds"));
        ddb.update(withMarkers);
        assertEquals(List.of(), authConfigOf(ddb.raw()).get("scope"));
        assertEquals(expected, CONNECTOR_INDEX.readConnector(ddb.read()).getAuthConfig());

        // Null markers (the only [] in the written document are the markers): the remote clients drop them, so stale
        // members survive there; a store that keeps them gives the replacement, since the reader treats a null member as absent.
        String withNulls = withMarkers.replace("[]", "null");
        assertNotEquals(expected, applyOnce(new MergingStore(true), withNulls));
        assertEquals(expected, applyOnce(new MergingStore(false), withNulls));

        // No markers: the recursive merge keeps every removed member.
        Map<String, Object> withoutMarkers = toMap(withMarkers);
        withoutMarkers.put("auth_config", toMap(toJson(request(FULL_REPLACEMENT).getAuthConfig())));
        assertNotEquals(expected, applyOnce(new MergingStore(false), mapToJson(withoutMarkers)));
    }
}
