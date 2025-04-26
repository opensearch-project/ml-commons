package org.opensearch.ml.common.transport.prompt;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;

import java.io.IOException;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

@Data
public class MLCreatePromptInput implements ToXContentObject, Writeable {
    public static final String PROMPT_NAME_FIELD = "name";
    public static final String PROMPT_DESCRIPTION_FIELD = "description";
    public static final String PROMPT_PROMPT_FIELD = "prompt";
    public static final String PROMPT_TAG_FIELD = "tag";

    public static final String DRY_RUN_FIELD = "dry_run";

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG = CommonValue.VERSION_2_13_0;

    public static final String DRY_RUN_PROMPT_NAME = "dryRunPrompt";

    private String name;
    private String description;
    private Map<String, String> prompt;
    @Setter
    private String tenantId;
    private String tag;
    private boolean dryRun;
    //private boolean updatePrompt;

    @Builder(toBuilder = true)
    public MLCreatePromptInput(
            String name,
            String description,
            Map<String, String> prompt,
            String tag,
            boolean dryRun,
            String tenantId
//            boolean updatePrompt
    ) {
//        if (!dryRun && !updatePrompt) {
//            if (prompt == null) {
//                throw new IllegalArgumentException("Prompt is null");
//            }
//        }
        if (!dryRun) {
            if (prompt == null) {
                throw new IllegalArgumentException("Prompt is null");
            }
        }
        this.name = name;
        this.description = description;
        this.prompt = prompt;
        this.tag = tag;
        this.dryRun = dryRun;
//        this.updatePrompt = updatePrompt;
        this.tenantId = tenantId;
    }

    public static MLCreatePromptInput parse(XContentParser parser) throws IOException {
        return parse(parser, false);
    }

    public static MLCreatePromptInput parse(XContentParser parser, boolean updatePrompt) throws IOException {
        String name = null;
        String description = null;
        Map<String, String> prompt = null;
        String tag = null;
        boolean dryRun = false;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PROMPT_NAME_FIELD:
                    name = parser.text();
                    break;
                case PROMPT_DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PROMPT_PROMPT_FIELD:
                    prompt = getParameterMap(parser.map());
                    break;
                case PROMPT_TAG_FIELD:
                    tag = parser.text();
                    break;
                case DRY_RUN_FIELD:
                    dryRun = parser.booleanValue();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLCreatePromptInput(
                name,
                description,
                prompt,
                tag,
                dryRun,
                tenantId
                //updatePrompt
        );
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(PROMPT_NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(PROMPT_DESCRIPTION_FIELD, description);
        }
        if (prompt != null) {
            builder.field(PROMPT_PROMPT_FIELD, prompt);
        }
        if (tag != null) {
            builder.field(PROMPT_TAG_FIELD, tag);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        Version streamOutputVersion = output.getVersion();
        output.writeOptionalString(name);
        output.writeOptionalString(description);
        if (prompt != null) {
            output.writeBoolean(true);
            output.writeMap(prompt, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
        output.writeOptionalString(tag);
        output.writeBoolean(dryRun);
//        output.writeBoolean(updatePrompt);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(output.getVersion().toString());
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
    }

    public MLCreatePromptInput(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        name = input.readOptionalString();
        description = input.readOptionalString();
        if (input.readBoolean()) {
            prompt = input.readMap(s -> s.readString(), s -> s.readString());
        }
        tag = input.readOptionalString();
        dryRun = input.readBoolean();
        if (streamInputVersion.onOrAfter(VERSION_2_19_0)) {
            input.readOptionalString();
        }
//        updatePrompt = input.readBoolean();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }
}