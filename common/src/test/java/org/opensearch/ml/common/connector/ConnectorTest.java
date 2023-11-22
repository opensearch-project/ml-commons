package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.HttpConnectorTest.createHttpConnector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

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
                        "^https://api\\.cohere\\.ai/.*$"
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
                        "^" + connector.getActions().get(0).getUrl()
                    )
            );
    }
}
