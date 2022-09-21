/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.custom;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Batchifier;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.custom_model.MLModelTensorOutput;
import org.opensearch.ml.common.model.MLResultDataType;
import org.opensearch.ml.common.output.custom_model.MLModelTensor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import  java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Log4j2
public class GeneralSentenceTransformerTranslator implements ServingTranslator {
    private HuggingFaceTokenizer tokenizer;

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        tokenizer = HuggingFaceTokenizer.newInstance(path.resolve("tokenizer.json"));
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        String sentence = input.getAsString("doc");
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();
        sentence = sentence.strip().toLowerCase(Locale.ROOT);
        Encoding encode = tokenizer.encode(sentence);

        long[] indices = encode.getIds();
        NDArray indicesArray;
        indicesArray = manager.create(indices);
        indicesArray.setName("input1.input_ids");

        long[] attentionMask = encode.getAttentionMask();
        NDArray attentionMaskArray;
        attentionMaskArray = manager.create(attentionMask);
        attentionMaskArray.setName("input1.attention_mask");
        ndList.add(indicesArray);
        ndList.add(attentionMaskArray);
        return ndList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        List<MLModelTensor> outputs = new ArrayList<>();
        Iterator<NDArray> iterator = list.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            String name = ndArray.getName();
            DataType dataType = ndArray.getDataType();
            String device = ndArray.getDevice().toString();
            long[] shape = ndArray.getShape().getShape();
            ByteBuffer buffer = ndArray.toByteBuffer();
            Number[] data = ndArray.toArray();
            MLResultDataType mlResultDataType = MLResultDataType.valueOf(dataType.name());
            outputs.add(new MLModelTensor(name, data, shape, mlResultDataType, device, buffer));
        }
        MLModelTensorOutput barchModelTensorOutput = new MLModelTensorOutput(outputs);
        Output output = new Output(200, "OK");
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            barchModelTensorOutput.writeTo(bytesStreamOutput);
            bytesStreamOutput.flush();
            byte[] bytes = bytesStreamOutput.bytes().toBytesRef().bytes;
            output.add(bytes);
        } catch (Exception e) {
            log.error("Failed to process ML model output", e);
            throw new MLException("Failed to parse result");
        }
        return output;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
