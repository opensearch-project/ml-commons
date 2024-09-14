package org.opensearch.ml.common.output.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.output.MLOutputType;

public class ModelTensorOutputTest {

    Float[] value;
    ModelTensorOutput modelTensorOutput;

    @Before
    public void setUp() throws Exception {
        value = new Float[] { 1.0f, 2.0f, 3.0f };
        List<ModelTensors> outputs = new ArrayList<>();
        ModelTensor tensor = ModelTensor
            .builder()
            .data(value)
            .name("test")
            .shape(new long[] { 1, 3 })
            .dataType(MLResultDataType.FLOAT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();
        List<ModelTensor> mlModelTensors = Arrays.asList(tensor);
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(outputs).build();
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(modelTensorOutput, parsedTensorOutput -> {
            assertEquals(MLOutputType.MODEL_TENSOR, parsedTensorOutput.getType());
            assertEquals(1, parsedTensorOutput.getMlModelOutputs().size());
            ModelTensors modelTensors = parsedTensorOutput.getMlModelOutputs().get(0);
            assertEquals(1, modelTensors.getMlModelTensors().size());
            ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
            assertArrayEquals(value, modelTensor.getData());
        });
    }

    @Test
    public void readInputStream_NullField() throws IOException {
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().build();
        readInputStream(modelTensorOutput, parsedTensorOutput -> {
            assertEquals(MLOutputType.MODEL_TENSOR, parsedTensorOutput.getType());
            assertNull(parsedTensorOutput.getMlModelOutputs());
        });
    }

    private void readInputStream(ModelTensorOutput input, Consumer<ModelTensorOutput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLOutputType outputType = streamInput.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.MODEL_TENSOR, outputType);
        verify.accept(new ModelTensorOutput(streamInput));
    }
}
