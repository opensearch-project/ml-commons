/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.compat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.remote.CompatInvoker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Replays ML system-index documents stored by older versions against the code on this branch.
 *
 * <p>A subclass names a classpath directory of fixture sets ({@link #fixturesResource()}). Each fixture set is one
 * capture from a throwaway cluster with dummy credentials: the raw {@code _source} of {@code .plugins-ml-connector},
 * {@code -model} and {@code -agent} ({@code plugins-ml-<index>.json}, a list of {@code {_id, _source}}) plus a
 * {@code manifest.json} holding the {@code master_key} the cluster encrypted credentials with.
 *
 * <p>For every document the test runs the steps the plugin runs on an upgraded cluster:
 * <ul>
 *   <li>{@code parse} - the same parser the GET/DELETE/predict transport actions use</li>
 *   <li>{@code decrypt} - every stored credential decrypts with the stored master key</li>
 *   <li>{@code roundtrip} - toXContent and re-parse yields the same document</li>
 *   <li>{@code validate-endpoint} - the connector still passes registration-time endpoint validation</li>
 *   <li>{@code invoke} - remote models only: deploy and predict through the real executor with the network stubbed
 *   (see {@link CompatInvoker}); the request must be signed/authorized and carry a JSON body without unresolved
 *   placeholders, and the canned response must post-process into model tensors</li>
 *   <li>{@code request} - the outgoing request (method, URL, stable headers, parsed body) equals the one recorded in
 *   the fixture set's {@code expected-requests.json}. Run with {@code -PmlCompatRecord} to (re)write those
 *   files after an intentional change, and review the diff.</li>
 * </ul>
 *
 * <p>A failure fails the build unless it is listed in {@code known-failures.txt} next to the fixture sets. A listed
 * failure that no longer happens also fails the build, so the list stays accurate once a fix lands.
 *
 * <p>Distributions that change connector behaviour (extra protocols, credential sources, endpoint allow-lists)
 * override {@link #environment()}. Calls whose signatures differ between release lines (credential decryption, model
 * deployment) are confined to {@link CompatInvoker}, so this class is the same on every branch.
 */
public abstract class StoredDocumentCompatibilityTestCase {

    /** Keeps integral JSON numbers integral (Gson's default reads every number as a double). */
    private static final Gson GSON = new com.google.gson.GsonBuilder()
        .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
        .create();

    private Map<String, String> knownFailures;
    private int decryptedFields;
    private int invoked;
    private int compared;
    /** Fixture set -> model id -> recorded request, as found on disk / as produced by this run. */
    private final Map<Path, Map<String, Object>> expectedRequests = new HashMap<>();
    private final Map<Path, Map<String, Object>> actualRequests = new TreeMap<>();
    private static final boolean RECORD = Boolean.getBoolean("ml.compat.record");
    /** Headers whose values change per request (signing time, signature, body hash) or are added by the transport. */
    private static final Set<String> VOLATILE_HEADERS = Set
        .of("authorization", "x-amz-date", "x-amz-security-token", "x-amz-content-sha256", "content-length", "host", "user-agent");

    /** Classpath directory holding the fixture sets, e.g. {@code /compat-fixtures/opensearch}. */
    protected abstract String fixturesResource();

    /** Distribution-specific stubs and settings. Plain OpenSearch by default. */
    protected CompatEnvironment environment() {
        return new CompatEnvironment() {
        };
    }

    @Test
    public void storedDocumentsAreReadableByCurrentCode() throws Exception {
        Path fixturesDir = fixturesDir();
        knownFailures = readKnownFailures(fixturesDir.resolve("known-failures.txt"));
        List<Path> sets = fixtureSets(fixturesDir);
        assertTrue("no fixture sets under " + fixturesDir, sets.size() > 0);
        Map<String, String> actual = new TreeMap<>();
        int checked = 0;
        for (Path set : sets) {
            int checkedBefore = checked;
            String masterKey = masterKey(set);
            Map<String, Map<String, Object>> connectorsById = new HashMap<>();
            for (Map<String, Object> doc : docs(set, "connector")) {
                checked++;
                connectorsById.put((String) doc.get("_id"), source(doc));
                checkConnectorDoc(set, doc, masterKey, actual);
            }
            for (Map<String, Object> doc : docs(set, "model")) {
                checked++;
                checkModelDoc(set, doc, masterKey, connectorsById, actual);
            }
            for (Map<String, Object> doc : docs(set, "agent")) {
                checked++;
                checkAgentDoc(set, doc, actual);
            }
            // A set whose document files are missing or empty would otherwise pass without checking anything.
            assertTrue("fixture set " + set.getFileName() + " has no connector, model or agent documents", checked > checkedBefore);
        }
        System.out
            .printf(
                "ml-compat[%s]: %d fixture sets, %d documents, %d credential fields decrypted, %d remote models invoked, %d requests compared, %d failures%n",
                fixturesResource(),
                sets.size(),
                checked,
                decryptedFields,
                invoked,
                compared,
                actual.size()
            );
        if (RECORD) {
            writeRecordedRequests();
        }
        report(actual);
        // Guard against the invoke and request stages silently not running (e.g. no model matched as remote).
        assertTrue("no remote model was invoked; the invoke stage did not run", invoked > 0);
        assertTrue("no outgoing request was compared; the request stage did not run", RECORD || compared > 0);
    }

    private void checkConnectorDoc(Path set, Map<String, Object> doc, String masterKey, Map<String, String> out) {
        String key = key(set, "connector", doc);
        Connector connector = step(out, key, "parse", () -> {
            Connector c = Connector.createConnector(parser(source(doc)));
            if (c == null) {
                throw new IllegalStateException("Connector.createConnector returned null");
            }
            return c;
        });
        if (connector == null) {
            return;
        }
        checkConnector(key, connector, masterKey, out);
        step(out, key, "roundtrip", () -> {
            String first = json(connector);
            String second = json(Connector.createConnector(parser(first)));
            assertSameJson(first, second);
            return null;
        });
    }

    private void checkModelDoc(
        Path set,
        Map<String, Object> doc,
        String masterKey,
        Map<String, Map<String, Object>> connectorsById,
        Map<String, String> out
    ) {
        String key = key(set, "model", doc);
        String algorithm = String.valueOf(source(doc).get(MLModel.ALGORITHM_FIELD));
        MLModel model = step(out, key, "parse", () -> parseModel(source(doc), algorithm));
        if (model == null) {
            return;
        }
        if (model.getConnector() != null) {
            checkConnector(key, model.getConnector(), masterKey, out);
        }
        step(out, key, "roundtrip", () -> {
            String first = json(model);
            assertSameJson(first, json(parseModel(GSON.fromJson(first, new TypeToken<Map<String, Object>>() {
            }.getType()), algorithm)));
            return null;
        });
        if (FunctionName.REMOTE.name().equals(algorithm)) {
            String modelId = String.valueOf(doc.get("_id"));
            CompatInvoker.SentRequest sent = step(out, key, "invoke", () -> invoke(set, modelId, model, masterKey, connectorsById));
            if (sent != null) {
                step(out, key, "request", () -> {
                    compareRequest(set, modelId, sent);
                    return null;
                });
            }
        }
    }

    /** Deploy and predict the stored remote model the way MLModelManager / MLPredictTaskRunner do, with the network stubbed. */
    private CompatInvoker.SentRequest invoke(
        Path set,
        String modelId,
        MLModel model,
        String masterKey,
        Map<String, Map<String, Object>> connectorsById
    ) throws Exception {
        if (model.getConnector() == null) {
            Map<String, Object> stored = connectorsById.get(model.getConnectorId());
            if (stored == null) {
                throw new IllegalStateException("connector " + model.getConnectorId() + " not in fixture set " + set.getFileName());
            }
            model.setConnector(Connector.createConnector(parser(stored)));
        }
        Connector connector = model.getConnector();
        ConnectorAction action = connector.getActions().get(0);
        CompatInvoker.Result result = CompatInvoker
            .invoke(model, masterKey, predictInput(connector, action), cannedResponse(connector, action), environment());

        CompatInvoker.SentRequest sent = result.request;
        String protocol = connector.getProtocol();
        if (environment().sigV4Protocols().contains(protocol)) {
            String auth = sent.header("Authorization").orElse("");
            if (!auth.startsWith("AWS4-HMAC-SHA256 ")) {
                throw new IllegalStateException("request to " + sent.uri + " is not SigV4-signed: Authorization=" + auth);
            }
        } else if (action.getHeaders() != null && action.getHeaders().containsKey("Authorization")) {
            String auth = sent.header("Authorization").orElse("");
            if (auth.isBlank() || auth.contains("${")) {
                throw new IllegalStateException("Authorization header not substituted: " + auth);
            }
        }
        if (!sent.body.isEmpty()) {
            try {
                GSON.fromJson(sent.body, Object.class);
            } catch (RuntimeException e) {
                throw new IllegalStateException("request body is not JSON: " + sent.body, e);
            }
        }
        if (sent.body.contains("${parameters.")) {
            throw new IllegalStateException("unresolved placeholder in request body: " + sent.body);
        }
        int tensors = 0;
        for (ModelTensors t : result.output.getMlModelOutputs()) {
            List<ModelTensor> list = t.getMlModelTensors();
            tensors += list == null ? 0 : list.size();
        }
        if (tensors == 0) {
            throw new IllegalStateException("response produced no model tensors");
        }
        invoked++;
        System.out.printf("ml-compat: invoked %s %s -> %s %s%n", set.getFileName(), modelId, sent.method, stripQuery(sent.uri));
        return sent;
    }

    // ---- request comparison ----

    /** The parts of a request that must not change across versions; volatile signing headers are reduced to presence. */
    private static Map<String, Object> normalize(CompatInvoker.SentRequest sent) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", sent.method);
        out.put("url", sent.uri);
        Map<String, Object> headers = new TreeMap<>();
        Set<String> present = new TreeSet<>();
        for (Map.Entry<String, List<String>> h : sent.headers.entrySet()) {
            String name = h.getKey().toLowerCase(java.util.Locale.ROOT);
            if (VOLATILE_HEADERS.contains(name)) {
                present.add(name);
            } else {
                headers.put(name, h.getValue().size() == 1 ? h.getValue().get(0) : h.getValue());
            }
        }
        out.put("headers", headers);
        out.put("volatile_headers_present", new ArrayList<>(present));
        out.put("body", sent.body.isEmpty() ? null : GSON.fromJson(sent.body, Object.class));
        return out;
    }

    private void compareRequest(Path set, String modelId, CompatInvoker.SentRequest sent) throws IOException {
        Map<String, Object> actual = GSON.fromJson(GSON.toJson(normalize(sent)), new TypeToken<Map<String, Object>>() {
        }.getType());
        actualRequests.computeIfAbsent(set, k -> new TreeMap<>()).put(modelId, actual);
        if (RECORD) {
            return;
        }
        Map<String, Object> expected = expectedRequests.computeIfAbsent(set, k -> {
            Path f = k.resolve("expected-requests.json");
            try {
                return Files.exists(f) ? GSON.fromJson(Files.readString(f), new TypeToken<Map<String, Object>>() {
                }.getType()) : new HashMap<>();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        Object want = expected.get(modelId);
        if (want == null) {
            throw new IllegalStateException(
                "no recorded request for this model in expected-requests.json; re-record with -PmlCompatRecord"
            );
        }
        compared++;
        if (!want.equals(actual)) {
            throw new AssertionError("request changed: " + diff(want, actual, "$"));
        }
    }

    /** First difference between two parsed JSON values, as a path. */
    @SuppressWarnings("unchecked")
    private static String diff(Object want, Object got, String path) {
        if (want instanceof Map && got instanceof Map) {
            Set<String> keys = new TreeSet<>(((Map<String, Object>) want).keySet());
            keys.addAll(((Map<String, Object>) got).keySet());
            for (String k : keys) {
                Object w = ((Map<String, Object>) want).get(k);
                Object g = ((Map<String, Object>) got).get(k);
                if (!Objects.equals(w, g)) {
                    return diff(w, g, path + "." + k);
                }
            }
        } else if (want instanceof List && got instanceof List && ((List<?>) want).size() == ((List<?>) got).size()) {
            for (int i = 0; i < ((List<?>) want).size(); i++) {
                if (!Objects.equals(((List<?>) want).get(i), ((List<?>) got).get(i))) {
                    return diff(((List<?>) want).get(i), ((List<?>) got).get(i), path + "[" + i + "]");
                }
            }
        }
        return path + " expected " + GSON.toJson(want) + " but was " + GSON.toJson(got);
    }

    /** In record mode, writes expected-requests.json into the source tree (ml.compat.recordDir) for every fixture set. */
    private void writeRecordedRequests() throws IOException {
        String dir = System.getProperty("ml.compat.recordDir");
        if (dir == null) {
            throw new IllegalStateException("record mode needs -Dml.compat.recordDir=<source fixtures root>");
        }
        Gson pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (Map.Entry<Path, Map<String, Object>> e : actualRequests.entrySet()) {
            Path target = Path.of(dir, fixturesResource(), e.getKey().getFileName().toString(), "expected-requests.json");
            Files.writeString(target, pretty.toJson(e.getValue()) + "\n");
            System.out.println("ml-compat: recorded " + target);
        }
    }

    // ---- predict input and canned remote responses ----

    private static final Pattern PLACEHOLDER = Pattern.compile("(\"?)\\$\\{parameters\\.([A-Za-z0-9_.-]+)}");

    /** Builds a predict input that supplies every request_body placeholder the connector does not set itself. */
    private static MLInput predictInput(Connector connector, ConnectorAction action) {
        String pre = action.getPreProcessFunction();
        if (pre != null && pre.contains("embedding")) {
            return MLInput
                .builder()
                .algorithm(FunctionName.TEXT_EMBEDDING)
                .inputDataset(TextDocsInputDataSet.builder().docs(List.of("compat test")).build())
                .build();
        }
        Map<String, String> params = new HashMap<>();
        Map<String, String> fromConnector = connector.getParameters() == null ? Map.of() : connector.getParameters();
        Matcher m = PLACEHOLDER.matcher(action.getRequestBody() == null ? "" : action.getRequestBody());
        while (m.find()) {
            String name = m.group(2);
            if (fromConnector.containsKey(name) || params.containsKey(name)) {
                continue;
            }
            boolean quoted = !m.group(1).isEmpty();
            params
                .put(
                    name,
                    quoted ? "compat test"
                        : "messages".equals(name) ? "[{\"role\":\"user\",\"content\":\"compat test\"}]"
                        : "[\"compat test\"]"
                );
        }
        return MLInput
            .builder()
            .algorithm(FunctionName.REMOTE)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(params).build())
            .build();
    }

    /**
     * A 200 body shaped the way the connector's post-processing (or response_filter) expects. A connector style not
     * covered here produces no tensors and fails the invoke stage loudly; extend this method when adding fixtures for it.
     */
    private static String cannedResponse(Connector connector, ConnectorAction action) {
        String post = action.getPostProcessFunction() == null ? "" : action.getPostProcessFunction();
        if (post.equals("connector.post_process.mlcommons.passthrough")) {
            return "{\"inference_results\":[{\"output\":[{\"name\":\"output\",\"data_type\":\"FLOAT32\",\"shape\":[3],\"data\":[0.1,0.2,0.3]}]}]}";
        }
        if (post.contains("embedding")) {
            return "{\"embedding\":[0.1,0.2,0.3]}";
        }
        String filter = connector.getParameters() == null ? null : connector.getParameters().get("response_filter");
        return filter == null ? "{\"compat\":\"ok\"}" : GSON.toJson(objectAtPath(filter, "compat ok"));
    }

    /** Smallest JSON value that has {@code leaf} at a simple JSONPath like {@code $.content[0].text}. */
    private static Object objectAtPath(String path, Object leaf) {
        Matcher m = Pattern.compile("\\.([A-Za-z0-9_]+)|\\[(\\d+)]").matcher(path);
        List<String[]> steps = new ArrayList<>();
        while (m.find()) {
            steps.add(new String[] { m.group(1), m.group(2) });
        }
        Object value = leaf;
        for (int i = steps.size() - 1; i >= 0; i--) {
            String[] step = steps.get(i);
            if (step[0] != null) {
                value = Map.of(step[0], value);
            } else {
                List<Object> list = new ArrayList<>();
                for (int j = 0; j < Integer.parseInt(step[1]); j++) {
                    list.add(Map.of());
                }
                list.add(value);
                value = list;
            }
        }
        return value;
    }

    private static String stripQuery(String uri) {
        int q = uri.indexOf('?');
        return q < 0 ? uri : uri.substring(0, q);
    }

    private void checkAgentDoc(Path set, Map<String, Object> doc, Map<String, String> out) {
        String key = key(set, "agent", doc);
        MLAgent agent = step(out, key, "parse", () -> parseAgent(GSON.toJson(source(doc))));
        if (agent == null) {
            return;
        }
        step(out, key, "roundtrip", () -> {
            String first = json(agent);
            assertSameJson(first, json(parseAgent(first)));
            return null;
        });
    }

    /** Decrypt and endpoint validation, shared by standalone connectors and connectors inline in a model. */
    private void checkConnector(String key, Connector connector, String masterKey, Map<String, String> out) {
        step(out, key, "decrypt", () -> {
            // getCredential() is declared on AbstractConnector (every connector type extends it); some distributions
            // also add it to the Connector interface.
            Map<String, String> stored = ((AbstractConnector) connector).getCredential();
            if (stored == null || stored.isEmpty()) {
                return null;
            }
            Map<String, String> plain = CompatInvoker.decryptCredentials(connector, masterKey);
            for (String field : stored.keySet()) {
                String value = plain == null ? null : plain.get(field);
                if (value == null || value.isEmpty()) {
                    throw new IllegalStateException("credential field '" + field + "' decrypted to empty");
                }
                decryptedFields++;
            }
            return null;
        });
        step(out, key, "validate-endpoint", () -> {
            connector.validateConnectorURL(environment().registrationTrustedEndpoints());
            return null;
        });
    }

    // ---- parsing, mirroring the plugin's transport actions ----

    private static MLModel parseModel(Map<String, Object> source, String algorithm) throws IOException {
        try (XContentParser parser = parser(GSON.toJson(source))) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return MLModel.parse(parser, algorithm);
        }
    }

    private static MLAgent parseAgent(String json) throws IOException {
        try (XContentParser parser = parser(json)) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return MLAgent.parse(parser);
        }
    }

    private static XContentParser parser(Map<String, Object> source) throws IOException {
        return parser(GSON.toJson(source));
    }

    private static XContentParser parser(String json) throws IOException {
        return XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, json);
    }

    private static String json(ToXContentObject object) throws IOException {
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        object.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return builder.toString();
    }

    private static void assertSameJson(String expected, String actual) {
        Object a = GSON.fromJson(expected, Object.class);
        Object b = GSON.fromJson(actual, Object.class);
        if (!a.equals(b)) {
            throw new AssertionError("round-trip changed the document:\n  before: " + expected + "\n  after:  " + actual);
        }
    }

    // ---- fixtures ----

    private Path fixturesDir() throws URISyntaxException {
        URL url = getClass().getResource(fixturesResource());
        if (url == null) {
            throw new IllegalStateException("fixture directory " + fixturesResource() + " not on the test classpath");
        }
        return Path.of(url.toURI());
    }

    private static List<Path> fixtureSets(Path fixturesDir) throws IOException {
        try (Stream<Path> s = Files.list(fixturesDir)) {
            return s.filter(p -> Files.isRegularFile(p.resolve("manifest.json"))).sorted().collect(Collectors.toList());
        }
    }

    private static String masterKey(Path set) throws IOException {
        Map<String, Object> manifest = GSON.fromJson(Files.readString(set.resolve("manifest.json")), new TypeToken<Map<String, Object>>() {
        }.getType());
        return (String) Objects.requireNonNull(manifest.get("master_key"), "master_key missing in " + set);
    }

    private static List<Map<String, Object>> docs(Path set, String index) throws IOException {
        Path file = set.resolve("plugins-ml-" + index + ".json");
        if (!Files.exists(file)) {
            return List.of();
        }
        return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Map<String, Object>>>() {
        }.getType());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("_source");
    }

    private static String key(Path set, String index, Map<String, Object> doc) {
        return set.getFileName() + "\t" + index + "\t" + doc.get("_id");
    }

    // ---- result bookkeeping ----

    interface Step<T> {
        T run() throws Exception;
    }

    private static <T> T step(Map<String, String> out, String key, String stage, Step<T> step) {
        try {
            return step.run();
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null && t instanceof java.util.concurrent.ExecutionException ? t.getCause() : t;
            out.put(key + "\t" + stage, cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()).split("\n")[0]);
            return null;
        }
    }

    private static Map<String, String> readKnownFailures(Path file) throws IOException {
        Map<String, String> known = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return known;
        }
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", 5);
            if (f.length != 5 || f[4].isBlank()) {
                // A blank message would match any failure for that key, hiding a changed failure.
                throw new IllegalArgumentException("bad known-failures line (need 5 tab-separated fields, non-empty message): " + line);
            }
            known.put(String.join("\t", f[0], f[1], f[2], f[3]), f[4]);
        }
        return known;
    }

    private void report(Map<String, String> actual) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : actual.entrySet()) {
            String expected = knownFailures.get(e.getKey());
            if (expected == null) {
                problems.add("NEW FAILURE      " + e.getKey() + "\t" + e.getValue());
            } else if (!e.getValue().contains(expected)) {
                problems.add("CHANGED FAILURE  " + e.getKey() + "\t" + e.getValue() + "   (known-failures.txt expects: " + expected + ")");
            }
        }
        for (String k : knownFailures.keySet()) {
            if (!actual.containsKey(k)) {
                problems.add("NOW PASSING      " + k + "   (remove it from known-failures.txt)");
            }
        }
        if (!problems.isEmpty()) {
            fail("Stored ML documents from older versions no longer behave as recorded:\n  " + String.join("\n  ", problems));
        }
    }
}
