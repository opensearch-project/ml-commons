package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryAccessControlHelper {
    private volatile Boolean memoryAccessControlEnabled;

    public void validateMemoryContainerAccess(
        SdkClient sdkClient,
        Client client,
        User user,
        String containerId,
        String tenantId,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ActionListener<Boolean> listener
    ) {
        if (containerId == null
            || (!mlFeatureEnabledSetting.isMultiTenancyEnabled()
                && (isAdmin(user) || !isSecurityEnabledAndMemoryContainerControlEnabled(user)))) {
            listener.onResponse(true);
            return;
        }

        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(containerId)
            .tenantId(tenantId)
            .build();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable == null) {
                    try {
                        GetResponse gr = r.getResponse();
                        if (gr != null && gr.isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLMemoryContainer mlMemoryContainer = MLMemoryContainer.parse(parser);
                                if (TenantAwareHelper
                                    .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlMemoryContainer.getTenantId(), listener)) {
                                    if (isAdmin(user) || !isSecurityEnabledAndMemoryContainerControlEnabled(user)) {
                                        listener.onResponse(true);
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse ML Memory Container");
                                wrappedListener.onFailure(e);
                            }
                        } else {
                            wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find memory container"));
                        }
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                } else {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find memory container"));
                    } else {
                        log.error("Fail to get memory container", e);
                        wrappedListener.onFailure(new MLValidationException("Fail to get memory container"));
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to validate Access", e);
            listener.onFailure(e);
        }
    }

    public boolean isSecurityEnabledAndMemoryContainerControlEnabled(User user) {
        return user != null && memoryAccessControlEnabled;
    }

    public boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(user.getRoles())) {
            return false;
        }
        return user.getRoles().contains("all_access");
    }
}
