package org.opensearch.ml.engine.tools;


import lombok.Getter;
import lombok.Setter;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.transport.client.Client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;

@ToolAnnotation(ClusterHealthTool.TYPE)
public class ClusterHealthTool implements Tool {
    public static final String TYPE = "ClusterHealthTool";
    public static final String DEFAULT_DESCRIPTION = String
            .join(
                    " ",
                    "This tool gets health information of the cluster."
            );
    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\","
                                                      + "\"additionalProperties\":false}";


    @Setter
    @Getter
    private String name = ClusterHealthTool.TYPE;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    @Setter
    private Map<String, Object> attributes;

    public static final String STRICT_FIELD = "strict";

    @Getter
    private String version;

    private Client client;
    @Setter
    private Parser<?, ?> inputParser;
    @Setter
    private Parser<?, ?> outputParser;
    @SuppressWarnings("unused")
    private ClusterService clusterService;

    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);

    public ClusterHealthTool(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;

        outputParser = new Parser<>() {
            @Override
            public Object parse(Object o) {
                @SuppressWarnings("unchecked")
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };

        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, false);
    }


    private void sendClusterHealthRequest(
            final Client client,
            final ActionListener<ClusterHealthResponse> listener
    ) {

        final ClusterHealthRequest request = new ClusterHealthRequest();
        request.local(true);

        client.admin().cluster().health(request, listener);
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        sendClusterHealthRequest(client, ActionListener.wrap(clusterHealthResponse -> {
            listener.onResponse((T) clusterHealthResponse.toString());
        }, e -> { listener.onFailure(e);}));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }


    public static class Factory implements Tool.Factory<ClusterHealthTool> {
        private Client client;
        private ClusterService clusterService;

        private static ClusterHealthTool.Factory INSTANCE;

        /**
         * Create or return the singleton factory instance
         */
        public static ClusterHealthTool.Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (ClusterHealthTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new ClusterHealthTool.Factory();
                return INSTANCE;
            }
        }

        /**
         * Initialize this factory
         * @param client The OpenSearch client
         * @param clusterService The OpenSearch cluster service
         */
        public void init(Client client, ClusterService clusterService) {
            this.client = client;
            this.clusterService = clusterService;
        }

        @Override
        public ClusterHealthTool create(Map<String, Object> map) {
            return new ClusterHealthTool(client, clusterService);
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
        public String getDefaultVersion() {
            return null;
        }

        @Override
        public Map<String, Object> getDefaultAttributes() {
            return DEFAULT_ATTRIBUTES;
        }
    }


}
