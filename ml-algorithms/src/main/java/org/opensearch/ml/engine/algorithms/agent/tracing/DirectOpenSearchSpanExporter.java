/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.index.IndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.transport.client.Client;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.log4j.Log4j2;

/**
 * Direct OpenSearch span exporter that writes traces to OpenSearch indices (for testing).
 */
@Log4j2
public class DirectOpenSearchSpanExporter implements SpanExporter {

    private final Client client;
    private final String indexPrefix;

    public DirectOpenSearchSpanExporter(Client client, String indexPrefix) {
        this.client = client;
        this.indexPrefix = indexPrefix != null ? indexPrefix : "otel-v1-apm-span";
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (spans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }

        log.info("[DirectOpenSearchExporter] Exporting {} spans", spans.size());
        CompletableResultCode result = new CompletableResultCode();

        try {
            for (SpanData span : spans) {
                String indexName = indexPrefix + "-" + Instant.ofEpochSecond(0, span.getStartEpochNanos()).toString().substring(0, 10);

                Map<String, Object> document = createSpanDocument(span);

                IndexRequest request = new IndexRequest(indexName).source(document, XContentType.JSON);

                client
                    .index(
                        request,
                        ActionListener
                            .wrap(
                                response -> log.debug("[DirectOpenSearchExporter] Span indexed: {}", span.getSpanId()),
                                error -> log.error("[DirectOpenSearchExporter] Failed to index span: {}", error.getMessage())
                            )
                    );
            }
            result.succeed();
        } catch (Exception e) {
            log.error("[DirectOpenSearchExporter] Export failed", e);
            result.fail();
        }

        return result;
    }

    private Map<String, Object> createSpanDocument(SpanData span) {
        Map<String, Object> doc = new HashMap<>();

        // Basic span info
        doc.put("trace_id", span.getTraceId());
        doc.put("span_id", span.getSpanId());
        doc.put("parent_span_id", span.getParentSpanId());
        doc.put("name", span.getName());
        doc.put("kind", span.getKind().name());
        doc.put("start_time", Instant.ofEpochSecond(0, span.getStartEpochNanos()));
        doc.put("end_time", Instant.ofEpochSecond(0, span.getEndEpochNanos()));
        doc.put("duration_ms", (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000);
        doc.put("status", span.getStatus().getStatusCode().name());

        // Attributes
        Map<String, Object> attributes = new HashMap<>();
        span.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), value != null ? value.toString() : null));
        doc.put("attributes", attributes);

        return doc;
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
