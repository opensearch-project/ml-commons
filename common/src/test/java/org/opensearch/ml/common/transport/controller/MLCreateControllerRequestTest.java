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

public class MLCreateControllerRequestTest {
	private MLController controllerInput;

	private MLCreateControllerRequest request;

	@Before
	public void setUp() throws Exception {

		MLRateLimiter rateLimiter = MLRateLimiter.builder()
				.limit("1")
				.unit(TimeUnit.MILLISECONDS)
				.build();
		controllerInput = MLController.builder()
				.modelId("testModelId")
				.userRateLimiter(new HashMap<>() {
					{
						put("testUser", rateLimiter);
					}
				})
				.build();
		request = MLCreateControllerRequest.builder()
				.controllerInput(controllerInput)
				.build();
	}

	@Test
	public void writeToSuccess() throws IOException {
		BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
		request.writeTo(bytesStreamOutput);
		MLCreateControllerRequest parsedRequest = new MLCreateControllerRequest(
				bytesStreamOutput.bytes().streamInput());
		assertEquals("testModelId", parsedRequest.getControllerInput().getModelId());
		assertTrue(parsedRequest.getControllerInput().getUserRateLimiter().containsKey("testUser"));
		assertEquals("1", parsedRequest.getControllerInput().getUserRateLimiter().get("testUser")
				.getLimit());
		assertEquals(TimeUnit.MILLISECONDS,
				parsedRequest.getControllerInput().getUserRateLimiter().get("testUser").getUnit());
	}

	@Test
	public void validateSuccess() {
		assertNull(request.validate());
	}

	@Test
	public void validateWithNullMLControllerInputException() {
		MLCreateControllerRequest request = MLCreateControllerRequest.builder().build();
		ActionRequestValidationException exception = request.validate();
		assertEquals("Validation Failed: 1: Model controller input can't be null;", exception.getMessage());
	}

	@Test
	public void validateWithNullMLModelID() {
		controllerInput.setModelId(null);
		MLCreateControllerRequest request = MLCreateControllerRequest.builder()
				.controllerInput(controllerInput)
				.build();

		assertNull(request.validate());
		assertNull(request.getControllerInput().getModelId());
	}

	@Test
	public void fromActionRequestWithMLCreateControllerRequestSuccess() {
		assertSame(MLCreateControllerRequest.fromActionRequest(request), request);
	}

	@Test
	public void fromActionRequestWithNonMLCreateControllerRequestSuccess() {
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
		MLCreateControllerRequest result = MLCreateControllerRequest.fromActionRequest(actionRequest);
		assertNotSame(result, request);
		assertEquals(request.getControllerInput().getModelId(), result.getControllerInput().getModelId());
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
		MLCreateControllerRequest.fromActionRequest(actionRequest);
	}
}
