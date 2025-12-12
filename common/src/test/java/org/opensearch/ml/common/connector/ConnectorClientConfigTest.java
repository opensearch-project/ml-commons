package org.opensearch.ml.common.connector;

import java.io.IOException;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class ConnectorClientConfigTest {

    @Test
    public void writeTo_ReadFromStream() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig
            .builder()
            .maxConnections(10)
            .connectionTimeout(5000)
            .readTimeout(3000)
            .retryBackoffMillis(123)
            .retryTimeoutSeconds(456)
            .maxRetryTimes(789)
            .retryBackoffPolicy(RetryBackoffPolicy.CONSTANT)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        config.writeTo(output);
        ConnectorClientConfig readConfig = new ConnectorClientConfig(output.bytes().streamInput());

        Assert.assertEquals(config, readConfig);
    }

    @Test
    public void writeTo_ReadFromStream_nullValues() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig.builder().build();

        BytesStreamOutput output = new BytesStreamOutput();
        config.writeTo(output);
        ConnectorClientConfig readConfig = new ConnectorClientConfig(output.bytes().streamInput());

        Assert.assertEquals(config, readConfig);
    }

    @Test
    public void writeTo_ReadFromStream_diffVersionThenNotProcessRetryOptions() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig
            .builder()
            .maxConnections(10)
            .connectionTimeout(5000)
            .readTimeout(3000)
            .retryBackoffMillis(123)
            .retryTimeoutSeconds(456)
            .maxRetryTimes(789)
            .retryBackoffPolicy(RetryBackoffPolicy.CONSTANT)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(Version.V_2_14_0);
        config.writeTo(output);
        StreamInput input = output.bytes().streamInput();
        input.setVersion(Version.V_2_14_0);
        ConnectorClientConfig readConfig = ConnectorClientConfig.fromStream(input);

        Assert.assertEquals(Integer.valueOf(10), readConfig.getMaxConnections());
        Assert.assertEquals(Integer.valueOf(5000), readConfig.getConnectionTimeout());
        Assert.assertEquals(Integer.valueOf(3000), readConfig.getReadTimeout());
        Assert.assertNull(readConfig.getRetryBackoffMillis());
        Assert.assertNull(readConfig.getRetryTimeoutSeconds());
        Assert.assertNull(readConfig.getMaxRetryTimes());
        Assert.assertNull(readConfig.getRetryBackoffPolicy());
    }

    @Test
    public void toXContent() throws IOException {
        ConnectorClientConfig config = ConnectorClientConfig
            .builder()
            .maxConnections(10)
            .connectionTimeout(5000)
            .readTimeout(3000)
            .retryBackoffMillis(123)
            .retryTimeoutSeconds(456)
            .maxRetryTimes(789)
            .retryBackoffPolicy(RetryBackoffPolicy.CONSTANT)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        String expectedJson = "{\"max_connection\":10,\"connection_timeout\":5000,\"read_timeout\":3000,"
            + "\"retry_backoff_millis\":123,\"retry_timeout_seconds\":456,\"max_retry_times\":789,\"retry_backoff_policy\":\"constant\"}";
        Assert.assertEquals(expectedJson, content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"max_connection\":10,\"connection_timeout\":5000,\"read_timeout\":3000,"
            + "\"retry_backoff_millis\":123,\"retry_timeout_seconds\":456,\"max_retry_times\":789,\"retry_backoff_policy\":\"constant\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        ConnectorClientConfig config = ConnectorClientConfig.parse(parser);

        Assert.assertEquals(Integer.valueOf(10), config.getMaxConnections());
        Assert.assertEquals(Integer.valueOf(5000), config.getConnectionTimeout());
        Assert.assertEquals(Integer.valueOf(3000), config.getReadTimeout());
        Assert.assertEquals(Integer.valueOf(123), config.getRetryBackoffMillis());
        Assert.assertEquals(Integer.valueOf(456), config.getRetryTimeoutSeconds());
        Assert.assertEquals(Integer.valueOf(789), config.getMaxRetryTimes());
        Assert.assertEquals(RetryBackoffPolicy.CONSTANT, config.getRetryBackoffPolicy());
    }

    @Test
    public void parse_whenMalformedBackoffPolicy_thenFail() throws IOException {
        String jsonStr = "{\"max_connection\":10,\"connection_timeout\":5000,\"read_timeout\":3000,"
            + "\"retry_backoff_millis\":123,\"retry_timeout_seconds\":456,\"max_retry_times\":789,\"retry_backoff_policy\":\"test\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> ConnectorClientConfig.parse(parser));
        Assert.assertEquals("Unsupported retry backoff policy", exception.getMessage());
    }

    @Test
    public void testDefaultValues() {
        ConnectorClientConfig config = ConnectorClientConfig.builder().build();

        Assert.assertNull(config.getMaxConnections());
        Assert.assertNull(config.getConnectionTimeout());
        Assert.assertNull(config.getReadTimeout());
        Assert.assertNull(config.getRetryBackoffMillis());
        Assert.assertNull(config.getRetryTimeoutSeconds());
        Assert.assertNull(config.getMaxRetryTimes());
        Assert.assertNull(config.getRetryBackoffPolicy());
    }

    @Test
    public void testDefaultValuesInitByNewInstance() {
        ConnectorClientConfig config = new ConnectorClientConfig();

        Assert.assertEquals(Integer.valueOf(30), config.getMaxConnections());
        Assert.assertEquals(Integer.valueOf(1), config.getConnectionTimeout());
        Assert.assertEquals(Integer.valueOf(10), config.getReadTimeout());
        Assert.assertEquals(Integer.valueOf(200), config.getRetryBackoffMillis());
        Assert.assertEquals(Integer.valueOf(30), config.getRetryTimeoutSeconds());
        Assert.assertEquals(Integer.valueOf(0), config.getMaxRetryTimes());
        Assert.assertEquals(RetryBackoffPolicy.CONSTANT, config.getRetryBackoffPolicy());
    }
}
