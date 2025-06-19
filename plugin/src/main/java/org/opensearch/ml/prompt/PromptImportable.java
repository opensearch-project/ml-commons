package org.opensearch.ml.prompt;

import java.util.List;

import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLImportPromptInput;

public interface PromptImportable {

    public List<MLPrompt> importPrompts(MLImportPromptInput mlImportPromptInput);
}
