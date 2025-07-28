package org.opensearch.ml.common.indexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;

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

@EqualsAndHashCode
@Getter
public class IndexInsight implements ToXContentObject, Writeable {
    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String STATISTICAL_DATA_FIELD = "statistical_data";
    public static final String INDEX_DESCRIPTION_FIELD = "index_description";
    public static final String FIELD_DESCRIPTION_FIELD = "field_description";
    public static final String LAST_UPDATE_FIELD = "last_updated_time";

    private String index;
    private String indexDescription;
    private String fieldDescription;
    private String statisticalData;
    private Instant lastUpdatedTime;

    @Builder(toBuilder = true)
    public IndexInsight(String index, String indexDescription, String fieldDescription, String statisticalData, Instant lastUpdatedTime) {
        this.index = index;
        this.indexDescription = indexDescription;
        this.fieldDescription = fieldDescription;
        this.statisticalData = statisticalData;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public IndexInsight(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        index = input.readString();
        if (input.readBoolean()) {
            indexDescription = input.readString();
        }
        if (input.readBoolean()) {
            fieldDescription = input.readString();
        }
        if (input.readBoolean()) {
            statisticalData = input.readString();
        }
        lastUpdatedTime = input.readInstant();

    }

    public static IndexInsight parse(XContentParser parser) throws IOException {
        String indexName = null;
        String indexDescription = null;
        String fieldDescription = null;
        String statisticalData = null;
        Instant lastUpdatedTime = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case INDEX_DESCRIPTION_FIELD:
                    indexDescription = parser.text();
                    break;
                case FIELD_DESCRIPTION_FIELD:
                    fieldDescription = parser.text();
                    break;
                case STATISTICAL_DATA_FIELD:
                    statisticalData = parser.text();
                    break;
                case LAST_UPDATE_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return IndexInsight
            .builder()
            .index(indexName)
            .indexDescription(indexDescription)
            .fieldDescription(fieldDescription)
            .statisticalData(statisticalData)
            .lastUpdatedTime(lastUpdatedTime)
            .build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(index);
        if (indexDescription != null && !indexDescription.isEmpty()) {
            out.writeBoolean(true);
            out.writeString(indexDescription);
        } else {
            out.writeBoolean(false);
        }
        if (fieldDescription != null && !fieldDescription.isEmpty()) {
            out.writeBoolean(true);
            out.writeString(fieldDescription);
        } else {
            out.writeBoolean(false);
        }
        if (statisticalData != null && !statisticalData.isEmpty()) {
            out.writeBoolean(true);
            out.writeString(statisticalData);
        } else {
            out.writeBoolean(false);
        }
        out.writeInstant(lastUpdatedTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (index != null) {
            builder.field(INDEX_NAME_FIELD, index);
        }
        if (indexDescription != null && !indexDescription.isEmpty()) {
            builder.field(INDEX_DESCRIPTION_FIELD, indexDescription);
        }
        if (fieldDescription != null && !fieldDescription.isEmpty()) {
            builder.field(FIELD_DESCRIPTION_FIELD, indexDescription);
        }
        if (statisticalData != null && !statisticalData.isEmpty()) {
            builder.field(STATISTICAL_DATA_FIELD, statisticalData);
        }
        builder.field(LAST_UPDATE_FIELD, lastUpdatedTime.toEpochMilli());
        builder.endObject();
        return builder;
    }

    public static IndexInsight fromStream(StreamInput in) throws IOException {
        return new IndexInsight(in);
    }
}
