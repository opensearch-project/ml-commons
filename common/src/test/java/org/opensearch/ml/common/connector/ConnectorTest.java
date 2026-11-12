/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
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
