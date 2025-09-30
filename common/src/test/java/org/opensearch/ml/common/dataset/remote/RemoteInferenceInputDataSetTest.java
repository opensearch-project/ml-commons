package org.opensearch.ml.common.dataset.remote;

import static org.opensearch.ml.common.dataset.MLInputDataType.REMOTE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
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

    @Test
    public void writeTo_withActionType() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "test value1");
        parameters.put("key2", "test value2");
        ActionType actionType = ActionType.from("predict");
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(parameters)
            .actionType(actionType)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        inputDataSet.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();

        RemoteInferenceInputDataSet inputDataSet2 = (RemoteInferenceInputDataSet) MLInputDataset.fromStream(streamInput);
        Assert.assertEquals(REMOTE, inputDataSet2.getInputDataType());
        Assert.assertEquals(2, inputDataSet2.getParameters().size());
        Assert.assertEquals("test value1", inputDataSet2.getParameters().get("key1"));
        Assert.assertEquals("test value2", inputDataSet2.getParameters().get("key2"));
        Assert.assertEquals("PREDICT", inputDataSet2.getActionType().toString());
    }

    @Test
    public void writeTo_withOptionalStringVersion() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "test value1");
        parameters.put("key2", null);
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_3_0);
        inputDataSet.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(CommonValue.VERSION_3_3_0);

        RemoteInferenceInputDataSet inputDataSet2 = (RemoteInferenceInputDataSet) MLInputDataset.fromStream(streamInput);
        Assert.assertEquals(REMOTE, inputDataSet2.getInputDataType());
        Assert.assertEquals(2, inputDataSet2.getParameters().size());
        Assert.assertEquals("test value1", inputDataSet2.getParameters().get("key1"));
        Assert.assertNull(inputDataSet2.getParameters().get("key2"));
    }

    @Test
    public void writeTo_withLegacyVersion() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "test value1");
        parameters.put("key2", "test value2");
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(Version.V_2_0_0);
        inputDataSet.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(Version.V_2_0_0);

        RemoteInferenceInputDataSet inputDataSet2 = (RemoteInferenceInputDataSet) MLInputDataset.fromStream(streamInput);
        Assert.assertEquals(REMOTE, inputDataSet2.getInputDataType());
        Assert.assertEquals(2, inputDataSet2.getParameters().size());
        Assert.assertEquals("test value1", inputDataSet2.getParameters().get("key1"));
        Assert.assertEquals("test value2", inputDataSet2.getParameters().get("key2"));
    }

    @Test(expected = NullPointerException.class)
    public void writeTo_withLegacyVersionAndNullValue() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "test value1");
        parameters.put("key2", null);
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(Version.V_3_1_0);
        inputDataSet.writeTo(output);
    }

}
