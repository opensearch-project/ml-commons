/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.ml.common.prompt.MLPrompt.LANGFUSE;
import static org.opensearch.ml.common.prompt.MLPrompt.MLPROMPT;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.prompt.PromptExtraConfig;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;

import lombok.Getter;

@Getter
public abstract class AbstractPromptManagement implements ToXContentObject {
    public static final String PROMPT_ID_FIELD = "prompt_id";

    public static AbstractPromptManagement init(String promptManagementType, PromptExtraConfig extraConfig) {
        // add additional case for new prompt management client below, if needed
        switch (promptManagementType.toUpperCase()) {
            case MLPROMPT:
                return new MLPromptManagement();
            case LANGFUSE:
                return new LangfusePromptManagement(extraConfig.getPublicKey(), extraConfig.getAccessKey());
            default:
                throw new IllegalArgumentException("Unsupported prompt management type: " + promptManagementType);
        }
    }

    public abstract MLPrompt createPrompt(MLCreatePromptInput mlCreatePromptInput);

    public abstract void getPrompt(MLPrompt mlPrompt);
}
