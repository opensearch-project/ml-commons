package org.opensearch.ml.common.indexInsight;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.index.Index;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;

@Getter
public class IndexInsightContainer implements ToXContentObject, Writeable {
    private String indexName;
    private String tenantId;

    @Builder(toBuilder = true)
    public IndexInsightContainer(String indexName, String tenantId) {
        this.indexName = indexName;
        this.tenantId = tenantId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(indexName);
        out.writeString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(INDEX_NAME_FIELD, indexName);
        builder.field(TENANT_ID_FIELD, tenantId);
        builder.endObject();
        return builder;
    }

    public static IndexInsightContainer parse(XContentParser parser) throws IOException {
        String indexName = null;
        String tenantId = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }

        }
        return IndexInsightContainer.builder().indexName(indexName).tenantId(tenantId).build();
    }
}
