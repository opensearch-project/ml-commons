/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ingest.IngestMetadata;
import org.opensearch.ingest.PipelineConfiguration;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ChatConnector;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.sort.SortOrder;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.connector.ChatConnector.CONTENT_INDEX;
import static org.opensearch.ml.common.connector.ConnectorNames.CHAT_V1;
import static org.opensearch.ml.common.connector.ChatConnector.CONTENT_DOC_SIZE_FIELD;
import static org.opensearch.ml.common.connector.ChatConnector.CONTENT_FIELD_FIELD;
import static org.opensearch.ml.common.connector.ChatConnector.SESSION_SIZE_FIELD;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.signRequest;
import static org.opensearch.ml.engine.utils.ScriptUtils.gson;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

@Log4j2
@ConnectorExecutor(CHAT_V1)
public class ChatConnectorExecutor implements RemoteConnectorExecutor{

    private NamedXContentRegistry xContentRegistry;
    private Client client;
    private ChatConnector connector;
    @Setter
    private ClusterService clusterService;
    @Setter
    private ScriptService scriptService;

    public ChatConnectorExecutor(Connector connector) {
        this.connector = (ChatConnector)connector;
    }

    @Override
    public ModelTensorOutput executePredict(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();

        RemoteInferenceInputDataSet inputData = null;
        if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            inputData = (RemoteInferenceInputDataSet)mlInput.getInputDataset();
        } else {
            throw new IllegalArgumentException("Wrong input type");
        }

        Map<String, String> parameters = new HashMap<>();
        if (connector.getParameters() != null) {
            parameters.putAll(connector.getParameters());
        }
        if (inputData.getParameters() != null) {
            parameters.putAll(inputData.getParameters());
        }

        String question = parameters.get("question");
        Boolean withMyContent = Boolean.parseBoolean(parameters.get("with_my_content"));

