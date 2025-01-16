/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.image_embedding;

import static org.opensearch.ml.engine.ModelHelper.IMAGE_PREPROCESSOR_CONFIG_FILE_NAME;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.SENTENCE_EMBEDDING;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.model.ImageEmbeddingPreprocessorConfig;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;

public class ImageEmbeddingTranslator implements ServingTranslator {
    private ImageEmbeddingPreprocessorConfig preprocessorConfig;

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) throws IOException {
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();

        String base64Image = input.getAsString(0);
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        Image inputImage = ImageFactory
            .getInstance()
            .fromInputStream(new ByteArrayInputStream(imageBytes))
            .resize(preprocessorConfig.imageWidthSize, preprocessorConfig.imageHeightSize, false);

        NDArray arrayImage = inputImage.toNDArray(manager);
        arrayImage = arrayImage.transpose(2, 0, 1).toType(DataType.FLOAT32, true); // array format must be (channels, height, width)
        arrayImage.setName("pixel_values");
        ndList.add(arrayImage);

        return ndList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        NDArray embeddings = list.get("last_hidden_state");
        if (embeddings == null) {
            embeddings = list.get(0);
        }

        Number[] data = embeddings.toArray();

        List<ModelTensor> outputs = new ArrayList<>();
        long[] shape = embeddings.getShape().getShape();
        ModelTensor modelTensor = ModelTensor
            .builder()
            .name(SENTENCE_EMBEDDING)
            .data(data)
            .shape(shape)
            .dataType(MLResultDataType.FLOAT32)
            .build();
        outputs.add(modelTensor);

        Output output = new Output(200, "OK");
        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        this.preprocessorConfig = new ImageEmbeddingPreprocessorConfig(path.resolve(IMAGE_PREPROCESSOR_CONFIG_FILE_NAME));
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {}
}
