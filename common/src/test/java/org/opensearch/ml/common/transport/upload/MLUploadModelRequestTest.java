package org.opensearch.ml.common.transport.upload;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelConfig;

import static org.junit.Assert.*;

public class MLUploadModelRequestTest {

    private MLUploadInput mlUploadInput;

    @Before
    public void setUp(){

        TextEmbeddingModelConfig config = TextEmbeddingModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();


        mlUploadInput = MLUploadInput.builder()
                .functionName(FunctionName.KMEANS)
                .modelName("modelName")
                .version("version")
                .url("url")
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .loadModel(true)
                .modelNodeIds(new String[]{"modelNodeIds" })
                .build();
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLUploadModelRequest request = MLUploadModelRequest.builder()
                .mlUploadInput(mlUploadInput)
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLUploadModelRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(FunctionName.KMEANS, request.getMlUploadInput().getFunctionName());
        assertEquals("modelName", request.getMlUploadInput().getModelName());
        assertEquals("version", request.getMlUploadInput().getVersion());
        assertEquals("url", request.getMlUploadInput().getUrl());
        assertEquals(MLModelFormat.ONNX, request.getMlUploadInput().getModelFormat());
        MLModelConfig config1 = request.getMlUploadInput().getModelConfig();
        assertEquals("testModelType", config1.getModelType());
        assertEquals("{\"field1\":\"value1\",\"field2\":\"value2\"}", config1.getAllConfig());
        assertTrue(request.getMlUploadInput().isLoadModel());
        String[] modelNodeIds = request.getMlUploadInput().getModelNodeIds();
        assertEquals("modelNodeIds", modelNodeIds[0]);
        assertEquals("TEXT_EMBEDDING", config1.getWriteableName());
    }

    @Test
    public void validate_Success() {
        MLUploadModelRequest request = MLUploadModelRequest.builder()
                .mlUploadInput(mlUploadInput)
                .build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLUploadInput() {
        MLUploadModelRequest request = MLUploadModelRequest.builder()
                .build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    // MLUploadInput check its parameters when created, so exception is not thrown here
    public void validate_Exception_NullMLModelName() {
        mlUploadInput.setModelName(null);
        MLUploadModelRequest request = MLUploadModelRequest.builder()
                .mlUploadInput(mlUploadInput)
                .build();

        assertNull(request.validate());
        assertNull(request.getMlUploadInput().getModelName());
    }

    @Test
    public void fromActionRequest_Success_WithMLUploadModelRequest() {
        MLUploadModelRequest request = MLUploadModelRequest.builder()
                .mlUploadInput(mlUploadInput)
                .build();
        assertSame(MLUploadModelRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLUploadModelRequest() {
        MLUploadModelRequest request = MLUploadModelRequest.builder()
                .mlUploadInput(mlUploadInput)
                .build();
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
        MLUploadModelRequest result = MLUploadModelRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getMlUploadInput().getModelName(), result.getMlUploadInput().getModelName());
        assertEquals(request.getMlUploadInput().getModelConfig().getModelType(), result.getMlUploadInput().getModelConfig().getModelType());
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
        MLUploadModelRequest.fromActionRequest(actionRequest);
    }
}