        AtomicReference<String> contextRef = new AtomicReference<>("");
        AtomicReference<Exception> exceptionRef = new AtomicReference<>(null);
        String contentIndex = parameters.containsKey(CONTENT_INDEX)? parameters.get(CONTENT_INDEX) : connector.getContentIndex();
        if (withMyContent && contentIndex != null) {
            if (!clusterService.state().metadata().hasIndex(contentIndex)) {
                throw new IllegalArgumentException("Index not found: " + contentIndex);
            }
            if (connector.getContentFields() == null && !parameters.containsKey(CONTENT_FIELD_FIELD)) {
                throw new IllegalArgumentException("Content field not set");
            }
            Settings settings = clusterService.state().metadata().index(contentIndex).getSettings();
            String ingestPipeline = settings.get("index.default_pipeline");
            if (ingestPipeline != null) {
                IngestMetadata ingest = (IngestMetadata)clusterService.state().getMetadata().customs().get("ingest");
                PipelineConfiguration pipelineConfiguration = ingest.getPipelines().get(ingestPipeline);
                Map<String, Object> configAsMap = pipelineConfiguration.getConfigAsMap();
                List processors = (List)configAsMap.get("processors");
                Map<String, Object> processor = (Map<String, Object>)processors.get(0);
                Map<String, Object> textEmbedding = (Map<String, Object>)processor.get("text_embedding");
                String modelId = (String)textEmbedding.get("model_id");
                Map<String, Object> fieldMap = (Map<String, Object>)textEmbedding.get("field_map");
                String contentField = connector.getParameters().get("content_fields");
                String knnField = (String)fieldMap.get(contentField);

                if (!parameters.containsKey("embedding_model_id")) {
                    parameters.put("embedding_model_id", modelId);
                }
                if (!parameters.containsKey("embedding_field")) {
                    parameters.put("embedding_field", knnField);
                }
            }

            try {
                Integer contentDocSize = connector.getContentDocSize();
                if (parameters.containsKey(CONTENT_DOC_SIZE_FIELD)) {
                    contentDocSize = Integer.parseInt(parameters.get(CONTENT_DOC_SIZE_FIELD));
                }
                String contentField = parameters.containsKey(CONTENT_FIELD_FIELD) ? parameters.get(CONTENT_FIELD_FIELD) : connector.getContentFields();
                String query = connector.createNeuralSearchQuery(parameters);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                XContentParser queryParser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, query);
                searchSourceBuilder.parseXContent(queryParser);
                searchSourceBuilder.seqNoAndPrimaryTerm(true).version(true);
                searchSourceBuilder.size(contentDocSize);
                FetchSourceContext fetchSourceContext = searchSourceBuilder.fetchSource();
                String[] excludes = null;
                Set<String> includedFields = new HashSet<>();
                if (fetchSourceContext != null) {
                    String[] includes = fetchSourceContext.includes();
                    excludes = fetchSourceContext.excludes();
                    includedFields.addAll(Arrays.asList(includes));
                    if (!includedFields.contains(contentField)) {
                        includedFields.add(contentField);
                    }
                } else {
                    includedFields.add(contentField);
                }

                searchSourceBuilder.fetchSource(includedFields.toArray(new String[0]), excludes);


                SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(contentIndex);
                CountDownLatch latch = new CountDownLatch(1);
                LatchedActionListener listener = new LatchedActionListener<SearchResponse>(ActionListener.wrap(r -> {
                    SearchHit[] hits = r.getHits().getHits();

                    if (hits != null && hits.length > 0) {
                        StringBuilder contextBuilder = new StringBuilder();
                        for (int i = 0; i < hits.length; i++) {
                            SearchHit hit = hits[i];
                            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                            String context = (String) sourceAsMap.get(connector.getContentFields());
                            contextBuilder.append("document_id: ").append(hit.getId()).append("\\\\nDocument context:").append(context).append("\\\\n");
                        }
                        contextRef.set(gson.toJson(contextBuilder.toString()));
                    }
                }, e -> {
                    log.error("Failed to search index", e);
                    exceptionRef.set(e);
                }), latch);
                client.search(searchRequest, listener);

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                if (exceptionRef.get() != null) {
                    throw new MLException(exceptionRef.get());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        String sessionId = parameters.get("session_id");
        if (sessionId != null && connector.getSessionIndex() != null) {
            try {
                Integer sessionSize = connector.getSessionSize();
                if (parameters.containsKey("session_size")) {
                    sessionSize = Integer.parseInt(parameters.get(SESSION_SIZE_FIELD));
                }
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                searchSourceBuilder.query(new TermQueryBuilder(connector.getSessionIdField(), sessionId));
                searchSourceBuilder.sort("created_time", SortOrder.DESC);
                searchSourceBuilder.size(sessionSize);
                SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(connector.getSessionIndex());

                CountDownLatch latch = new CountDownLatch(1);
                LatchedActionListener listener = new LatchedActionListener<SearchResponse>(ActionListener.wrap(r -> {
                    SearchHit[] hits = r.getHits().getHits();

                    if (hits != null && hits.length > 0) {
                        StringBuilder contextBuilder = new StringBuilder("Chat history: \\\\n");
                        for (int i = hits.length - 1; i >= 0; i--) {
                            SearchHit hit = hits[i];
                            String historicalQuestion = (String) hit.getSourceAsMap().get("question");
                            String historicalAnswer = (String) hit.getSourceAsMap().get("answer");
                            contextBuilder.append("Question: ").append(historicalQuestion).append("\\\\nAnswer:").append(historicalAnswer).append("\\\\n");
                        }
                        String myContent = contextRef.get().length() > 0 ? gson.fromJson(contextRef.get(), String.class) : "";
                        String context = myContent + contextBuilder.toString();
                        contextRef.set(gson.toJson(context));
                    }
                }, e -> {
                    log.error("Failed to search index", e);
                    exceptionRef.set(e);
                }), latch);
                client.search(searchRequest, listener);

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                if (exceptionRef.get() != null) {
                    throw new MLException(exceptionRef.get());
                }
            } catch (Exception e) {
                log.error("Failed to search sessions", e);
            }
        } else {
            sessionId = UUID.randomUUID().toString();
        }


        AtomicReference<String> responseRef = new AtomicReference<>("");

        Map<String, String> newParameters = new HashMap<>();
        newParameters.putAll(parameters);
        newParameters.put("question", question);
        newParameters.put("context", contextRef.get());
        String payload = connector.createPredictPayload(newParameters);

        if (connector.hasAwsCredential()) {
            try {
                RequestBody requestBody = RequestBody.fromString(payload);

                SdkHttpFullRequest.Builder builder = SdkHttpFullRequest.builder()
                        .method(POST)
                        .uri(URI.create(connector.getEndpoint()))
                        .contentStreamProvider(requestBody.contentStreamProvider());
                Map<String, String> headers = connector.getDecryptedHeaders();
                for (String key : headers.keySet()) {
                    builder.putHeader(key, headers.get(key));
                }
                SdkHttpFullRequest sdkHttpFullRequest = builder.build();
                HttpExecuteRequest executeRequest = HttpExecuteRequest.builder()
                        .request(signRequest(sdkHttpFullRequest, connector.getAccessKey(), connector.getSecretKey(), connector.getServiceName(), connector.getRegion()))
                        .contentStreamProvider(sdkHttpFullRequest.contentStreamProvider().orElse(null))
                        .build();

                SdkHttpClient sdkHttpClient = new DefaultSdkHttpClientBuilder().build();
                HttpExecuteResponse response = AccessController.doPrivileged((PrivilegedExceptionAction<HttpExecuteResponse>) () -> {
                    return sdkHttpClient.prepareRequest(executeRequest).call();
                });

                AbortableInputStream body = null;
                if (response.responseBody().isPresent()) {
                    body = response.responseBody().get();
                }

                StringBuilder responseBuilder = new StringBuilder();
                if (body != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                    }
                }
                String modelResponse = responseBuilder.toString();

                ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters, modelTensors);
                tensorOutputs.add(tensors);
                if (connector.getSessionIndex() != null) {
                    IndexRequest indexRequest = new IndexRequest(connector.getSessionIndex());
                    List results = (List)modelTensors.get(0).getDataAsMap().get("results");
                    indexRequest.source(ImmutableMap.of(connector.getSessionIdField(), sessionId,
                            "question", question,
                            "answer", ((Map)results.get(0)).get("outputText"),
                            "created_time", Instant.now().toEpochMilli()));
                    client.index(indexRequest);
                    modelTensors.add(ModelTensor.builder().name("session_id").result(sessionId).build());
                }
                return new ModelTensorOutput(tensorOutputs);
            } catch (Throwable e) {
                log.error("Failed to execute chat connector", e);
                throw new MLException("Fail to execute chat connector", e);
            }
        }

