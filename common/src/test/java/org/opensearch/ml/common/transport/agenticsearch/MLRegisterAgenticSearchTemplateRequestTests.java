/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLRegisterAgenticSearchTemplateRequestTests {

    private static Map<String, Object> sampleSchema() {
        Map<String, Object> lexQuery = new LinkedHashMap<>();
        lexQuery.put("type", "string");
        lexQuery.put("required", true);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("lex_query", lexQuery);
        return schema;
    }

    @Test
    public void streamRoundTrip_withParamSchema() throws IOException {
        MLRegisterAgenticSearchTemplateRequest request = new MLRegisterAgenticSearchTemplateRequest(
            "product_search",
            "products",
            "desc",
            sampleSchema()
        );

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLRegisterAgenticSearchTemplateRequest parsed = new MLRegisterAgenticSearchTemplateRequest(out.bytes().streamInput());

        assertEquals("product_search", parsed.getTemplateId());
        assertEquals("products", parsed.getIndex());
        assertEquals("desc", parsed.getDescription());
        assertEquals(sampleSchema(), parsed.getParamSchema());
    }

    @Test
    public void streamRoundTrip_nullParamSchema() throws IOException {
        MLRegisterAgenticSearchTemplateRequest request = new MLRegisterAgenticSearchTemplateRequest(
            "product_search",
            "products",
            null,
            null
        );

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLRegisterAgenticSearchTemplateRequest parsed = new MLRegisterAgenticSearchTemplateRequest(out.bytes().streamInput());

        assertEquals("product_search", parsed.getTemplateId());
        assertEquals("products", parsed.getIndex());
        assertNull(parsed.getDescription());
        assertNull(parsed.getParamSchema());
    }

    @Test
    public void fromActionRequest_sameInstanceReturnedAsIs() {
        MLRegisterAgenticSearchTemplateRequest request = new MLRegisterAgenticSearchTemplateRequest("t", "i", "d", sampleSchema());
        assertSame(request, MLRegisterAgenticSearchTemplateRequest.fromActionRequest(request));
    }

    @Test
    public void fromActionRequest_reserializesForeignRequest() throws IOException {
        MLRegisterAgenticSearchTemplateRequest original = new MLRegisterAgenticSearchTemplateRequest("t", "i", "d", sampleSchema());
        // A foreign ActionRequest that writes the same wire form is re-read into this type.
        ActionRequest foreign = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                original.writeTo(out);
            }
        };

        MLRegisterAgenticSearchTemplateRequest parsed = MLRegisterAgenticSearchTemplateRequest.fromActionRequest(foreign);
        assertNotNull(parsed);
        assertEquals("t", parsed.getTemplateId());
        assertEquals(sampleSchema(), parsed.getParamSchema());
    }

    @Test
    public void validate_missingTemplateIdAndIndex() {
        MLRegisterAgenticSearchTemplateRequest request = new MLRegisterAgenticSearchTemplateRequest("", "", "d", null);
        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(2, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Template id"));
        assertTrue(exception.validationErrors().get(1).contains("Index"));
    }

    @Test
    public void validate_ok() {
        MLRegisterAgenticSearchTemplateRequest request = new MLRegisterAgenticSearchTemplateRequest("t", "i", null, null);
        assertNull(request.validate());
    }
}
