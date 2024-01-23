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

public class MLUpdateModelControllerRequestTest {
	private MLModelController updateModelControllerInput;

	private MLUpdateModelControllerRequest request;

	@Before
	public void setUp() throws Exception {

		MLRateLimiter rateLimiter = MLRateLimiter.builder()
				.limit("1")
				.unit(TimeUnit.MILLISECONDS)
				.build();
		updateModelControllerInput = MLModelController.builder()
				.modelId("testModelId")
				.userRateLimiter(new HashMap<>() {
					{
						put("testUser", rateLimiter);
					}
				})
				.build();
		request = MLUpdateModelControllerRequest.builder()
				.updateModelControllerInput(updateModelControllerInput)
				.build();
	}

	@Test
	public void writeToSuccess() throws IOException {
		BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
		request.writeTo(bytesStreamOutput);
		MLUpdateModelControllerRequest parsedRequest = new MLUpdateModelControllerRequest(
				bytesStreamOutput.bytes().streamInput());
		assertEquals("testModelId", parsedRequest.getUpdateModelControllerInput().getModelId());
		assertTrue(parsedRequest.getUpdateModelControllerInput().getUserRateLimiter().containsKey("testUser"));
		assertEquals("1", parsedRequest.getUpdateModelControllerInput().getUserRateLimiter().get("testUser")
				.getLimit());
		assertEquals(TimeUnit.MILLISECONDS, parsedRequest.getUpdateModelControllerInput().getUserRateLimiter()
				.get("testUser").getUnit());
	}

	@Test
	public void validateSuccess() {
		assertNull(request.validate());
	}

	@Test
	public void validateWithNullMLModelControllerInputException() {
		MLUpdateModelControllerRequest request = MLUpdateModelControllerRequest.builder().build();
		ActionRequestValidationException exception = request.validate();
		assertEquals("Validation Failed: 1: Update model controller input can't be null;", exception.getMessage());
	}

	@Test
	public void validateWithNullMLModelID() {
		updateModelControllerInput.setModelId(null);
		MLUpdateModelControllerRequest request = MLUpdateModelControllerRequest.builder()
				.updateModelControllerInput(updateModelControllerInput)
				.build();

		assertNull(request.validate());
		assertNull(request.getUpdateModelControllerInput().getModelId());
	}

	@Test
	public void fromActionRequestWithMLUpdateModelControllerRequestSuccess() {
		assertSame(MLUpdateModelControllerRequest.fromActionRequest(request), request);
	}

	@Test
	public void fromActionRequestWithNonMLUpdateModelControllerRequestSuccess() {
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
		MLUpdateModelControllerRequest result = MLUpdateModelControllerRequest.fromActionRequest(actionRequest);
		assertNotSame(result, request);
		assertEquals(request.getUpdateModelControllerInput().getModelId(),
				result.getUpdateModelControllerInput().getModelId());
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
		MLUpdateModelControllerRequest.fromActionRequest(actionRequest);
	}
}
