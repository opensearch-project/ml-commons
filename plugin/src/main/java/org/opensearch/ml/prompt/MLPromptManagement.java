/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLImportPromptInput;

import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptInput;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

@Log4j2
public class MLPromptManagement extends AbstractPromptManagement {
    public static final String INITIAL_VERSION = "1";

    public MLPromptManagement() {}

    @Override
    public MLPrompt createPrompt(MLCreatePromptInput mlCreatePromptInput) {
        String version = mlCreatePromptInput.getVersion();
        return MLPrompt
            .builder()
            .name(mlCreatePromptInput.getName())
            .description(mlCreatePromptInput.getDescription())
            .version(version == null ? INITIAL_VERSION : version)
            .prompt(mlCreatePromptInput.getPrompt())
            .promptManagementType(mlCreatePromptInput.getPromptManagementType())
            .tags(mlCreatePromptInput.getTags())
            .promptExtraConfig(mlCreatePromptInput.getPromptExtraConfig())
            .tenantId(mlCreatePromptInput.getTenantId())
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();
    }

    @Override
    public void getPrompt(MLPrompt mlPrompt) {
        // do nothing
    }

    @Override
    public UpdateDataObjectRequest updatePrompt(MLUpdatePromptInput mlUpdatePromptInput, MLPrompt mlPrompt) {
        int version = Integer.parseInt(mlPrompt.getVersion());
        mlUpdatePromptInput.setLastUpdateTime(Instant.now());
        mlUpdatePromptInput.setVersion(String.valueOf(version + 1));

        return UpdateDataObjectRequest
            .builder()
            .index(ML_PROMPT_INDEX)
            .id(mlPrompt.getPromptId())
            .tenantId(mlUpdatePromptInput.getTenantId())
            .dataObject(mlUpdatePromptInput)
            .build();
    }

    @Override
    public List<MLPrompt> importPrompts(MLImportPromptInput mlImportPromptInput) {
        throw new OpenSearchStatusException("Import prompt is not supported for MLPromptManagement", RestStatus.BAD_REQUEST);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder;
    }
}
