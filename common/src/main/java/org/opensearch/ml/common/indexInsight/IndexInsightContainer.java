package org.opensearch.ml.common.indexInsight;

import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.index.Index;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;

public class IndexInsightContainer implements ToXContentObject, Writeable {
    private String indexName;
    private String tenantId;

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
}
