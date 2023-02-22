/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class MLCreateModelMetaResponseTest {

	MLCreateModelMetaResponse mlCreateModelMetaResponse;

	@Before
	public void setup() {
		mlCreateModelMetaResponse = new MLCreateModelMetaResponse("Model Id", "Status");
	}


	@Test
	public void writeTo_Success() throws IOException {
		BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
		mlCreateModelMetaResponse.writeTo(bytesStreamOutput);
		MLCreateModelMetaResponse newResponse = new MLCreateModelMetaResponse(bytesStreamOutput.bytes().streamInput());
//		assertEquals(mlCreateModelMetaResponse, newResponse);
	}

	@Test
	public void testToXContent() throws IOException {
		MLCreateModelMetaResponse response = new MLCreateModelMetaResponse("Model Id", "Status");
		XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
		response.toXContent(builder, EMPTY_PARAMS);
		assertNotNull(builder);
		String jsonStr = TestHelper.xContentBuilderToString(builder);
		final String expected = "{\"model_id\":\"Model Id\",\"status\":\"Status\"}";
		assertEquals(expected, jsonStr);
	}
}
