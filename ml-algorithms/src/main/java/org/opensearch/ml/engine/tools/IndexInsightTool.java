/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.input.Constants.CMK_ASSUME_ROLE_FIELD;
import static org.opensearch.ml.common.input.Constants.CMK_ROLE_FIELD;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToolAnnotation(IndexInsightTool.TYPE)
public class IndexInsightTool implements Tool {
    public static final String TYPE = "IndexInsightTool";

    @Getter
    @Setter
    private Map<String, Object> attributes;

    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;

    @Setter
    private Client client;

    public IndexInsightTool(Client client) {
        this.client = client;
    }

    @VisibleForTesting
    static String DEFAULT_DESCRIPTION =
        "Use this tool to get details of one index according to different task type, including STATISTICAL_DATA: the data distribution and index mapping of the index, FIELD_DESCRIPTION: The description of each column, LOG_RELATED_INDEX_CHECK: Whether the index is related to log/trace and whether it contains trace/log fields";

    public static final String STRICT_FIELD = "strict";

    public static final String DEFAULT_INPUT_SCHEMA = """
                {
                    "type": "object",
                    "properties": {
                        "index": {
                            "type": "string",
                            "description": "OpenSearch index name"
                        },
                        "question": {
                            "type": "string",
                            "description": "Natural language question to query data from opensearch index"
                        }
                    }
                },
                "strict": false
              }
        """;

    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        try {
            Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
            String indexName = parameters.get("indexName");
            MLIndexInsightType taskType = MLIndexInsightType.fromString(parameters.get("taskType"));
            MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
                indexName,
                taskType,
                parameters.getOrDefault(TENANT_ID_FIELD, null),
                parameters.getOrDefault(CMK_ROLE_FIELD, null),
                parameters.getOrDefault(CMK_ASSUME_ROLE_FIELD, null)
            );
            client.execute(MLIndexInsightGetAction.INSTANCE, mlIndexInsightGetRequest, ActionListener.wrap(r -> {
                IndexInsight indexInsight = r.getIndexInsight();
                listener.onResponse((T) indexInsight.toString());
            }, listener::onFailure));

        } catch (Exception e) {
            log.error("Failed to run index insight tool.", e);
            listener.onFailure(e);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    public static class Factory implements Tool.Factory<IndexInsightTool> {
        private Client client;

        private static IndexInsightTool.Factory INSTANCE;

        public static IndexInsightTool.Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (MLModelTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new IndexInsightTool.Factory();
                return INSTANCE;
            }
        }

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public IndexInsightTool create(Map<String, Object> map) {
            return new IndexInsightTool(client);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public Map<String, Object> getDefaultAttributes() {
            return DEFAULT_ATTRIBUTES;
        }

        @Override
        public String getDefaultVersion() {
            return null;
        }
    }

}
