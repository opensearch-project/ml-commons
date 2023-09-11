/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.tokenize;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.engine.Engine;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.algorithms.TextEmbeddingModel;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.*;
import java.util.stream.Collectors;

import static org.opensearch.ml.engine.ModelHelper.*;
import static org.opensearch.ml.engine.ModelHelper.ONNX_FILE_EXTENSION;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;

@Log4j2
@Function(FunctionName.TOKENIZE)
public class TokenizerModel extends DLModel {
    private HuggingFaceTokenizer tokenizer;

    private Map<String, Float> idf;

    @Override
    public ModelTensorOutput innerPredict(MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;
        ModelResultFilter resultFilter = textDocsInput.getResultFilter();
        for (String doc : textDocsInput.getDocs()) {
            Output output = new Output(200, "OK");
            Encoding encodings = tokenizer.encode(doc);
            long[] indices = encodings.getIds();
            List<ModelTensor> outputs = new ArrayList<>();
            String[] tokens = Arrays.stream(indices)
                    .mapToObj(value -> new long[]{value})
                    .map(value -> this.tokenizer.decode(value, true))
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
            Map<String, Float> tokenWeights = Arrays.stream(tokens)
                    .collect(Collectors.toMap(
                            token -> token,
                            token -> idf.getOrDefault(token, 1.0f)
                    ));
            Map<String, List<Map<String, Float> > > resultMap = new HashMap<>();
            List<Map<String, Float> > listOftokenWeights = new ArrayList<>();
            listOftokenWeights.add(tokenWeights);
            resultMap.put("response", listOftokenWeights);
            ModelTensor tensor = ModelTensor.builder()
                    .dataAsMap(resultMap)
                    .build();
            outputs.add(tensor);
            ModelTensors modelTensorOutput = new ModelTensors(outputs);
            output.add(modelTensorOutput.toBytes());
            tensorOutputs.add(parseModelTensorOutput(output, resultFilter));
        }
        return new ModelTensorOutput(tensorOutputs);
    }

    protected void innerLoadModel(List<Predictor<Input, Output>> predictorList, List<ZooModel<Input, Output>> modelList,
                                  String engine,
                                  Path modelPath,
                                  MLModelConfig modelConfig) throws ModelNotFoundException, MalformedModelException, IOException, TranslateException {
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(modelPath.resolve("tokenizer.json")).build();
        if (Files.exists(modelPath.resolve("idf.json"))){
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Float>>() {}.getType();
            idf = gson.fromJson(new InputStreamReader(Files.newInputStream(modelPath.resolve("idf.json"))), mapType);
        }
        log.info("tokenize Model {} is successfully deployed", modelId);
    }
    @Override
    public boolean isModelReady() {
        if (modelHelper == null || modelId == null) {
            return false;
        }
        return true;
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
