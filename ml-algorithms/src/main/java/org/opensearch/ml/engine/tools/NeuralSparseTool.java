/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.Client;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

import java.util.Map;

import static org.opensearch.ml.common.utils.StringUtils.gson;

/**
 * This tool supports neural_sparse search with sparse encoding models and rank_features field.
 */
@Log4j2
@ToolAnnotation(NeuralSparseTool.TYPE)
public class NeuralSparseTool extends AbstractRetrieverTool {
    public static final String TYPE = "NeuralSparseTool";
    @Setter @Getter
    private String name = TYPE;

    @Builder
    public NeuralSparseTool(Client client, NamedXContentRegistry xContentRegistry, String index, String embeddingField, String[] sourceFields, Integer k, Integer docSize, String modelId) {
        super(client, xContentRegistry, index, embeddingField, sourceFields, docSize, modelId);
    }

    @Override
    protected String getQueryBody(String queryText){
        return "{\"query\":{\"neural_sparse\":{\"" + embeddingField + "\":{\"query_text\":\"" + queryText + "\",\"model_id\":\""
                + modelId + "\"}}}" + " }";
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    public static class Factory extends AbstractRetrieverTool.Factory<NeuralSparseTool> {
        private static Factory INSTANCE;
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (NeuralSparseTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        @Override
        public NeuralSparseTool create(Map<String, Object> params) {
            String index = (String)params.get("index");
            String embeddingField = (String)params.get("embedding_field");
            String[] sourceFields = gson.fromJson((String)params.get("source_field"), String[].class);
            String modelId = (String)params.get("model_id");
            Integer docSize = params.containsKey("doc_size")? Integer.parseInt((String)params.get("doc_size")) : 2;
            return NeuralSparseTool.builder()
                    .client(client)
                    .xContentRegistry(xContentRegistry)
                    .index(index)
                    .embeddingField(embeddingField)
                    .sourceFields(sourceFields)
                    .modelId(modelId)
                    .docSize(docSize)
                    .build();
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