        try {
            HttpUriRequest request;
            switch (connector.getPredictHttpMethod().toUpperCase(Locale.ROOT)) {
                case "POST":
                    try {
                        request = new HttpPost(connector.getEndpoint());
                        HttpEntity entity = new StringEntity(payload);
                        ((HttpPost)request).setEntity(entity);
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                case "GET":
                    try {
                        request = new HttpGet(connector.getEndpoint());
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            Map<String, ?> headers = connector.getDecryptedHeaders();
            boolean hasContentTypeHeader = false;
            for (String key : headers.keySet()) {
                request.addHeader(key, (String)headers.get(key));
                if (key.toLowerCase().equals("Content-Type")) {
                    hasContentTypeHeader = true;
                }
            }
            if (!hasContentTypeHeader) {
                request.addHeader("Content-Type", "application/json");
            }
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                try (CloseableHttpClient httpClient = MLHttpClientFactory.getCloseableHttpClient();
                     CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity responseEntity = response.getEntity();
                    String responseBody = EntityUtils.toString(responseEntity);
                    EntityUtils.consume(responseEntity);
                    responseRef.set(responseBody);
                }
                return null;
            });
            String modelResponse = responseRef.get();

            ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters, modelTensors);
            tensorOutputs.add(tensors);
            if (connector.getSessionIndex() != null) {
                IndexRequest indexRequest = new IndexRequest(connector.getSessionIndex());
                indexRequest.source(ImmutableMap.of(connector.getSessionIdField(), sessionId,
                        "question", question,
                        "answer", modelTensors.get(0).getDataAsMap().get("response"),
                        "created_time", Instant.now().toEpochMilli()));
                client.index(indexRequest);
                modelTensors.add(ModelTensor.builder().name("session_id").result(sessionId).build());
            }
            return new ModelTensorOutput(tensorOutputs);
        } catch (Throwable e) {
            log.error("Fail to execute qa connector", e);
            throw new MLException("Fail to execute qa connector", e);
        }
    }

    @Override
    public void setClient(Client client) {
        this.client = client;
    }

    @Override
    public void setXContentRegistry(NamedXContentRegistry xContentRegistry) {
        this.xContentRegistry = xContentRegistry;
    }
}
