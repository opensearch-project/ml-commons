package org.opensearch.ml.action.IndexInsight;

import lombok.extern.log4j.Log4j2;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.indexInsight.IndexInsightContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerPutAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerPutRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerPutResponse;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import javax.swing.*;

import java.util.HashMap;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.FIXED_INDEX_INSIGHT_CONTAINER_ID;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONTAINER_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsight.CONTENT_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.LAST_UPDATE_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.STATUS_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

@Log4j2
public class PutIndexInsightContainerTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightContainerPutResponse> {
    private Client client;
    private final SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public PutIndexInsightContainerTransportAction(TransportService transportService,
                                                   ActionFilters actionFilters,
                                                   NamedXContentRegistry xContentRegistry,
                                                   MLFeatureEnabledSetting mlFeatureEnabledSetting,
                                                   Client client, SdkClient sdkClient) {
        super(MLIndexInsightContainerPutAction.NAME, transportService, actionFilters, MLIndexInsightContainerPutRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.sdkClient = sdkClient;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightContainerPutResponse> listener) {
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = MLIndexInsightContainerPutRequest.fromActionRequest(request);
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlIndexInsightContainerPutRequest.getTenantId(), listener)) {
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        String tenantId = mlIndexInsightContainerPutRequest.getTenantId();
        IndexInsightContainer indexInsightContainer = IndexInsightContainer.builder().indexName(mlIndexInsightContainerPutRequest.getIndexName()).tenantId(tenantId).build();
        checkIfBeforeIndexContainer(indexInsightContainer, ActionListener.wrap( r -> {
            indexIndexInsightContainer(indexInsightContainer, ActionListener.wrap(r1 -> {
                        initIndexInsightIndex(mlIndexInsightContainerPutRequest.getIndexName(), ActionListener.wrap(
                                r2 -> {
                                    log.info("Successfully created index insight container");
                                    listener.onResponse(new MLIndexInsightContainerPutResponse());
                                },
                                e -> {
                                    log.error("Failed to create index insight container", e);
                                    listener.onFailure(e);
                                }));
                    },
                    listener::onFailure
            ));

                }, listener::onFailure
        ));

    }

    private void deleteOriginalIndexInsightIndex(String indexName, ActionListener<Boolean> listener) {
        client.admin().indices().delete(
          new DeleteIndexRequest(indexName),
          ActionListener.wrap(r -> {
              if (r.isAcknowledged()) {
                  listener.onResponse(true);
              } else {
                  listener.onFailure(new RuntimeException("Failed to delete original index insight data index: " + indexName));
              }
          }, listener::onFailure)
        );
    }

    private void checkIfBeforeIndexContainer(IndexInsightContainer indexInsightContainer, ActionListener<Boolean> listener){
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                    .getDataObjectAsync(
                            GetDataObjectRequest
                                    .builder()
                                    .tenantId(indexInsightContainer.getTenantId())
                                    .index(ML_INDEX_INSIGHT_CONTAINER_INDEX)
                                    .id(FIXED_INDEX_INSIGHT_CONTAINER_ID)
                                    .build()
                    )
                    .whenComplete((r, throwable) -> {
                        context.restore();
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            log.error("Failed to index index insight container", cause);
                            if (cause.getCause() instanceof IndexNotFoundException || cause.getCause()  instanceof ResourceNotFoundException) {
                                listener.onResponse(true);
                            }
                            listener.onFailure(cause);
                        } else {
                            try {
                                GetResponse getResponse = r.getResponse();
                                if (getResponse.isExists()) {
                                    listener.onResponse(true);
                                    return;
                                }
                                assert getResponse != null;
                                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, getResponse.getSourceAsBytesRef())) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                    IndexInsightContainer returnedContainer = indexInsightContainer.parse(parser);
                                    String originalIndexName = returnedContainer.getIndexName();
                                    // delete the original index
                                    deleteOriginalIndexInsightIndex(originalIndexName, ActionListener.wrap(
                                            r1 -> {
                                                log.info("Successfully deleted original index insight data index: {}", originalIndexName);
                                                listener.onResponse(true);
                                            },
                                            e -> {
                                                log.error("Failed to delete original index insight data index: {}", originalIndexName, e);
                                                listener.onFailure(e);
                                            }
                                    ));
                                } catch (Exception e) {
                                    listener.onFailure(e);
                                }
                            } catch (Exception e) {
                                listener.onFailure(e);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to create index insight container", e);
            listener.onFailure(e);
        }
    }

    private void indexIndexInsightContainer(IndexInsightContainer indexInsightContainer, ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                    .putDataObjectAsync(
                            PutDataObjectRequest
                                    .builder()
                                    .tenantId(indexInsightContainer.getTenantId())
                                    .index(ML_INDEX_INSIGHT_CONTAINER_INDEX)
                                    .dataObject(indexInsightContainer)
                                    .id(FIXED_INDEX_INSIGHT_CONTAINER_ID)
                                    .build()
                    )
                    .whenComplete((r, throwable) -> {
                        context.restore();
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            log.error("Failed to index index insight container", cause);
                            listener.onFailure(cause);
                        } else {
                            try {
                                IndexResponse indexResponse = r.indexResponse();
                                assert indexResponse != null;
                                if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                                    String generatedId = indexResponse.getId();
                                    log.info("Successfully created index insight with ID: {}", generatedId);
                                    listener.onResponse(true);
                                } else {
                                    listener.onFailure(new RuntimeException("Failed to create index insight container"));
                                }
                            } catch (Exception e) {
                                listener.onFailure(e);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to create index insight container", e);
            listener.onFailure(e);
        }
    }

    private void initIndexInsightIndex(String indexName,  ActionListener<Boolean> listener) {
        Map<String, Object> indexSettings = new HashMap<>();
        Map<String, Object> indexMappings = new HashMap<>();

        // Build index mappings based on semantic storage config
        Map<String, Object> properties = new HashMap<>();

        // Common fields for all index types
        // Use keyword type for ID fields that need exact matching
        properties.put(INDEX_NAME_FIELD, Map.of("type", "keyword"));
        properties.put(CONTENT_FIELD, Map.of("type", "text"));
        properties.put(STATUS_FIELD, Map.of("type", "text"));
        properties.put(TASK_TYPE_FIELD, Map.of("type", "text")); // Keep as text for full-text search
        properties.put(LAST_UPDATE_FIELD, Map.of("type", "date", "format", "strict_date_time||epoch_millis"));
        indexMappings.put("properties", properties);
        client
                .admin()
                .indices().create(
                        new org.opensearch.action.admin.indices.create.CreateIndexRequest(indexName)
                                .settings(indexSettings)
                                .mapping(indexMappings),
                        ActionListener.wrap(response -> {
                            if (response.isAcknowledged()) {
                                log.info("Successfully created index insight data index: {}", indexName);
                                listener.onResponse(true);
                            } else {
                                listener.onFailure(new RuntimeException("Failed to create index insight data index: " + indexName));
                            }
                        }, e -> {
                            if (e instanceof org.opensearch.ResourceAlreadyExistsException) {
                                log.info("index insight data index already exists: {}", indexName);
                                listener.onResponse(true);
                            } else {
                                log.error("Error creating index insight data index: {}", indexName, e);
                                listener.onFailure(e);
                            }
                        })
                );
    }
}
