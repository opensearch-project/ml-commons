package org.opensearch.ml.common.input.remote;

import java.io.IOException;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.search.SearchModule;

public class RemoteInferenceMLInputTest {

    @Test
    public void constructor_parser() throws IOException {
        RemoteInferenceMLInput input = createRemoteInferenceMLInput();
        Assert.assertNotNull(input.getInputDataset());
        Assert.assertEquals(MLInputDataType.REMOTE, input.getInputDataset().getInputDataType());
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) input.getInputDataset();
        Assert.assertEquals(1, inputDataSet.getParameters().size());
        Assert.assertEquals("hello world", inputDataSet.getParameters().get("prompt"));
    }

    @Test
    public void constructor_stream() throws IOException {
        RemoteInferenceMLInput originalInput = createRemoteInferenceMLInput();
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);

        RemoteInferenceMLInput input = new RemoteInferenceMLInput(output.bytes().streamInput());
        Assert.assertNotNull(input.getInputDataset());
        Assert.assertEquals(MLInputDataType.REMOTE, input.getInputDataset().getInputDataType());
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) input.getInputDataset();
        Assert.assertEquals(1, inputDataSet.getParameters().size());
        Assert.assertEquals("hello world", inputDataSet.getParameters().get("prompt"));
    }

    private static RemoteInferenceMLInput createRemoteInferenceMLInput() throws IOException {
        String jsonStr = "{ \"parameters\": { \"prompt\": \"hello world\" } }";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        RemoteInferenceMLInput input = new RemoteInferenceMLInput(parser, FunctionName.REMOTE);
        return input;
    }
}
