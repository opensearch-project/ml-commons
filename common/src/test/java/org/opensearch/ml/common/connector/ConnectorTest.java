package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.HttpConnectorTest.createHttpConnector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.Assert;
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

    // A nested quantifier like this passes MLCommonsSettings#validateRegexSafety but backtracks exponentially on a
    // non-matching input; the cost doubles with every extra character, so without a match budget matching the URL
    // below does not finish in any reasonable time (see issue #5078).
    private static final String CATASTROPHIC_REGEX = "^https://host/((a+)){1,100}/path$";
    private static final String CATASTROPHIC_URL = "https://host/" + "a".repeat(40) + "/nope";

    @Test(timeout = 10_000)
    public void validateResolvedEndpoint_catastrophicPattern_rejectedWithinBudget() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint(CATASTROPHIC_URL, Arrays.asList(CATASTROPHIC_REGEX));
    }

    @Test(timeout = 10_000)
    public void validateResolvedEndpoint_catastrophicPattern_laterPatternStillMatches() {
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint(CATASTROPHIC_URL, Arrays.asList(CATASTROPHIC_REGEX, "^https://host/.*$"));
    }

    @Test(timeout = 10_000)
    public void validateResolvedEndpoint_catastrophicPattern_matchingUrlStillAccepted() {
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint("https://host/" + "a".repeat(25) + "/path", Arrays.asList(CATASTROPHIC_REGEX));
    }

    @Test
    public void validateResolvedEndpoint_veryLongUrl_matches() {
        HttpConnector connector = createHttpConnector();
        String url = "https://bedrock-runtime.us-east-1.amazonaws.com/model/m/invoke?q=" + "x".repeat(500_000);
        connector.validateResolvedEndpoint(url, Arrays.asList("^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"));
    }

    @Test
    public void validateResolvedEndpoint_veryLongUrl_noMatch_rejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        HttpConnector connector = createHttpConnector();
        String url = "https://bedrock-runtime.evil.example.com/" + "x".repeat(500_000);
        connector.validateResolvedEndpoint(url, Arrays.asList("^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"));
    }

    @Test
    public void validateResolvedEndpoint_defaultTrustedEndpoints_match() {
        HttpConnector connector = createHttpConnector();
        List<String> defaults = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getDefault(Settings.EMPTY);
        for (String url : Arrays
            .asList(
                "https://api.openai.com/v1/chat/completions",
                "https://api.cohere.ai/v1/embed",
                "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke",
                "https://runtime.sagemaker.us-west-2.amazonaws.com/endpoints/my-endpoint/invocations"
            )) {
            connector.validateResolvedEndpoint(url, defaults);
        }
    }

    @Test
    public void validateResolvedEndpoint_defaultTrustedEndpoints_noMatch_rejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        HttpConnector connector = createHttpConnector();
        List<String> defaults = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getDefault(Settings.EMPTY);
        connector.validateResolvedEndpoint("https://attacker.example.com/v1/chat/completions", defaults);
    }

    @Test
    public void validateResolvedEndpoint_invalidRegex_throws() {
        exceptionRule.expect(PatternSyntaxException.class);
        HttpConnector connector = createHttpConnector();
        connector.validateResolvedEndpoint("https://api.openai.com/v1/chat/completions", Arrays.asList("^https://(unclosed"));
    }
}
