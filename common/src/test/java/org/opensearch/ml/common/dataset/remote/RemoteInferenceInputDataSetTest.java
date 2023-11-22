package org.opensearch.ml.common.dataset.remote;

import static org.opensearch.ml.common.dataset.MLInputDataType.REMOTE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.dataset.MLInputDataset;

public class RemoteInferenceInputDataSetTest {

    @Test
    public void writeTo_NullParameter() throws IOException {
        Map<String, String> parameters = null;
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();

        BytesStreamOutput output = new BytesStreamOutput();
        inputDataSet.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();

        RemoteInferenceInputDataSet inputDataSet2 = (RemoteInferenceInputDataSet) MLInputDataset.fromStream(streamInput);
        Assert.assertEquals(REMOTE, inputDataSet2.getInputDataType());
        Assert.assertNull(inputDataSet2.getParameters());
    }

    @Test
    public void writeTo() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "test value1");
        parameters.put("key2", "test value2");
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();

        BytesStreamOutput output = new BytesStreamOutput();
        inputDataSet.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();

        RemoteInferenceInputDataSet inputDataSet2 = (RemoteInferenceInputDataSet) MLInputDataset.fromStream(streamInput);
        Assert.assertEquals(REMOTE, inputDataSet2.getInputDataType());
        Assert.assertEquals(2, inputDataSet2.getParameters().size());
        Assert.assertEquals("test value1", inputDataSet2.getParameters().get("key1"));
        Assert.assertEquals("test value2", inputDataSet2.getParameters().get("key2"));
    }
}
