package org.opensearch.ml.common.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchModule;
import org.opensearch.transport.client.Client;

public class ModelGuardrailTests {
    NamedXContentRegistry xContentRegistry;
    @Mock
    Client client;
    @Mock
    SdkClient sdkClient;
    String tenantId;

    Pattern regexPattern;
    ModelGuardrail modelGuardrail;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        xContentRegistry = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents());
        doNothing().when(this.client).execute(any(), any(), any());
        modelGuardrail = new ModelGuardrail("test_model_id", "$.test", "^accept$");
        tenantId = "tenant_id";
        regexPattern = Pattern.compile("^accept$");
    }

    @Test
    public void writeTo() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        modelGuardrail.writeTo(output);
        ModelGuardrail modelGuardrail1 = new ModelGuardrail(output.bytes().streamInput());

        Assert.assertEquals(modelGuardrail.getModelId(), modelGuardrail1.getModelId());
        Assert.assertEquals(modelGuardrail.getResponseFilter(), modelGuardrail1.getResponseFilter());
        Assert.assertEquals(modelGuardrail.getResponseAccept(), modelGuardrail1.getResponseAccept());
    }

    @Test
    public void validateParametersNull() {
        Assert.assertTrue(modelGuardrail.validate("test", null));
    }

    @Test
    public void validateParametersEmpty() {
        Assert.assertTrue(modelGuardrail.validate("test", Collections.emptyMap()));
    }

    @Test
    public void validateParametersEmpty1() {
        Assert.assertTrue(modelGuardrail.validate("test", Map.of("question", "")));
    }

    @Test
    public void init() {
        Assert.assertNull(modelGuardrail.getRegexAcceptPattern());
        modelGuardrail.init(xContentRegistry, client, sdkClient, tenantId);
        Assert.assertEquals(regexPattern.toString(), modelGuardrail.getRegexAcceptPattern().toString());
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        modelGuardrail.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"model_id\":\"test_model_id\",\"response_filter\":\"$.test\",\"response_validation_regex\":\"^accept$\"}",
                content
            );
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"model_id\":\"test_model_id\",\"response_filter\":\"$.test\",\"response_validation_regex\":\"^accept$\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        ModelGuardrail modelGuardrail1 = ModelGuardrail.parse(parser);

        Assert.assertEquals(modelGuardrail1.getModelId(), modelGuardrail.getModelId());
        Assert.assertEquals(modelGuardrail1.getResponseFilter(), modelGuardrail.getResponseFilter());
        Assert.assertEquals(modelGuardrail1.getResponseAccept(), modelGuardrail.getResponseAccept());
    }
}
