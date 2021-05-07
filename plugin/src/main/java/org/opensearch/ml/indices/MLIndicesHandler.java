/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.indices;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentType;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Log4j2
public class MLIndicesHandler {
    public static final String OS_ML_MODEL_RESULT = ".os_ml_model_result";
    private static final String OS_ML_MODEL_RESULT_INDEX_MAPPING = "{\n" +
            "    \"properties\": {\n" +
            "      \"taskId\": { \"type\": \"keyword\" },\n" +
            "      \"algorithm\": {\"type\": \"keyword\"},\n" +
            "      \"model\" : { \"type\": \"binary\"}\n" +
            "    }\n" +
            "}";


    ClusterService clusterService;
    Client client;

    public void initModelIndexIfAbsent() {
        initMLIndexIfAbsent(OS_ML_MODEL_RESULT, OS_ML_MODEL_RESULT_INDEX_MAPPING);
    }

    public boolean doesModelIndexExist() {
        return clusterService.state().metadata().hasIndex(OS_ML_MODEL_RESULT);
    }

    private void initMLIndexIfAbsent(String indexName, String mapping) {
        if (!clusterService.state().metadata().hasIndex(indexName)) {
            val request = new CreateIndexRequest(indexName)
                    .mapping("_doc", mapping, XContentType.JSON);
            client.admin().indices().prepareCreate(indexName).addMapping("_doc", mapping, XContentType.JSON).get();
            log.info("create index:{}", indexName);
        } else {
            log.info("index:{} is already created", indexName);
        }
    }
}
