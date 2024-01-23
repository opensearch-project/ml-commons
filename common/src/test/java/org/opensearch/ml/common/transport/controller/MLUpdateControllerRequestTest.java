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
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.controller.MLRateLimiter;

public class MLUpdateControllerRequestTest {
	private MLController updateControllerInput;

	private MLUpdateControllerRequest request;

	@Before
	public void setUp() throws Exception {

		MLRateLimiter rateLimiter = MLRateLimiter.builder()
				.limit("1")
				.unit(TimeUnit.MILLISECONDS)
				.build();
		updateControllerInput = MLController.builder()
				.modelId("testModelId")
				.userRateLimiter(new HashMap<>() {
					{
						put("testUser", rateLimiter);
					}
				})
				.build();
		request = MLUpdateControllerRequest.builder()
				.updateControllerInput(updateControllerInput)
				.build();
	}

	@Test
	public void writeToSuccess() throws IOException {
		BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
		request.writeTo(bytesStreamOutput);
		MLUpdateControllerRequest parsedRequest = new MLUpdateControllerRequest(
				bytesStreamOutput.bytes().streamInput());
		assertEquals("testModelId", parsedRequest.getUpdateControllerInput().getModelId());
		assertTrue(parsedRequest.getUpdateControllerInput().getUserRateLimiter().containsKey("testUser"));
		assertEquals("1", parsedRequest.getUpdateControllerInput().getUserRateLimiter().get("testUser")
				.getLimit());
		assertEquals(TimeUnit.MILLISECONDS, parsedRequest.getUpdateControllerInput().getUserRateLimiter()
				.get("testUser").getUnit());
	}

	@Test
	public void validateSuccess() {
		assertNull(request.validate());
	}

	@Test
	public void validateWithNullMLControllerInputException() {
		MLUpdateControllerRequest request = MLUpdateControllerRequest.builder().build();
		ActionRequestValidationException exception = request.validate();
		assertEquals("Validation Failed: 1: Update model controller input can't be null;", exception.getMessage());
	}

	@Test
	public void validateWithNullMLModelID() {
		updateControllerInput.setModelId(null);
		MLUpdateControllerRequest request = MLUpdateControllerRequest.builder()
				.updateControllerInput(updateControllerInput)
				.build();

		assertNull(request.validate());
		assertNull(request.getUpdateControllerInput().getModelId());
	}

	@Test
	public void fromActionRequestWithMLUpdateControllerRequestSuccess() {
		assertSame(MLUpdateControllerRequest.fromActionRequest(request), request);
	}

	@Test
	public void fromActionRequestWithNonMLUpdateControllerRequestSuccess() {
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
		MLUpdateControllerRequest result = MLUpdateControllerRequest.fromActionRequest(actionRequest);
		assertNotSame(result, request);
		assertEquals(request.getUpdateControllerInput().getModelId(),
				result.getUpdateControllerInput().getModelId());
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
		MLUpdateControllerRequest.fromActionRequest(actionRequest);
	}
}
