/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agenticsearch;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Param-schema for agentic-search template fill: the typed "slots" an LLM fills for
 * one registered OpenSearch search template. One document per template in the
 * {@code .plugins-ml-agentic-search-templates} system index, keyed by
 * {@code template_id} (which is also the core {@code _scripts} template name — one
 * identifier keys the schema doc, rides in the connector, and renders the body).
 *
 * <p>The Mustache body itself lives in core {@code _scripts} and is owned by
 * opensearch-core; this artifact is ml-commons' metadata beside it. The schema is
 * assembled from three inputs at registration (body parse-tree → param names/types;
 * index mapping → field-name enums; customer → value enums/descriptions), so it is
 * stored and editable rather than re-parsed per query.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AgenticSearchTemplate implements ToXContentObject, Writeable {

    public static final String TEMPLATE_ID_FIELD = "template_id";
    public static final String INDEX_BINDING_FIELD = "index_binding";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PARAM_SCHEMA_FIELD = "param_schema";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String CREATED_BY_FIELD = "created_by";

    // Same grammar as the core _scripts template name it mirrors.
    private static final Pattern VALID_TEMPLATE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-.]{1,255}$");

    /** The core {@code _scripts} template name; also this doc's {@code _id}. */
    private String templateId;

    /** The index this template targets (used to derive field-name enums). */
    private String indexBinding;

    /** Human-readable description of what the template searches. */
    private String description;

    /**
     * The typed slots the model fills, keyed by param name. Each value is a spec
     * map: {@code {type, required?, enum?, description?, source?}}. Stored opaquely
     * ({@code enabled:false} in the mapping) because keys are per-template.
     */
    private Map<String, Object> paramSchema;

    private Instant createdTime;
    private Instant lastUpdatedTime;
    private String createdBy;

    public AgenticSearchTemplate(StreamInput input) throws IOException {
        this.templateId = input.readString();
        this.indexBinding = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            this.paramSchema = input.readMap();
        }
        this.createdTime = input.readOptionalInstant();
        this.lastUpdatedTime = input.readOptionalInstant();
        this.createdBy = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(templateId);
        out.writeOptionalString(indexBinding);
        out.writeOptionalString(description);
        if (paramSchema != null) {
            out.writeBoolean(true);
            out.writeMap(paramSchema);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdatedTime);
        out.writeOptionalString(createdBy);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (templateId != null) {
            builder.field(TEMPLATE_ID_FIELD, templateId);
        }
        if (indexBinding != null) {
            builder.field(INDEX_BINDING_FIELD, indexBinding);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (paramSchema != null) {
            builder.field(PARAM_SCHEMA_FIELD, paramSchema);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());
        }
        if (createdBy != null) {
            builder.field(CREATED_BY_FIELD, createdBy);
        }
        builder.endObject();
        return builder;
    }

    public static AgenticSearchTemplate parse(XContentParser parser) throws IOException {
        String templateId = null;
        String indexBinding = null;
        String description = null;
        Map<String, Object> paramSchema = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;
        String createdBy = null;

        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            parser.nextToken();
        }

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TEMPLATE_ID_FIELD:
                    templateId = parser.text();
                    break;
                case INDEX_BINDING_FIELD:
                    indexBinding = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PARAM_SCHEMA_FIELD:
                    paramSchema = parser.map();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case CREATED_BY_FIELD:
                    createdBy = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return AgenticSearchTemplate
            .builder()
            .templateId(templateId)
            .indexBinding(indexBinding)
            .description(description)
            .paramSchema(paramSchema)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .createdBy(createdBy)
            .build();
    }

    /** Whether the template id is well-formed (matches the {@code _scripts} name grammar). */
    public boolean isValidTemplateId() {
        return templateId != null && VALID_TEMPLATE_ID_PATTERN.matcher(templateId).matches();
    }

    /** A stored schema is usable only if it declares at least one param. */
    public boolean hasParams() {
        return paramSchema != null && !paramSchema.isEmpty();
    }
}
