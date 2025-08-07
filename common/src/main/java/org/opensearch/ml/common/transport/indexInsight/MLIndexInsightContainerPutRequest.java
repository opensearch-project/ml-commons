package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;

import java.io.IOException;

public class MLIndexInsightContainerPutRequest extends ActionRequest {
    private String indexName;
    private String tenantId;

    public MLIndexInsightContainerPutRequest(String indexName, String tenantId){
        this.indexName = indexName;
        this.tenantId = tenantId;
    }

    public MLIndexInsightContainerPutRequest(StreamInput in) throws IOException {
        super(in);
        this.indexName = in.readString();
        this.tenantId = in.readString();
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
