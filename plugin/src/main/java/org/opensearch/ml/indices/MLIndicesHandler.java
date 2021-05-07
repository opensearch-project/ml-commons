/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *  
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
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
