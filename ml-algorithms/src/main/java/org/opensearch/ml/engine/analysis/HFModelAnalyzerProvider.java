/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractIndexAnalyzerProvider;

/**
 * Provider class for HFModelAnalyzer instances.
 * Handles the creation and configuration of HFModelAnalyzer instances within OpenSearch.
 */
public class HFModelAnalyzerProvider extends AbstractIndexAnalyzerProvider<HFModelAnalyzer> {
    private final HFModelAnalyzer analyzer;

    public HFModelAnalyzerProvider(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
        HFModelTokenizerFactory tokenizerFactory = new HFModelTokenizerFactory(indexSettings, environment, name, settings);
        analyzer = new HFModelAnalyzer(tokenizerFactory::create);
    }

    @Override
    public HFModelAnalyzer get() {
        return analyzer;
    }
}
