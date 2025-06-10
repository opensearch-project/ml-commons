/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.ml.common.prompt.MLPrompt.LANGFUSE;
import static org.opensearch.ml.common.prompt.MLPrompt.MLPROMPT;

import java.util.List;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.prompt.PromptExtraConfig;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLImportPromptInput;

import lombok.Getter;

@Getter
public abstract class AbstractPromptManagement implements ToXContentObject {
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

    public abstract List<MLPrompt> importPrompts(MLImportPromptInput mlImportPromptInput);
}
