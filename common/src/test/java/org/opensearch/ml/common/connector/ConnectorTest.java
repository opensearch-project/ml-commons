/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opensearch.ml.common.connector.HttpConnectorTest.createHttpConnector;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.search.SearchModule;

public class ConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void fromStream() throws IOException {
        HttpConnector connector = createHttpConnector();
        BytesStreamOutput output = new BytesStreamOutput();
        connector.writeTo(output);
        Connector connector2 = Connector.fromStream(output.bytes().streamInput());
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void createConnector_Builder() throws IOException {
        HttpConnector connector = createHttpConnector();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);

        Connector connector2 = Connector.createConnector(builder, connector.getProtocol());
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void createConnector_Parser() throws IOException {
        HttpConnector connector = createHttpConnector();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        Connector connector2 = Connector.createConnector(parser);
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void validateConnectorURL_Invalid() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        HttpConnector connector = createHttpConnector();
        connector
            .validateConnectorURL(
                Arrays
                    .asList(
                        "^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                        "^https://api\\.openai\\.com/.*$",
                        "^https://api\\.cohere\\.ai/.*$",
                        "^https://bedrock-agent-runtime\\\\..*[a-z0-9-]\\\\.amazonaws\\\\.com/.*$"
                    )
            );
    }

    @Test
    public void validateConnectorURL() {
        HttpConnector connector = createHttpConnector();
        connector
            .validateConnectorURL(
                Arrays
                    .asList(
                        "^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                        "^https://api\\.openai\\.com/.*$",
                        "^https://bedrock-agent-runtime\\\\..*[a-z0-9-]\\\\.amazonaws\\\\.com/.*$",
                        "^" + connector.getActions().get(0).getUrl()
                    )
            );
    }

    @Test
    public void validateResolvedEndpoint_matches() {
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint("https://api.openai.com/v1/chat/completions", Arrays.asList("^https://api\\.openai\\.com/.*$"));
    }

