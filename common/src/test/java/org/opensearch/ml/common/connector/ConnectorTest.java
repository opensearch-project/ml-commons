package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.HttpConnectorTest.createHttpConnector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class ConnectorTest {
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
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
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
        });
        assertEquals("Connector URL is not matching the trusted connector endpoint regex", exception.getMessage());
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
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpConnector connector = createHttpConnector();
            connector
                .validateResolvedEndpoint(
                    "https://attacker.example.com/anything?/v1/chat/completions",
                    Arrays.asList("^https://api\\.openai\\.com/.*$")
                );
        });
        assertEquals("Connector URL is not matching the trusted connector endpoint regex", exception.getMessage());
    }

    @Test
    public void validateResolvedEndpoint_emptyAllowlist_rejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpConnector connector = createHttpConnector();
            connector.validateResolvedEndpoint("https://api.openai.com/v1/chat/completions", Collections.emptyList());
        });
        assertEquals(
            "Trusted connector endpoints regex is not configured. Please set plugins.ml_commons.trusted_connector_endpoints_regex.",
            exception.getMessage()
        );
    }

    @Test
    public void validateResolvedEndpoint_nullAllowlist_rejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpConnector connector = createHttpConnector();
            connector.validateResolvedEndpoint("https://api.openai.com/v1/chat/completions", null);
        });
        assertEquals(
            "Trusted connector endpoints regex is not configured. Please set plugins.ml_commons.trusted_connector_endpoints_regex.",
            exception.getMessage()
        );
    }

    @Test
    public void validateResolvedEndpoint_nullResolvedUrl_rejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpConnector connector = createHttpConnector();
            connector.validateResolvedEndpoint(null, Arrays.asList("^https://api\\.openai\\.com/.*$"));
        });
        assertEquals("Resolved connector URL is null", exception.getMessage());
    }
}
