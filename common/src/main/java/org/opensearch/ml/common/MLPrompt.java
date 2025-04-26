package org.opensearch.ml.common;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@EqualsAndHashCode
public class MLPrompt implements ToXContentObject, Writeable {

    public static final String PROMPT_ID_FIELD = "prompt_id";
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PROMPT_FIELD = "prompt";
    public static final String TAG_FIELD = "tag";
    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    @Setter
    private String promptId;
    private String name;
    private String description;
    @Setter
    private Map<String, String> prompt;
    private String tag;
    private String tenantId;
    @Setter
    private Instant createTime;
    @Setter
    private Instant lastUpdateTime;

    @Builder(toBuilder = true)
    public MLPrompt(
        String promptId,
        String name,
        String description,
        Map<String, String> prompt,
        String tag,
        String tenantId,
        Instant createTime,
        Instant lastUpdateTime
    ) {
        this.promptId = promptId;
        this.name = name;
        this.description = description;
        this.prompt = prompt;
        this.tag = tag;
        this.tenantId = tenantId;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    public MLPrompt(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.promptId = input.readOptionalString();
        this.name = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            this.prompt = input.readMap(s -> s.readString(), s -> s.readString());
        }
        this.tag = input.readOptionalString();
        tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
        this.createTime = input.readInstant();
        this.lastUpdateTime = input.readInstant();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalString(promptId);
        out.writeOptionalString(name);
        out.writeOptionalString(description);
        if (prompt != null) {
            out.writeBoolean(true);
            out.writeMap(prompt, StreamOutput::writeString, StreamOutput::writeString);
        }
        out.writeOptionalString(tag);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
        out.writeInstant(createTime);
        out.writeInstant(lastUpdateTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws java.io.IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (promptId != null) {
            builder.field(PROMPT_ID_FIELD, promptId);
        }
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (prompt != null) {
            builder.field(PROMPT_FIELD, prompt);
        }
        if (tag != null) {
            builder.field(TAG_FIELD, tag);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (createTime != null) {
            builder.field(CREATE_TIME_FIELD, createTime);
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATE_TIME_FIELD, lastUpdateTime);
        }
        return builder.endObject();
    }

    public static MLPrompt fromStream(StreamInput in) throws IOException {
        return new MLPrompt(in);
    }

    public static MLPrompt parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        Map<String, String> prompt = null;
        String tag = null;
        String tenantId = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;

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
                case PROMPT_FIELD:
                    prompt = parser.mapStrings();
                    break;
                case TAG_FIELD:
                    tag = parser.text();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case CREATE_TIME_FIELD:
                    createTime = Instant.parse(parser.text());
                    break;
                case LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = Instant.parse(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLPrompt
            .builder()
            .name(name)
            .description(description)
            .prompt(prompt)
            .tag(tag)
            .tenantId(tenantId)
            .createTime(createTime)
            .lastUpdateTime(lastUpdateTime)
            .build();
    }

    /*public void update(MLCreatePromptInput updateContent) {
        if (updateContent.getName() != null) {
            this.name = updateContent.getName();
        }
        if (updateContent.getDescription() != null) {
            this.description = updateContent.getDescription();
        }
        if (updateContent.getPrompt() != null) {
            this.prompt = updateContent.getPrompt();
        }
        if (updateContent.getTag() != null) {
            this.tag = updateContent.getTag();
        }
    }*/
}
