/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Data;

@Data
public class MLSkill implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String LICENSE_FIELD = "license";
    public static final String COMPATIBILITY_FIELD = "compatibility";
    public static final String METADATA_FIELD = "metadata";
    public static final String ALLOWED_TOOLS_FIELD = "allowed_tools";
    public static final String INSTRUCTIONS_FIELD = "instructions";
    public static final String SCRIPTS_FIELD = "scripts";
    public static final String REFERENCES_FIELD = "references";
    public static final String ASSETS_FIELD = "assets";
    public static final String TENANT_ID_FIELD = "tenant_id";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_FIELD = "access";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    private String name;  // This is the document ID
    private String description;
    private String license;
    private String compatibility;
    private Map<String, String> metadata;
    private List<String> allowedTools;
    private String instructions;
    private List<SkillScript> scripts;
    private List<SkillReference> references;
    private List<SkillAsset> assets;
    private String tenantId;
    private List<String> backendRoles;
    private User owner;
    private AccessMode access;
    private Instant createdTime;
    private Instant lastUpdatedTime;

    // Constructor for builder pattern
    @lombok.Builder(toBuilder = true)
    public MLSkill(
        String name,
        String description,
        String license,
        String compatibility,
        Map<String, String> metadata,
        List<String> allowedTools,
        String instructions,
        List<SkillScript> scripts,
        List<SkillReference> references,
        List<SkillAsset> assets,
        String tenantId,
        List<String> backendRoles,
        User owner,
        AccessMode access,
        Instant createdTime,
        Instant lastUpdatedTime
    ) {
        this.name = name;
        this.description = description;
        this.license = license;
        this.compatibility = compatibility;
        this.metadata = metadata;
        this.allowedTools = allowedTools;
        this.instructions = instructions;
        this.scripts = scripts;
        this.references = references;
        this.assets = assets;
        this.tenantId = tenantId;
        this.backendRoles = backendRoles;
        this.owner = owner;
        this.access = access;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public MLSkill(StreamInput in) throws IOException {
        this.name = in.readString();
        this.description = in.readString();
        this.license = in.readOptionalString();
        this.compatibility = in.readOptionalString();
        if (in.readBoolean()) {
            this.metadata = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (in.readBoolean()) {
            this.allowedTools = in.readStringList();
        }
        this.instructions = in.readString();
        if (in.readBoolean()) {
            this.scripts = in.readList(SkillScript::new);
        }
        if (in.readBoolean()) {
            this.references = in.readList(SkillReference::new);
        }
        if (in.readBoolean()) {
            this.assets = in.readList(SkillAsset::new);
        }
        this.tenantId = in.readOptionalString();
        if (in.readBoolean()) {
            this.backendRoles = in.readStringList();
        }
        if (in.readBoolean()) {
            this.owner = new User(in);
        }
        if (in.readBoolean()) {
            this.access = in.readEnum(AccessMode.class);
        }
        this.createdTime = in.readOptionalInstant();
        this.lastUpdatedTime = in.readOptionalInstant();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(description);
        out.writeOptionalString(license);
        out.writeOptionalString(compatibility);
        if (metadata != null) {
            out.writeBoolean(true);
            out.writeMap(metadata, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (allowedTools != null) {
            out.writeBoolean(true);
            out.writeStringCollection(allowedTools);
        } else {
            out.writeBoolean(false);
        }
        out.writeString(instructions);
        if (scripts != null) {
            out.writeBoolean(true);
            out.writeList(scripts);
        } else {
            out.writeBoolean(false);
        }
        if (references != null) {
            out.writeBoolean(true);
            out.writeList(references);
        } else {
            out.writeBoolean(false);
        }
        if (assets != null) {
            out.writeBoolean(true);
            out.writeList(assets);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
        if (backendRoles != null) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
        if (owner != null) {
            out.writeBoolean(true);
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (access != null) {
            out.writeBoolean(true);
            out.writeEnum(access);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdatedTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        builder.field(DESCRIPTION_FIELD, description);
        if (license != null) {
            builder.field(LICENSE_FIELD, license);
        }
        if (compatibility != null) {
            builder.field(COMPATIBILITY_FIELD, compatibility);
        }
        if (metadata != null && !metadata.isEmpty()) {
            builder.field(METADATA_FIELD, metadata);
        }
        if (allowedTools != null && !allowedTools.isEmpty()) {
            builder.field(ALLOWED_TOOLS_FIELD, allowedTools);
        }
        builder.field(INSTRUCTIONS_FIELD, instructions);
        if (scripts != null && !scripts.isEmpty()) {
            builder.startArray(SCRIPTS_FIELD);
            for (SkillScript script : scripts) {
                script.toXContent(builder, params);
            }
            builder.endArray();
        }
        if (references != null && !references.isEmpty()) {
            builder.startArray(REFERENCES_FIELD);
            for (SkillReference reference : references) {
                reference.toXContent(builder, params);
            }
            builder.endArray();
        }
        if (assets != null && !assets.isEmpty()) {
            builder.startArray(ASSETS_FIELD);
            for (SkillAsset asset : assets) {
                asset.toXContent(builder, params);
            }
            builder.endArray();
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (backendRoles != null && !backendRoles.isEmpty()) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (owner != null) {
            builder.field(OWNER_FIELD);
            owner.toXContent(builder, params);
        }
        if (access != null) {
            builder.field(ACCESS_FIELD, access);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }

    public static MLSkill parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        String license = null;
        String compatibility = null;
        Map<String, String> metadata = null;
        List<String> allowedTools = null;
        String instructions = null;
        List<SkillScript> scripts = null;
        List<SkillReference> references = null;
        List<SkillAsset> assets = null;
        String tenantId = null;
        List<String> backendRoles = null;
        User owner = null;
        AccessMode access = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case LICENSE_FIELD:
                    license = parser.text();
                    break;
                case COMPATIBILITY_FIELD:
                    compatibility = parser.text();
                    break;
                case METADATA_FIELD:
                    metadata = parser.mapStrings();
                    break;
                case ALLOWED_TOOLS_FIELD:
                    allowedTools = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        allowedTools.add(parser.text());
                    }
                    break;
                case INSTRUCTIONS_FIELD:
                    instructions = parser.text();
                    break;
                case SCRIPTS_FIELD:
                    scripts = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        scripts.add(SkillScript.parse(parser));
                    }
                    break;
                case REFERENCES_FIELD:
                    references = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        references.add(SkillReference.parse(parser));
                    }
                    break;
                case ASSETS_FIELD:
                    assets = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        assets.add(SkillAsset.parse(parser));
                    }
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case BACKEND_ROLES_FIELD:
                    backendRoles = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case OWNER_FIELD:
                    owner = User.parse(parser);
                    break;
                case ACCESS_FIELD:
                    access = AccessMode.from(parser.text());
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLSkill
            .builder()
            .name(name)
            .description(description)
            .license(license)
            .compatibility(compatibility)
            .metadata(metadata)
            .allowedTools(allowedTools)
            .instructions(instructions)
            .scripts(scripts)
            .references(references)
            .assets(assets)
            .tenantId(tenantId)
            .backendRoles(backendRoles)
            .owner(owner)
            .access(access)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .build();
    }

    @Data
    public static class SkillScript implements ToXContentObject, Writeable {
        private String name;
        private String content;
        private String language;

        @lombok.Builder
        public SkillScript(String name, String content, String language) {
            this.name = name;
            this.content = content;
            this.language = language;
        }

        public SkillScript(StreamInput in) throws IOException {
            this.name = in.readString();
            this.content = in.readString();
            this.language = in.readOptionalString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(name);
            out.writeString(content);
            out.writeOptionalString(language);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field("name", name);
            builder.field("content", content);
            if (language != null) {
                builder.field("language", language);
            }
            builder.endObject();
            return builder;
        }

        public static SkillScript parse(XContentParser parser) throws IOException {
            String name = null;
            String content = null;
            String language = null;

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case "name":
                        name = parser.text();
                        break;
                    case "content":
                        content = parser.text();
                        break;
                    case "language":
                        language = parser.text();
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }

            return SkillScript.builder().name(name).content(content).language(language).build();
        }
    }

    @Data
    public static class SkillReference implements ToXContentObject, Writeable {
        private String name;
        private String content;

        @lombok.Builder
        public SkillReference(String name, String content) {
            this.name = name;
            this.content = content;
        }

        public SkillReference(StreamInput in) throws IOException {
            this.name = in.readString();
            this.content = in.readString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(name);
            out.writeString(content);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field("name", name);
            builder.field("content", content);
            builder.endObject();
            return builder;
        }

        public static SkillReference parse(XContentParser parser) throws IOException {
            String name = null;
            String content = null;

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case "name":
                        name = parser.text();
                        break;
                    case "content":
                        content = parser.text();
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }

            return SkillReference.builder().name(name).content(content).build();
        }
    }

    @Data
    public static class SkillAsset implements ToXContentObject, Writeable {
        private String name;
        private String content;
        private String contentType;

        @lombok.Builder
        public SkillAsset(String name, String content, String contentType) {
            this.name = name;
            this.content = content;
            this.contentType = contentType;
        }

        public SkillAsset(StreamInput in) throws IOException {
            this.name = in.readString();
            this.content = in.readString();
            this.contentType = in.readOptionalString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(name);
            out.writeString(content);
            out.writeOptionalString(contentType);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field("name", name);
            builder.field("content", content);
            if (contentType != null) {
                builder.field("content_type", contentType);
            }
            builder.endObject();
            return builder;
        }

        public static SkillAsset parse(XContentParser parser) throws IOException {
            String name = null;
            String content = null;
            String contentType = null;

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case "name":
                        name = parser.text();
                        break;
                    case "content":
                        content = parser.text();
                        break;
                    case "content_type":
                        contentType = parser.text();
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }

            return SkillAsset.builder().name(name).content(content).contentType(contentType).build();
        }
    }
}
