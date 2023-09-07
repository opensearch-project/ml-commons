/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.tokenize;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.ArgumentsUtil;
import ai.djl.translate.Batchifier;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.stream.*;
import java.lang.reflect.Type;

@Log4j2
public class TokenizerTranslator implements ServingTranslator {
    private HuggingFaceTokenizer tokenizer;
    private Path modelPath;
    private Map<String, Float> idf;
    public TokenizerTranslator(Path modelPath)
    {
        this.modelPath = modelPath;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        if (this.modelPath != null) {
            path = this.modelPath;
        }
        idf = new HashMap<>();
        if (Files.exists(path.resolve("idf.json"))){
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Float>>() {}.getType();
            idf = gson.fromJson(new InputStreamReader(Files.newInputStream(path.resolve("idf.json"))), mapType);
        }
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(path.resolve("tokenizer.json")).build();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        String sentence = input.getAsString(0);
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();
        Encoding encodings = tokenizer.encode(sentence);
        long[] indices = encodings.getIds();

        NDArray indicesArray = manager.create(indices);
        indicesArray.setName("input1.input_ids");

        ndList.add(indicesArray);
        return ndList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");

        List<ModelTensor> outputs = new ArrayList<>();
        NDArray ndArray = list.get(0);
        String name = ndArray.getName();

        long[] ids = ndArray.toLongArray();
        String[] tokens = Arrays.stream(ids)
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
        ByteBuffer buffer = ndArray.toByteBuffer();
        ModelTensor tensor = ModelTensor.builder()
                .name(name)
                .dataAsMap(resultMap)
                .byteBuffer(buffer)
                .build();
        outputs.add(tensor);

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
