package org.opensearch.ml.common.connector;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.settings.Settings;
import org.opensearch.search.SearchModule;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.util.Collections;

public class ConnectorClientConfigTest {

    @Test
    public void writeTo_ReadFromStream() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder()
                .maxConnections(10)
                .connectionTimeout(5000)
                .readTimeout(3000)
                .build();

        BytesStreamOutput output = new BytesStreamOutput();
        config.writeTo(output);
        ConnectorClientConfig readConfig = new ConnectorClientConfig(output.bytes().streamInput());

        Assert.assertEquals(config, readConfig);
    }

    @Test
    public void toXContent() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder()
                .maxConnections(10)
                .connectionTimeout(5000)
                .readTimeout(3000)
                .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        String expectedJson = "{\"max_connection\":10,\"connection_timeout\":5000,\"read_timeout\":3000}";
        Assert.assertEquals(expectedJson, content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"max_connection\":10,\"connection_timeout\":5000,\"read_timeout\":3000}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();

        ConnectorClientConfig config = ConnectorClientConfig.parse(parser);

        Assert.assertEquals(Integer.valueOf(10), config.getMaxConnections());
        Assert.assertEquals(Integer.valueOf(5000), config.getConnectionTimeout());
        Assert.assertEquals(Integer.valueOf(3000), config.getReadTimeout());
    }

    @Test
    public void testDefaultValues() {
        ConnectorClientConfig config = ConnectorClientConfig.builder().build();

        Assert.assertNull(config.getMaxConnections());
        Assert.assertNull(config.getConnectionTimeout());
        Assert.assertNull(config.getReadTimeout());
    }
}

