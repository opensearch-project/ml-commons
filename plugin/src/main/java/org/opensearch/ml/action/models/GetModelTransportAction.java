/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.MLModel.IS_HIDDEN_FIELD;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetModelTransportAction extends HandledTransportAction<ActionRequest, MLModelGetResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;

    final ModelAccessControlHelper modelAccessControlHelper;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    Settings settings;

    @Inject
    public GetModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        Settings settings,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelGetAction.NAME, transportService, actionFilters, MLModelGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.settings = settings;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLModelGetResponse> actionListener) {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.fromActionRequest(request);
        String modelId = mlModelGetRequest.getModelId();
        String tenantId = mlModelGetRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_MODEL_INDEX)
            .id(modelId)
            .fetchSourceContext(fetchSourceContext)
            .build();
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModelGetResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            client.get(getRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        String algorithmName = r.getSource().get(ALGORITHM_FIELD).toString();
                        Boolean isHidden = (Boolean) r.getSource().get(IS_HIDDEN_FIELD);
                        MLModel mlModel = MLModel.parse(parser, algorithmName);
                        if (!TenantAwareHelper
                            .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlModel.getTenantId(), actionListener)) {
                            return;
                        }
                        if (isHidden != null && isHidden) {
                            if (isSuperAdmin || !mlModelGetRequest.isUserInitiatedGetRequest()) {
                                wrappedListener.onResponse(MLModelGetResponse.builder().mlModel(mlModel).build());
                            } else {
                                wrappedListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "User doesn't have privilege to perform this operation on this model",
                                            RestStatus.FORBIDDEN
                                        )
                                    );
                            }
                        } else {
                            modelAccessControlHelper
                                .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                                    if (!access) {
                                        wrappedListener
                                            .onFailure(
                                                new OpenSearchStatusException(
                                                    "User doesn't have privilege to perform this operation on this model",
                                                    RestStatus.FORBIDDEN
                                                )
                                            );
                                    } else {
                                        log.debug("Completed Get Model Request, id:{}", modelId);
                                        Connector connector = mlModel.getConnector();
                                        if (connector != null) {
                                            connector.removeCredential();
                                        }
                                        wrappedListener.onResponse(MLModelGetResponse.builder().mlModel(mlModel).build());
                                    }
                                }, e -> {
                                    log.error("Failed to validate Access for Model Id {}", modelId, e);
                                    wrappedListener.onFailure(e);
                                }));
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml model {}", r.getId(), e);
                        wrappedListener.onFailure(e);
                    }
                } else {
                    wrappedListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to find model with the provided model id: " + modelId,
                                RestStatus.NOT_FOUND
                            )
                        );
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model"));
                } else {
                    log.error("Failed to get ML model {}", modelId, e);
                    wrappedListener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to get ML model {}", modelId, e);
            actionListener.onFailure(e);
        }

    }

    // this method is only to stub static method.
    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