    @Test
    public void validateResolvedEndpoint_noMatch_rejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        HttpConnector connector = createHttpConnector();
        connector
            .validateResolvedEndpoint(
                "https://attacker.example.com/anything?/v1/chat/completions",
                Arrays.asList("^https://api\\.openai\\.com/.*$")
            );
    }

    @Test
    public void validateResolvedEndpoint_emptyAllowlist_rejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Trusted connector endpoints regex is not configured");
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint("https://api.openai.com/v1/chat/completions", Collections.emptyList());
    }

    @Test
    public void validateResolvedEndpoint_nullAllowlist_rejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Trusted connector endpoints regex is not configured");
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint("https://api.openai.com/v1/chat/completions", null);
    }

    @Test
    public void validateResolvedEndpoint_nullResolvedUrl_rejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Resolved connector URL is null");
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint(null, Arrays.asList("^https://api\\.openai\\.com/.*$"));
    }

    @Test
    public void validateResolvedEndpoint_awsDefaultsAllowRegionalHosts() {
        HttpConnector connector = createHttpConnector();
        List<String> defaults = defaults();
        connector.validateResolvedEndpoint("https://bedrock-runtime.us-east-1.amazonaws.com/model/m/invoke", defaults);
        connector.validateResolvedEndpoint("https://runtime.sagemaker.us-west-2.amazonaws.com/endpoints/x/invocations", defaults);
        connector.validateResolvedEndpoint("https://api.sagemaker.eu-west-1.amazonaws.com/endpoints", defaults);
        connector.validateResolvedEndpoint("https://bedrock-agent-runtime.us-east-1.amazonaws.com/agents/a", defaults);
        connector.validateResolvedEndpoint("https://bedrock.us-gov-west-1.amazonaws.com/foundation-models", defaults);
        connector.validateResolvedEndpoint("https://textract.us-east-1.amazonaws.com", defaults);
        connector.validateResolvedEndpoint("https://comprehend.eusc-de-east-1.amazonaws.com", defaults);
        connector.validateResolvedEndpoint("https://rekognition.us-east-1.amazonaws.com", defaults);
        connector.validateResolvedEndpoint("https://rekognition-fips.us-east-1.amazonaws.com", defaults);
    }

    @Test
    public void validateResolvedEndpoint_awsDefaultsRejectHostCrossingAndDottedBucket() {
        HttpConnector connector = createHttpConnector();
        List<String> defaults = defaults();
        assertRejected(connector, "https://bedrock-runtime.attacker.example/x.amazonaws.com/model/m/invoke", defaults);
        assertRejected(connector, "https://bedrock-runtime.evil.example?x=a.amazonaws.com/model/m/invoke", defaults);
        assertRejected(connector, "https://bedrock-runtime.evil.s3.amazonaws.com/x", defaults);
        assertRejected(connector, "https://bedrock-runtime.foo.us-east-1.amazonaws.com/x", defaults);
    }

    @Test
    public void validateResolvedEndpoint_awsDefaultsRejectS3BucketHosts() {
        HttpConnector connector = createHttpConnector();
        List<String> defaults = defaults();
        assertRejected(connector, "https://bedrock-runtime.s3.amazonaws.com/x", defaults);
        assertRejected(connector, "https://bedrock-agent-runtime.s3.amazonaws.com/x", defaults);
        assertRejected(connector, "https://bedrock-runtime.s3-accelerate.amazonaws.com/x", defaults);
        assertRejected(connector, "https://textract.s3-external-1.amazonaws.com", defaults);
        assertRejected(connector, "https://runtime.sagemaker.s3.amazonaws.com/x", defaults);
    }

    @Test
    public void validateResolvedEndpoint_awsDefaultsAcceptRegionNamesInAllPartitions() {
        HttpConnector connector = createHttpConnector();
        List<String> defaults = defaults();
        for (String region : List
            .of(
                "us-east-1",
                "us-gov-west-1",
                "cn-northwest-1",
                "eusc-de-east-1",
                "us-isob-east-1",
                "eu-isoe-west-1",
                "ap-southeast-7",
                "il-central-1"
            )) {
            connector.validateResolvedEndpoint("https://bedrock-runtime." + region + ".amazonaws.com/model/m/invoke", defaults);
            connector.validateResolvedEndpoint("https://rekognition-fips." + region + ".amazonaws.com", defaults);
            connector.validateResolvedEndpoint("https://textract." + region + ".amazonaws.com", defaults);
        }
    }

    @Test
    public void validateResolvedEndpoint_rejectsUserInfoFragmentAndBadUri() {
        HttpConnector connector = createHttpConnector();
        List<String> allow = Arrays.asList("^https://.*$");
        expectMessage(connector, "https://x@evil.example/path", allow, "Connector URL must not contain user information");
        expectMessage(connector, "https://api.openai.com/v1/chat#x", allow, "Connector URL must not contain a fragment");
        expectMessage(
            connector,
            "https://bedrock-runtime.evil.example#a.amazonaws.com/x",
            allow,
            "Connector URL must not contain a fragment"
        );
        expectMessage(connector, "ftp://api.openai.com/v1", allow, "Connector URL scheme must be http or https");
        expectMessage(connector, "https://evil_host/a", allow, "Connector URL has no host");
        expectMessage(connector, "https://host/path with space", allow, "Connector URL is not a valid URI");
    }

    @Test
    public void validateResolvedEndpoint_preservesQueryAndWellFormedText() throws Exception {
        HttpConnector connector = createHttpConnector();
        String url = "https://example.openai.azure.com/openai/deployments/d/chat/completions?api-version=2024-10-21";
        connector.validateResolvedEndpoint(url, Arrays.asList("^https://example\\.openai\\.azure\\.com/.*$"));
        URI uri = new URI(url);
        String canonical = uri.getScheme() + "://" + uri.getHost() + uri.getRawPath() + "?" + uri.getRawQuery();
        assertEquals(url, canonical);
        String ipv6 = "https://[2001:db8::1]/v1";
        connector.validateResolvedEndpoint(ipv6, Arrays.asList("^https://\\[2001:db8::1\\]/v1$"));
    }

    /**
     * Predict-time host templates. The three forms below used to satisfy the stock Bedrock pattern
     * because the match ran on the raw string. Credit for the cases: independent external report, 2026-11-03.
     */
    @Test
    public void validateResolvedEndpoint_bedrockHostTemplateGapRejected() {
        HttpConnector connector = createHttpConnector();
        String template = "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/m/invoke";
        assertRejected(connector, template.replace("${parameters.region}", "evil.example?x=a"), defaults());
        expectMessage(
            connector,
            template.replace("${parameters.region}", "evil.example#a"),
            defaults(),
            "Connector URL must not contain a fragment"
        );
        expectMessage(
            connector,
            template.replace("${parameters.region}", "x@evil.example?a"),
            defaults(),
            "Connector URL must not contain user information"
        );
        connector.validateResolvedEndpoint(template.replace("${parameters.region}", "us-east-1"), defaults());
    }

    @Test
    public void blueprintPredictUrlsMatchTightenedDefaults() throws IOException {
        Path docs = Path.of("..", "docs", "remote_inference_blueprints");
        Assume.assumeTrue("blueprint docs not found at " + docs.toAbsolutePath(), Files.isDirectory(docs));
        int checked = 0;
        Pattern urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"(https://[^\"]+)\"");
        HttpConnector connector = createHttpConnector();
        try (Stream<Path> files = Files.walk(docs)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".md"))::iterator) {
                String text = Files.readString(file);
                Matcher matcher = urlPattern.matcher(text);
                while (matcher.find()) {
                    String resolved = matcher.group(1).replace("${parameters.region}", "us-east-1");
                    if (resolved.contains("${") || resolved.contains("{") || resolved.contains("}")) {
                        continue;
                    }
                    String host = resolved.substring(resolved.indexOf("://") + 3);
                    int slash = host.indexOf('/');
                    if (slash >= 0) {
                        host = host.substring(0, slash);
                    }
                    int colon = host.indexOf(':');
                    if (colon >= 0) {
                        host = host.substring(0, colon);
                    }
                    if (!(host.endsWith("amazonaws.com")
                        || host.endsWith("openai.com")
                        || host.endsWith("cohere.ai")
                        || host.endsWith("deepseek.com")
                        || host.endsWith("googleapis.com"))) {
                        continue;
                    }
                    try {
                        connector.validateResolvedEndpoint(resolved, defaults());
                    } catch (IllegalArgumentException e) {
                        fail(file + " url " + resolved + " rejected: " + e.getMessage());
                    }
                    checked++;
                }
            }
        }
        assertTrue("no blueprint URLs were checked", checked > 0);
    }

    @Test
    public void validateResolvedEndpoint_rejectsUnresolvedPlaceholder() {
        expectMessage(
            createHttpConnector(),
            "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/m/invoke",
            defaults(),
            "Connector URL contains an unresolved parameter placeholder; define it in the connector parameters"
        );
    }

    @Test
    public void validateResolvedEndpoint_schemeAndHostAreCaseInsensitive() {
        createHttpConnector().validateResolvedEndpoint("HTTPS://Bedrock-Runtime.US-EAST-1.amazonaws.com/model/m/invoke", defaults());
        assertRejected(createHttpConnector(), "HTTPS://Bedrock-Runtime.Evil.Example/x.amazonaws.com/model/m/invoke", defaults());
    }

    @Test
    public void rejectedUrlLogCarriesOnlySchemeHostAndLength() {
        String withUserInfo = "https://user:pass@Host.Example/x";
        String described = RejectedConnectorUrlLog.describe(withUserInfo);
        assertEquals("https://host.example (length " + withUserInfo.length() + ")", described);
        assertFalse(described.contains("user"));
        assertFalse(described.contains("pass"));
        String withQuery = "https://h.example/path?token=secret#frag";
        assertEquals("https://h.example (length " + withQuery.length() + ")", RejectedConnectorUrlLog.describe(withQuery));
        String withControl = "https://h.example/a\r\nFAKE LOG LINE";
        assertEquals("unparseable (length " + withControl.length() + ")", RejectedConnectorUrlLog.describe(withControl));
        assertEquals("unparseable (length 6)", RejectedConnectorUrlLog.describe("${x}/a"));
        assertEquals("null", RejectedConnectorUrlLog.describe(null));
        String longUrl = "https://h.example/" + "a".repeat(2000);
        assertEquals("https://h.example (length " + longUrl.length() + ")", RejectedConnectorUrlLog.describe(longUrl));
    }

    private static HttpConnector connectorWithUrl(String url, java.util.Map<String, String> parameters) {
        ConnectorAction action = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url(url)
            .requestBody("{}")
            .build();
        return HttpConnector.builder().name("n").protocol("http").parameters(parameters).actions(List.of(action)).build();
    }

    @Test
    public void validateConnectorURL_acceptsPredictTimePlaceholdersInPathAndQuery() {
        java.util.Map<String, String> parameters = java.util.Map.of("region", "us-east-1");
        connectorWithUrl("https://bedrock-runtime.us-east-1.amazonaws.com/model/${parameters.model}/invoke", parameters)
            .validateConnectorURL(defaults());
        connectorWithUrl("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke", parameters)
            .validateConnectorURL(defaults());
        connectorWithUrl("https://bedrock-runtime.us-east-1.amazonaws.com/model/m/invoke?x=${parameters.x}", parameters)
            .validateConnectorURL(defaults());
    }

    @Test
    public void validateConnectorURL_rejectsUnresolvedHostPlaceholder() {
        try {
            connectorWithUrl("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/m/invoke", Collections.emptyMap())
                .validateConnectorURL(defaults());
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("unresolved parameter placeholder"));
        }
    }

    @Test
    public void validateResolvedEndpoint_stillRejectsUnresolvedPathPlaceholder() {
        expectMessage(
            createHttpConnector(),
            "https://bedrock-runtime.us-east-1.amazonaws.com/model/${parameters.model}/invoke",
            defaults(),
            "unresolved parameter placeholder"
        );
    }

    @Test
    public void fillPathPlaceholders_onlyTouchesPathAndQuery() {
        assertEquals("https://h/a/0/b?c=0", Connector.fillPathPlaceholders("https://h/a/${parameters.x}/b?c=${parameters.y}"));
        assertEquals("https://h?c=0", Connector.fillPathPlaceholders("https://h?c=${parameters.y}"));
        assertEquals("https://${parameters.h}/0", Connector.fillPathPlaceholders("https://${parameters.h}/${parameters.p}"));
        assertEquals("${parameters.s}://h/a", Connector.fillPathPlaceholders("${parameters.s}://h/a"));
        assertEquals("no-scheme/${x}", Connector.fillPathPlaceholders("no-scheme/${x}"));
    }

    @Test
    public void validateConnectorURL_rejectsPlaceholderThatPredictTimeNeverFills() {
        assertEquals("https://h/${model}/0", Connector.fillPathPlaceholders("https://h/${model}/${parameters.p}"));
        List<String> allow = new java.util.ArrayList<>(defaults());
        allow.add("^https://h/.*$");
        for (String url : Arrays
            .asList("https://h/${model}", "https://bedrock-runtime.us-east-1.amazonaws.com/model/${model}/invoke?x=${parameters.x}")) {
            try {
                connectorWithUrl(url, Collections.emptyMap()).validateConnectorURL(allow);
                fail("expected rejection for " + url);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("unresolved parameter placeholder"));
            }
        }
    }

    private static final String PREDICT = ConnectorAction.ActionType.PREDICT.name();
    private static final List<String> ANY_URL = List.of(".*");
    private static final String ENDPOINT_URL = "https://${parameters.endpoint}/v1/chat/completions";
    private static final Map<String, String> STORED_ENDPOINT = Map.of("endpoint", "api.openai.com");

    @Test
    public void resolveActionEndpoint_requestParametersFillPathAndQuery() {
        HttpConnector connector = connectorWithUrl(
            "https://${parameters.endpoint}/v1/${parameters.model}?api-version=${parameters.v}",
            STORED_ENDPOINT
        );
        Map<String, String> request = Map.of("endpoint", "api.openai.com", "model", "gpt-4o", "v", "2024-10-21");

        assertEquals(
            URI.create("https://api.openai.com/v1/gpt-4o?api-version=2024-10-21"),
            connector.resolveActionEndpoint(PREDICT, request, ANY_URL)
        );
    }

    @Test
    public void resolveActionEndpoint_requestParametersCannotChangeSchemeHostOrPort() {
        assertEquals(
            "The scheme, host and port of a connector action URL come from the connector's own parameters; request parameters "
                + "cannot change them. Store the parameters that set them on the connector, or on the model for a connector "
                + "defined inside it",
            Connector.ACTION_URL_HOST_CHANGED_MESSAGE
        );
        assertHostChanged(ENDPOINT_URL, STORED_ENDPOINT, Map.of("endpoint", "api.example.com"));
        assertHostChanged(ENDPOINT_URL, STORED_ENDPOINT, Map.of("endpoint", "api.openai.com:8443"));
        assertHostChanged("${parameters.scheme}://api.openai.com/v1", Map.of("scheme", "https"), Map.of("scheme", "http"));
    }

    @Test
    public void resolveActionEndpoint_comparesCaseInsensitiveHostAndDefaultPort() {
        HttpConnector connector = connectorWithUrl(ENDPOINT_URL, STORED_ENDPOINT);
        assertEquals(
            URI.create("https://api.openai.com:443/v1/chat/completions"),
            connector.resolveActionEndpoint(PREDICT, Map.of("endpoint", "api.openai.com:443"), ANY_URL)
        );
        assertEquals(
            URI.create("https://API.OpenAI.com/v1/chat/completions"),
            connector.resolveActionEndpoint(PREDICT, Map.of("endpoint", "API.OpenAI.com"), ANY_URL)
        );
        Map<String, String> storedIpv6 = Map.of("endpoint", "[2001:db8::1]");
        assertEquals(
            URI.create("https://[2001:DB8::1]/v1/chat/completions"),
            connectorWithUrl(ENDPOINT_URL, storedIpv6).resolveActionEndpoint(PREDICT, Map.of("endpoint", "[2001:DB8::1]"), ANY_URL)
        );
        assertHostChanged(ENDPOINT_URL, storedIpv6, Map.of("endpoint", "[2001:db8:0::1]"));
        String httpUrl = "http://${parameters.endpoint}/v1";
        assertEquals(
            URI.create("http://api.openai.com:80/v1"),
            connectorWithUrl(httpUrl, STORED_ENDPOINT).resolveActionEndpoint(PREDICT, Map.of("endpoint", "api.openai.com:80"), ANY_URL)
        );
        assertHostChanged(httpUrl, STORED_ENDPOINT, Map.of("endpoint", "api.openai.com:443"));
    }

    @Test
    public void resolveActionEndpoint_comparesTheHostTheParserReports() {
        for (String value : List.of("evil.example?x=api.openai.com", "evil.example/api.openai.com", "api.openai.com.")) {
            assertHostChanged(ENDPOINT_URL, STORED_ENDPOINT, Map.of("endpoint", value));
        }
        HttpConnector connector = connectorWithUrl(ENDPOINT_URL, STORED_ENDPOINT);
        // Refused before the comparison by validateResolvedEndpoint, whose messages its own tests pin.
        assertThrows(
            IllegalArgumentException.class,
            () -> connector.resolveActionEndpoint(PREDICT, Map.of("endpoint", "api.openai.com@evil.example"), ANY_URL)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> connector.resolveActionEndpoint(PREDICT, Map.of("endpoint", "evil.example#a"), ANY_URL)
        );
    }

    @Test
    public void resolveActionEndpoint_requestParameterMayAddPathAfterHost() {
        assertEquals(
            URI.create("https://api.openai.com/other/v1/chat/completions"),
            connectorWithUrl(ENDPOINT_URL, STORED_ENDPOINT)
                .resolveActionEndpoint(PREDICT, Map.of("endpoint", "api.openai.com/other"), ANY_URL)
        );
    }

    @Test
    public void resolveActionEndpoint_failsWhenStoredParametersGiveNoSchemeOrHost() {
        assertHostChanged(ENDPOINT_URL, Map.of(), Map.of("endpoint", "api.openai.com"));
        Map<String, String> tenantHost = Map.of("endpoint", "${parameters.tenant}.example.com");
        assertHostChanged(ENDPOINT_URL, tenantHost, Map.of("endpoint", "${parameters.tenant}.example.com", "tenant", "a"));
        assertThrows(
            IllegalArgumentException.class,
            () -> connectorWithUrl(ENDPOINT_URL, Map.of()).resolveActionEndpoint(PREDICT, Map.of(), ANY_URL)
        );
    }

    @Test
    public void resolveActionEndpoint_readsOnlyTheStoredSchemeAndAuthority() {
        assertEquals(
            URI.create("https://api.x.com/ab"),
            connectorWithUrl("https://api.x.com/${parameters.model}", Map.of("model", "a b"))
                .resolveActionEndpoint(PREDICT, Map.of("model", "ab"), ANY_URL)
        );
    }

    @Test
    public void parameterCycleIsRejectedWithoutNamingParameters() throws IOException {
        assertEquals("Connector URL parameters refer to each other in a cycle", Connector.URL_PARAMETER_CYCLE_MESSAGE);
        String url = "https://api.x.com/${parameters.a}";
        Map<String, String> cycle = Map.of("a", "${parameters.b}", "b", "${parameters.a}");
        assertParameterCycle(() -> connectorWithUrl(url, Map.of()).resolveActionEndpoint(PREDICT, cycle, ANY_URL));
        Connector stored = readConnector(url, cycle);
        assertParameterCycle(() -> stored.resolveActionEndpoint(PREDICT, Map.of("a", "x", "b", "y"), ANY_URL));
        assertParameterCycle(() -> stored.validateConnectorURL(ANY_URL));
        assertParameterCycle(() -> connectorWithUrl(url, cycle));
    }

    @Test
    public void resolveActionEndpoint_hostParameterThatCarriesTheScheme() {
        String url = "${parameters.endpoint}/_plugins/x/${parameters.id}";
        Map<String, String> stored = Map.of("endpoint", "https://memory.example.com");
        assertEquals(
            URI.create("https://memory.example.com/_plugins/x/1"),
            connectorWithUrl(url, stored)
                .resolveActionEndpoint(PREDICT, Map.of("endpoint", "https://memory.example.com", "id", "1"), ANY_URL)
        );
        assertHostChanged(url, stored, Map.of("endpoint", "https://other.example.com", "id", "1"));
    }

    /** A connector as read back from the index, which does not run the checks of the builder. */
    private static Connector readConnector(String url, Map<String, String> parameters) throws IOException {
        Map<String, Object> action = Map.of("action_type", "PREDICT", "method", "POST", "url", url, "request_body", "{}");
        String json = StringUtils.gson
            .toJson(Map.of("name", "n", "version", "1", "protocol", "http", "parameters", parameters, "actions", List.of(action)));
        XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, json);
        parser.nextToken();
        return new HttpConnector("http", parser);
    }

    private static void assertHostChanged(String url, Map<String, String> stored, Map<String, String> request) {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> connectorWithUrl(url, stored).resolveActionEndpoint(PREDICT, request, ANY_URL)
        );
        assertEquals(request.toString(), Connector.ACTION_URL_HOST_CHANGED_MESSAGE, e.getMessage());
    }

    private static void assertParameterCycle(ThrowingRunnable call) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
        assertEquals(Connector.URL_PARAMETER_CYCLE_MESSAGE, e.getMessage());
        assertNull(e.getCause());
    }

    private static List<String> defaults() {
        return MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(Settings.EMPTY);
    }

    private static void assertRejected(HttpConnector connector, String url, List<String> allow) {
        try {
            connector.validateResolvedEndpoint(url, allow);
            fail("expected rejection for " + url);
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage().contains("not matching")
                    || expected.getMessage().contains("fragment")
                    || expected.getMessage().contains("user information")
            );
        }
    }

    private static void expectMessage(HttpConnector connector, String url, List<String> allow, String message) {
        try {
            connector.validateResolvedEndpoint(url, allow);
            fail("expected rejection for " + url);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
