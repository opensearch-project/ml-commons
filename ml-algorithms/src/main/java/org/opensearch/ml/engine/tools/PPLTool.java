package org.opensearch.ml.engine.tools;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.admin.cluster.shards.ClusterSearchShardsRequest;
import org.opensearch.action.admin.cluster.shards.ClusterSearchShardsResponse;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.opensearch.ml.common.utils.StringUtils.gson;

@Log4j2
public class PPLTool implements Tool {
    public static final String TYPE = "PPLTool";

    private Client client;

    private static final String DEFAULT_DESCRIPTION = "Use this tool to generate PPL and execute.";

    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    private String version;

    private String modelId;

    public PPLTool(Client client, String modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        String indexName = parameters.get("index");
        String question = parameters.get("question");
        SearchRequest searchRequest = buildSearchRequest(indexName);
        GetMappingsRequest getMappingsRequest = buildGetMappingRequest(indexName);
        client.admin().indices().getMappings(getMappingsRequest, ActionListener.<GetMappingsResponse>wrap(getMappingsResponse -> {
            Map<String, MappingMetadata> mappings = getMappingsResponse.getMappings();
            client.search(searchRequest, ActionListener.<SearchResponse>wrap(searchResponse -> {
                SearchHit[] searchHits = searchResponse.getHits().getHits();
                String tableInfo = constructTableInfo(searchHits, mappings, indexName);
                String prompt = constructPrompt(tableInfo, question, indexName);
                RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(Collections.singletonMap("prompt", prompt)).build();
                ActionRequest request = new MLPredictionTaskRequest(modelId, MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
                client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.<MLTaskResponse>wrap(mlTaskResponse -> {
                    ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
                    ModelTensors modelTensors = modelTensorOutput.getMlModelOutputs().get(0);
                    ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
                    Map<String, String> dataAsMap = (Map<String, String>) modelTensor.getDataAsMap();
                    String ppl = dataAsMap.get("output");

                    //Execute output here
                    listener.onResponse((T) ppl);
                        }, e -> {
                    log.info("fail to predict model: " + e);
                    listener.onFailure(e);
                        }
                ));
                    }, e -> {
                log.info("fail to search: " + e);
                listener.onFailure(e);
                    }

            ));
            }, e -> {
            log.info("fail to get mapping: " + e);
            listener.onFailure(e);
                })
        );

        //client.search(request);
        //client.admin().indices().getMappings(ActionListener< GetMappingsResponse >);
    }

    @Override
    public void setInputParser(Parser<?, ?> parser) {
        Tool.super.setInputParser(parser);
    }

    @Override
    public void setOutputParser(Parser<?, ?> parser) {
        Tool.super.setOutputParser(parser);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getName() {
        return name;
    }


    @Override
    public String getDescription() {
        return null;
    }


    @Override
    public boolean validate(Map<String, String> map) {
        return false;
    }

    public static class Factory implements Tool.Factory<PPLTool> {
        private Client client;

        private static PPLTool.Factory INSTANCE;
        public static PPLTool.Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (PPLTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new PPLTool.Factory();
                return INSTANCE;
            }
        }

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public PPLTool create(Map<String, Object> map) {
            return new PPLTool(client, (String)map.get("model_id"));
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

    private SearchRequest buildSearchRequest(String indexName)
    {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder
                .size(1)
                .query(new MatchAllQueryBuilder());
        //  client;
        SearchRequest request = new SearchRequest(new String[]{indexName}, searchSourceBuilder);
        return request;
    }

    private GetMappingsRequest buildGetMappingRequest(String indexName)
    {
        String [] indices = new String[] {indexName};
        GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
        getMappingsRequest.indices(indices);
        return getMappingsRequest;
    }

    private String constructTableInfo(SearchHit [] searchHits, Map<String, MappingMetadata> mappings, String indexName)
    {
        MappingMetadata mappingMetadata = mappings.get(indexName);
        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
        Map<String, String> fieldsToType = new HashMap<>();
        extractNamesTypes(mappingSource, fieldsToType, "");

        SearchHit hit = searchHits[0];
        Map<String, Object> sampleSource = hit.getSourceAsMap();
        Map<String, String> fieldsToSample = new HashMap<>();
        for (String key : fieldsToType.keySet()) {
            fieldsToSample.put(key, "");
        }
        extractSamples(sampleSource, fieldsToSample, "");
        StringJoiner tableInfoJoiner = new StringJoiner("\n");

        for (String key : fieldsToType.keySet()) {
            String line = "- " + key + ": " + fieldsToType.get(key) + " (" + fieldsToSample.get(key) + ")";
            tableInfoJoiner.add(line);
        }

        String tableInfo = tableInfoJoiner.toString();
        return tableInfo;
    }

    private String constructPrompt(String tableInfo, String question, String indexName)
    {
        String template = "Below is an instruction that describes a task, paired with the index and "
                + "corresponding fields that provides further context. Write a response that appropriately "
                + "completes the request.\n\n"
                + "### Instruction:\n"
                + "I have an opensearch index with fields in the following. Now I have a question: %s "
                + "Can you help me generate a PPL for that?\n\n"
                + "### Index:\n"
                + "%s\n\n"
                + "### Fields:\n"
                + "%s\n\n"
                + "### Response:\n";

        return String.format(template, question.strip(), indexName, tableInfo.strip());
    }

    private void extractNamesTypes(Map<String, Object> mappingSource, Map<String, String> fieldsToType, String prefix) {
        if (prefix.length() > 0) {
            prefix += ".";
        }

        for (Map.Entry<String, Object> entry : mappingSource.entrySet()) {
            String n = entry.getKey();
            Object v = entry.getValue();

            if (v instanceof Map) {
                Map<String, Object> vMap = (Map<String, Object>) v;
                if (vMap.containsKey("type")) {
                    fieldsToType.put(prefix + n, (String) vMap.get("type"));
                } else if (vMap.containsKey("properties")) {
                    extractNamesTypes((Map<String, Object>) vMap.get("properties"), fieldsToType, prefix + n);
                }
            }
        }
    }

    private static void extractSamples(Map<String, Object> sampleSource, Map<String, String> fieldsToSample, String prefix) {
        if (prefix.length() > 0) {
            prefix += ".";
        }

        for (Map.Entry<String, Object> entry : sampleSource.entrySet()) {
            String p = entry.getKey();
            Object v = entry.getValue();

            String fullKey = prefix + p;
            if (fieldsToSample.containsKey(fullKey)) {
                fieldsToSample.put(fullKey, gson.toJson(v));
            } else {
                if (v instanceof Map) {
                    // Assuming that `v` is a Map, as in the Python version
                    extractSamples((Map<String, Object>) v, fieldsToSample, fullKey);
                }
            }
        }
    }
}
