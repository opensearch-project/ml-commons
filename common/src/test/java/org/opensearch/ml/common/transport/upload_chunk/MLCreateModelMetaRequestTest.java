/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class MLCreateModelMetaRequestTest {

	TextEmbeddingModelConfig config;
	MLCreateModelMetaInput mlCreateModelMetaInput;

	@Before
	public void setUp() {
		config = new TextEmbeddingModelConfig("Model Type", 123, FrameworkType.SENTENCE_TRANSFORMERS, "All Config",
				TextEmbeddingModelConfig.PoolingMode.MEAN, true, 512);
		mlCreateModelMetaInput = new MLCreateModelMetaInput("Model Name", FunctionName.BATCH_RCF, "1.0",
				"Model Description", MLModelFormat.TORCH_SCRIPT, MLModelState.LOADING, 200L, "123", config, 2);
	}

	@Test
	public void writeTo_Succeess() throws IOException {
		MLCreateModelMetaRequest request = new MLCreateModelMetaRequest(mlCreateModelMetaInput);
		BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
		request.writeTo(bytesStreamOutput);
		MLCreateModelMetaRequest newRequest = new MLCreateModelMetaRequest(bytesStreamOutput.bytes().streamInput());
		assertEquals(request.getMlCreateModelMetaInput().getName(), newRequest.getMlCreateModelMetaInput().getName());
		assertEquals(request.getMlCreateModelMetaInput().getDescription(),
				newRequest.getMlCreateModelMetaInput().getDescription());
		assertEquals(request.getMlCreateModelMetaInput().getFunctionName(),
				newRequest.getMlCreateModelMetaInput().getFunctionName());
		assertEquals(request.getMlCreateModelMetaInput().getModelConfig().getAllConfig(),
				newRequest.getMlCreateModelMetaInput().getModelConfig().getAllConfig());
		assertEquals(request.getMlCreateModelMetaInput().getVersion(),
				newRequest.getMlCreateModelMetaInput().getVersion());
	}

	@Test
	public void validate_Exception_NullModelId() {
		MLCreateModelMetaRequest mlCreateModelMetaRequest = MLCreateModelMetaRequest.builder().build();
		ActionRequestValidationException exception = mlCreateModelMetaRequest.validate();
		assertEquals("Validation Failed: 1: Model meta input can't be null;", exception.getMessage());
	}

	@Test
	public void fromActionRequest_Success() {
		MLCreateModelMetaRequest request = new MLCreateModelMetaRequest(mlCreateModelMetaInput);
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
		MLCreateModelMetaRequest newRequest = MLCreateModelMetaRequest.fromActionRequest(actionRequest);
		assertNotSame(request, newRequest);
		assertEquals(request.getMlCreateModelMetaInput().getName(), newRequest.getMlCreateModelMetaInput().getName());
		assertEquals(request.getMlCreateModelMetaInput().getDescription(),
				newRequest.getMlCreateModelMetaInput().getDescription());
		assertEquals(request.getMlCreateModelMetaInput().getFunctionName(),
				newRequest.getMlCreateModelMetaInput().getFunctionName());
		assertEquals(request.getMlCreateModelMetaInput().getModelConfig().getAllConfig(),
				newRequest.getMlCreateModelMetaInput().getModelConfig().getAllConfig());
		assertEquals(request.getMlCreateModelMetaInput().getVersion(),
				newRequest.getMlCreateModelMetaInput().getVersion());
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
		MLCreateModelMetaRequest.fromActionRequest(actionRequest);
	}
}
