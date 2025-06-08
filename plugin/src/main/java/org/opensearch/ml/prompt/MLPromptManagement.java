/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLPromptManagement extends AbstractPromptManagement {
    private static final String INITIAL_VERSION = "1";

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
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder;
    }
}