/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

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
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLModelGroupTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void toXContent_NullName() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("model group name must not be null");

        MLModelGroup.builder().build();
    }

    @Test
    public void toXContent_Empty() throws IOException {
        MLModelGroup modelGroup = MLModelGroup.builder().name("test").build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        modelGroup.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{\"name\":\"test\",\"latest_version\":0}", content);
    }

    @Test
    public void toXContent() throws IOException {
        MLModelGroup modelGroup = MLModelGroup
            .builder()
            .name("test")
            .description("this is test group")
            .latestVersion(1)
            .backendRoles(Arrays.asList("role1", "role2"))
            .owner(new User())
            .access(AccessMode.PUBLIC.name())
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        modelGroup.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert
            .assertEquals(
                "{\"name\":\"test\",\"latest_version\":1,\"description\":\"this is test group\","
                    + "\"backend_roles\":[\"role1\",\"role2\"],"
                    + "\"owner\":{\"name\":\"\",\"backend_roles\":[],\"roles\":[],\"custom_attribute_names\":[],\"user_requested_tenant\":null},"
                    + "\"access\":\"PUBLIC\"}",
                content
            );
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"latest_version\":1,\"description\":\"this is test group\","
            + "\"backend_roles\":[\"role1\",\"role2\"],"
            + "\"owner\":{\"name\":\"\",\"backend_roles\":[],\"roles\":[],\"custom_attribute_names\":[],\"user_requested_tenant\":null},"
            + "\"access\":\"PUBLIC\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLModelGroup modelGroup = MLModelGroup.parse(parser);
        Assert.assertEquals("test", modelGroup.getName());
        Assert.assertEquals("this is test group", modelGroup.getDescription());
        Assert.assertEquals("PUBLIC", modelGroup.getAccess());
        Assert.assertEquals(2, modelGroup.getBackendRoles().size());
        Assert.assertEquals("role1", modelGroup.getBackendRoles().get(0));
        Assert.assertEquals("role2", modelGroup.getBackendRoles().get(1));
    }

    @Test
    public void parse_Empty() throws IOException {
        String jsonStr = "{\"name\":\"test\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLModelGroup modelGroup = MLModelGroup.parse(parser);
        Assert.assertEquals("test", modelGroup.getName());
        Assert.assertNull(modelGroup.getBackendRoles());
        Assert.assertNull(modelGroup.getAccess());
        Assert.assertNull(modelGroup.getOwner());
    }

    @Test
    public void writeTo() throws IOException {
        MLModelGroup originalModelGroup = MLModelGroup
            .builder()
            .name("test")
            .description("this is test group")
            .latestVersion(1)
            .backendRoles(Arrays.asList("role1", "role2"))
            .owner(new User())
            .access(AccessMode.PUBLIC.name())
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        originalModelGroup.writeTo(output);
        MLModelGroup modelGroup = new MLModelGroup(output.bytes().streamInput());
        Assert.assertEquals("test", modelGroup.getName());
        Assert.assertEquals("this is test group", modelGroup.getDescription());
        Assert.assertEquals("PUBLIC", modelGroup.getAccess());
        Assert.assertEquals(2, modelGroup.getBackendRoles().size());
        Assert.assertEquals("role1", modelGroup.getBackendRoles().get(0));
        Assert.assertEquals("role2", modelGroup.getBackendRoles().get(1));
    }

    @Test
    public void writeTo_Empty() throws IOException {
        MLModelGroup originalModelGroup = MLModelGroup.builder().name("test").build();

        BytesStreamOutput output = new BytesStreamOutput();
        originalModelGroup.writeTo(output);
        MLModelGroup modelGroup = new MLModelGroup(output.bytes().streamInput());
        Assert.assertEquals("test", modelGroup.getName());
        Assert.assertNull(modelGroup.getBackendRoles());
        Assert.assertNull(modelGroup.getAccess());
        Assert.assertNull(modelGroup.getOwner());
    }
}
