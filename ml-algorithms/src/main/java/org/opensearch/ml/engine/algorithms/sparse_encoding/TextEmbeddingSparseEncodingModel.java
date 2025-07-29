/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sparse_encoding;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.engine.algorithms.TextEmbeddingModel;
import org.opensearch.ml.engine.annotation.Function;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Function(FunctionName.SPARSE_ENCODING)
public class TextEmbeddingSparseEncodingModel extends TextEmbeddingModel {

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) {
        return new SparseEncodingTranslator();
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }

    @Override
    protected boolean isAsymmetricModel(MLAlgoParams mlParams) {
        return false;
    }
}
