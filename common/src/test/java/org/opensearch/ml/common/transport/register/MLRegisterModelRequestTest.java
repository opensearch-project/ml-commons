package org.opensearch.ml.common.transport.register;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

public class MLRegisterModelRequestTest {

    private MLRegisterModelInput mlRegisterModelInput;

    @Before
    public void setUp() {

        TextEmbeddingModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .build();

        mlRegisterModelInput = mlRegisterModelInput
            .builder()
            .functionName(FunctionName.KMEANS)
            .modelName("modelName")
            .version("version")
            .modelGroupId("modelGroupId")
            .url("url")
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(config)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLRegisterModelRequest request = MLRegisterModelRequest.builder().registerModelInput(mlRegisterModelInput).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLRegisterModelRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(FunctionName.KMEANS, request.getRegisterModelInput().getFunctionName());
        assertEquals("modelName", request.getRegisterModelInput().getModelName());
        assertEquals("version", request.getRegisterModelInput().getVersion());
        assertEquals("url", request.getRegisterModelInput().getUrl());
        assertEquals(MLModelFormat.ONNX, request.getRegisterModelInput().getModelFormat());
        MLModelConfig config1 = request.getRegisterModelInput().getModelConfig();
        assertEquals("testModelType", config1.getModelType());
        assertEquals("{\"field1\":\"value1\",\"field2\":\"value2\"}", config1.getAllConfig());
        assertTrue(request.getRegisterModelInput().isDeployModel());
        String[] modelNodeIds = request.getRegisterModelInput().getModelNodeIds();
        assertEquals("modelNodeIds", modelNodeIds[0]);
        assertEquals("TEXT_EMBEDDING", config1.getWriteableName());
    }

    @Test
    public void validate_Success() {
        MLRegisterModelRequest request = MLRegisterModelRequest.builder().registerModelInput(mlRegisterModelInput).build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLRegisterModelInput() {
        MLRegisterModelRequest request = MLRegisterModelRequest.builder().build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    // MLRegisterModelInput check its parameters when created, so exception is not thrown here
    public void validate_Exception_NullMLModelName() {
        mlRegisterModelInput.setModelName(null);
        MLRegisterModelRequest request = MLRegisterModelRequest.builder().registerModelInput(mlRegisterModelInput).build();

        assertNull(request.validate());
        assertNull(request.getRegisterModelInput().getModelName());
    }

    @Test
    public void fromActionRequest_Success_WithMLRegisterModelRequest() {
        MLRegisterModelRequest request = MLRegisterModelRequest.builder().registerModelInput(mlRegisterModelInput).build();
        assertSame(MLRegisterModelRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLRegisterModelRequest() {
        MLRegisterModelRequest request = MLRegisterModelRequest.builder().registerModelInput(mlRegisterModelInput).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLRegisterModelRequest result = MLRegisterModelRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getRegisterModelInput().getModelName(), result.getRegisterModelInput().getModelName());
        assertEquals(
            request.getRegisterModelInput().getModelConfig().getModelType(),
            result.getRegisterModelInput().getModelConfig().getModelType()
        );
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLRegisterModelRequest.fromActionRequest(actionRequest);
    }
}
