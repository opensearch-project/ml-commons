package org.opensearch.ml.common.indexInsight;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.index.Index;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.LLMSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

@EqualsAndHashCode
@Getter
public class IndexInsight implements ToXContentObject, Writeable {
    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String STATISTICAL_DATA_FIELD = "statistical_data";
    public static final String HIGH_LEVEL_FEATURE_FIELD = "highLevel_feature";

    private String index;
    private Map<String, String> higherFeatures;
    private Map<String, String> statisticalData;

    @Builder(toBuilder = true)
    public IndexInsight(String index,  Map<String, String> higherFeatures, Map<String, String > statisticalData) {
        this.index = index;
        this.higherFeatures = higherFeatures;
        this.statisticalData = statisticalData;
    }

    public IndexInsight(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        index = input.readString();
        if (input.readBoolean()) {
            higherFeatures = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (input.readBoolean()) {
            statisticalData = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }


    }

    public static IndexInsight parse(XContentParser parser) throws IOException {
        String indexName = null;
        Map<String, String> higherFeatures = null;
        Map<String, String> statisticalData = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case STATISTICAL_DATA_FIELD:
                    statisticalData = getParameterMap(parser.map());
                    break;
                case HIGH_LEVEL_FEATURE_FIELD:
                    higherFeatures = getParameterMap(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return IndexInsight.builder().index(indexName).higherFeatures(higherFeatures).statisticalData(statisticalData).build();
    }



    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(index);
        if (higherFeatures != null && !higherFeatures.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(higherFeatures, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        if (statisticalData != null && !statisticalData.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(statisticalData, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }

    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (index != null) {
            builder.field(INDEX_NAME_FIELD, index);
        }
        if (higherFeatures != null && !higherFeatures.isEmpty()) {
            builder.field(HIGH_LEVEL_FEATURE_FIELD, higherFeatures);
        }
        if (statisticalData != null && !statisticalData.isEmpty()) {
            builder.field(STATISTICAL_DATA_FIELD, statisticalData);
        }
        builder.endObject();
        return builder;
    }

    public static IndexInsight fromStream(StreamInput in) throws IOException {
        return new IndexInsight(in);
    }
}
