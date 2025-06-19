/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLConfigTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void toXContent_Minimal() throws IOException {
        MLConfig config = MLConfig.builder().type("test_type").build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{\"type\":\"test_type\"}", content);
    }

    @Test
    public void toXContent_Full() throws IOException {
        Instant now = Instant.now();
        Configuration configuration = Configuration.builder().build();
        MLConfig config = MLConfig
            .builder()
            .type("test_type")
            .configType("test_config_type")
            .configuration(configuration)
            .mlConfiguration(configuration)
            .createTime(now)
            .lastUpdateTime(now)
            .lastUpdatedTime(now)
            .tenantId("test_tenant")
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert
            .assertTrue(
                content.contains("\"type\":\"test_config_type\"")
                    && content.contains("\"configuration\":")
                    && content.contains("\"create_time\":" + now.toEpochMilli())
                    && content.contains("\"last_update_time\":" + now.toEpochMilli())
                    && content.contains("\"tenant_id\":\"test_tenant\"")
            );
    }

    @Test
    public void parse_Minimal() throws IOException {
        String jsonStr = "{\"type\":\"test_type\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLConfig config = MLConfig.parse(parser);
        Assert.assertEquals("test_type", config.getType());
        Assert.assertNull(config.getConfigType());
        Assert.assertNull(config.getConfiguration());
        Assert.assertNull(config.getMlConfiguration());
        Assert.assertNull(config.getCreateTime());
        Assert.assertNull(config.getLastUpdateTime());
        Assert.assertNull(config.getLastUpdatedTime());
        Assert.assertNull(config.getTenantId());
    }

    @Test
    public void parse_Full() throws IOException {
        String jsonStr = "{\"type\":\"test_type\",\"config_type\":\"test_config_type\","
            + "\"configuration\":{},\"ml_configuration\":{},\"create_time\":1672531200000,"
            + "\"last_update_time\":1672534800000,\"last_updated_time\":1672538400000,\"tenant_id\":\"test_tenant\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLConfig config = MLConfig.parse(parser);
        Assert.assertEquals("test_type", config.getType());
        Assert.assertEquals("test_config_type", config.getConfigType());
        Assert.assertNotNull(config.getConfiguration());
        Assert.assertNotNull(config.getMlConfiguration());
        Assert.assertEquals(Instant.ofEpochMilli(1672531200000L), config.getCreateTime());
        Assert.assertEquals(Instant.ofEpochMilli(1672534800000L), config.getLastUpdateTime());
        Assert.assertEquals(Instant.ofEpochMilli(1672538400000L), config.getLastUpdatedTime());
        Assert.assertEquals("test_tenant", config.getTenantId());
    }

    @Test
    public void writeToAndReadFrom() throws IOException {
        Instant now = Instant.now();
        Configuration configuration = Configuration.builder().build();
        MLConfig originalConfig = MLConfig
            .builder()
            .type("test_type")
            .configType("test_config_type")
            .configuration(configuration)
            .mlConfiguration(configuration)
            .createTime(now)
            .lastUpdateTime(now)
            .lastUpdatedTime(now)
            .tenantId("test_tenant")
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        originalConfig.writeTo(output);

        MLConfig deserializedConfig = new MLConfig(output.bytes().streamInput());
        Assert.assertEquals("test_type", deserializedConfig.getType());
        Assert.assertEquals("test_config_type", deserializedConfig.getConfigType());
        Assert.assertNotNull(deserializedConfig.getConfiguration());
        Assert.assertNotNull(deserializedConfig.getMlConfiguration());
        Assert.assertEquals(now, deserializedConfig.getCreateTime());
        Assert.assertEquals(now, deserializedConfig.getLastUpdateTime());
        Assert.assertEquals(now, deserializedConfig.getLastUpdatedTime());
        Assert.assertEquals("test_tenant", deserializedConfig.getTenantId());
    }

    @Test
    public void writeToAndReadFrom_Minimal() throws IOException {
        MLConfig originalConfig = MLConfig.builder().type("test_type").build();

        BytesStreamOutput output = new BytesStreamOutput();
        originalConfig.writeTo(output);

        MLConfig deserializedConfig = new MLConfig(output.bytes().streamInput());
        Assert.assertEquals("test_type", deserializedConfig.getType());
        Assert.assertNull(deserializedConfig.getConfigType());
        Assert.assertNull(deserializedConfig.getConfiguration());
        Assert.assertNull(deserializedConfig.getMlConfiguration());
        Assert.assertNull(deserializedConfig.getCreateTime());
        Assert.assertNull(deserializedConfig.getLastUpdateTime());
        Assert.assertNull(deserializedConfig.getLastUpdatedTime());
        Assert.assertNull(deserializedConfig.getTenantId());
    }

    @Test
    public void crossVersionSerialization_NoTenantId() throws IOException {
        // Simulate an older version (before VERSION_2_19_0)
        Version oldVersion = Version.V_2_18_0;

        // Create an MLConfig instance with tenantId set
        MLConfig originalConfig = MLConfig.builder().type("test_type").tenantId("test_tenant").build();

        // Serialize using the older version
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(oldVersion);
        originalConfig.writeTo(output);

        // Deserialize and verify tenantId is not present
        StreamInput input = output.bytes().streamInput();
        input.setVersion(oldVersion);
        MLConfig deserializedConfig = new MLConfig(input);

        Assert.assertEquals("test_type", deserializedConfig.getType());
        Assert.assertNull(deserializedConfig.getTenantId());
    }

    @Test
    public void crossVersionSerialization_WithTenantId() throws IOException {
        // Simulate a newer version (on or after VERSION_2_19_0)
        Version newVersion = Version.V_2_19_0;

        // Create an MLConfig instance with tenantId set
        MLConfig originalConfig = MLConfig.builder().type("test_type").tenantId("test_tenant").build();

        // Serialize using the newer version
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(newVersion);
        originalConfig.writeTo(output);

        // Deserialize and verify tenantId is present
        StreamInput input = output.bytes().streamInput();
        input.setVersion(newVersion);
        MLConfig deserializedConfig = new MLConfig(input);

        Assert.assertEquals("test_type", deserializedConfig.getType());
        Assert.assertEquals("test_tenant", deserializedConfig.getTenantId());
    }

}
