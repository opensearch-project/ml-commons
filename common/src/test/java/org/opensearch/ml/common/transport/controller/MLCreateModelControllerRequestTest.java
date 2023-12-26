/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.controller.MLRateLimiter;


public class MLCreateModelControllerRequestTest {
	private MLModelController modelControllerInput;

	private MLCreateModelControllerRequest request;

	@Before
	public void setUp() throws Exception {

	MLRateLimiter rateLimiter = MLRateLimiter.builder()
          .rateLimitNumber("1")
          .rateLimitUnit(TimeUnit.MILLISECONDS)
          .build();
	modelControllerInput = MLModelController.builder()
			.modelId("testModelId")
			.userRateLimiterConfig(new HashMap<>() {{
				put("testUser", rateLimiter);
			}})
			.build();
	request = MLCreateModelControllerRequest.builder()
			.modelControllerInput(modelControllerInput)
			.build();
	}

	@Test
	public void writeToSuccess() throws IOException {
		BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
		request.writeTo(bytesStreamOutput);
		MLCreateModelControllerRequest parsedRequest = new MLCreateModelControllerRequest(bytesStreamOutput.bytes().streamInput());
		assertEquals("testModelId", parsedRequest.getModelControllerInput().getModelId());
		assertTrue(parsedRequest.getModelControllerInput().getUserRateLimiterConfig().containsKey("testUser"));
		assertEquals("1", parsedRequest.getModelControllerInput().getUserRateLimiterConfig().get("testUser").getRateLimitNumber());
		assertEquals(TimeUnit.MILLISECONDS, parsedRequest.getModelControllerInput().getUserRateLimiterConfig().get("testUser").getRateLimitUnit());
	}

	@Test
	public void validateSuccess() {
		assertNull(request.validate());
	}

	@Test
	public void validateWithNullMLModelControllerInputException() {
		MLCreateModelControllerRequest request = MLCreateModelControllerRequest.builder().build();
		ActionRequestValidationException exception = request.validate();
		assertEquals("Validation Failed: 1: Model controller input can't be null;", exception.getMessage());
	}

	@Test
	public void validateWithNullMLModelID() {
	modelControllerInput.setModelId(null);
	MLCreateModelControllerRequest request = MLCreateModelControllerRequest.builder()
			.modelControllerInput(modelControllerInput)
			.build();

	assertNull(request.validate());
	assertNull(request.getModelControllerInput().getModelId());
	}

	@Test
	public void fromActionRequestWithMLCreateModelControllerRequestSuccess() {
		assertSame(MLCreateModelControllerRequest.fromActionRequest(request), request);
	}

	@Test
	public void fromActionRequestWithNonMLCreateModelControllerRequestSuccess() {
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
		MLCreateModelControllerRequest result = MLCreateModelControllerRequest.fromActionRequest(actionRequest);
		assertNotSame(result, request);
		assertEquals(request.getModelControllerInput().getModelId(), result.getModelControllerInput().getModelId());
	}

	@Test(expected = UncheckedIOException.class)
	public void fromActionRequestIOException() {
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
		MLCreateModelControllerRequest.fromActionRequest(actionRequest);
	}
}
