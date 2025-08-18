/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.tokenize;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.analysis.DJLUtils;
import org.opensearch.ml.engine.annotation.Function;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;

/**
 * Tokenizer model will load from two file: tokenizer file and IDF file.
 * For IDF file, it is a predefined weight for each Token. It is calculated like the BM25 IDF for each dataset.
 * Decouple idf with model inference will give a more tight upperbound for predicted token weight. And this can help accelerate the WAND algorithm for lucene 9.7.
 * IDF introduces global token information, boost search relevance.
 * In our pretrained Tokenizer model, we will provide a general IDF from MSMARCO. Customer could recalculate a IDF in their own dataset.
 * If without IDF, the weight of each token in the result will be set to 1.0.
 * Since we regard tokenizer as a model. Cusotmer needs to keep the consistency between tokenizer/model by themselves.
 */
@Log4j2
@Function(FunctionName.SPARSE_TOKENIZE)
public class SparseTokenizerModel extends DLModel {
    private HuggingFaceTokenizer tokenizer;

    private Map<String, Float> idf;

    public String IDF_FILE_NAME = "idf.json";

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;

        // Get the embedding format from parameters
        MLAlgoParams parameters = mlInput.getParameters();
        SparseEmbeddingFormat sparseEmbeddingFormat = SparseEmbeddingFormat.WORD; // default

        if (parameters instanceof AsymmetricTextEmbeddingParameters) {
            AsymmetricTextEmbeddingParameters sparseParams = (AsymmetricTextEmbeddingParameters) parameters;
            sparseEmbeddingFormat = sparseParams.getSparseEmbeddingFormat();
        }

        for (String doc : textDocsInput.getDocs()) {
            Encoding encodings = tokenizer.encode(doc);
            long[] indices = encodings.getIds();
            long[] uniqueIndices = Arrays.stream(indices).distinct().toArray();
            String[] tokens = Arrays
                .stream(uniqueIndices)
                .mapToObj(value -> this.tokenizer.decode(new long[] { value }, true))
                .toArray(String[]::new);

            Map<String, Float> tokenWeights = new HashMap<>();
            for (int i = 0; i < uniqueIndices.length; i++) {
                String token = tokens[i];
                if (token.isEmpty()) {
                    continue;
                }
                if (sparseEmbeddingFormat == SparseEmbeddingFormat.TOKEN_ID) {
                    tokenWeights.put(String.valueOf(uniqueIndices[i]), idf.getOrDefault(token, 1.0f));
                } else {
                    tokenWeights.put(token, idf.getOrDefault(token, 1.0f));
                }
            }

            Map<String, ?> wrappedMap = Map.of(ML_MAP_RESPONSE_KEY, Collections.singletonList(tokenWeights));
            ModelTensor tensor = ModelTensor.builder().dataAsMap(wrappedMap).build();
            tensorOutputs.add(new ModelTensors(List.of(tensor)));
        }
        return new ModelTensorOutput(tensorOutputs);
    }

    protected void doLoadModel(
        List<Predictor<Input, Output>> predictorList,
        List<ZooModel<Input, Output>> modelList,
        String engine,
        Path modelPath,
        MLModelConfig modelConfig
    ) throws ModelNotFoundException,
        MalformedModelException,
        IOException,
        TranslateException {
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(modelPath.resolve("tokenizer.json")).build();
        idf = new HashMap<>();
        if (Files.exists(modelPath.resolve(IDF_FILE_NAME))) {
            idf = DJLUtils.fetchTokenWeights(modelPath.resolve(IDF_FILE_NAME));
        }
        log.info("sparse tokenize Model {} is successfully deployed", modelId);
    }

    @Override
    public boolean isModelReady() {
        if (modelHelper == null || modelId == null || tokenizer == null) {
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        if (modelHelper != null && modelId != null) {
            modelHelper.deleteFileCache(modelId);
            if (idf != null || tokenizer != null) {
                tokenizer = null;
                idf = null;
            }
        }
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) {
        return null;
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }

}
