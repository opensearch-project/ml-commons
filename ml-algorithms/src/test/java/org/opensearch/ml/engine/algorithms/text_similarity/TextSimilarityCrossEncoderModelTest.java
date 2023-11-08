/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.engine.algorithms.text_similarity;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import ai.djl.Model;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslatorContext;
import lombok.extern.log4j.Log4j2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.DLModel.*;

@Log4j2
public class TextSimilarityCrossEncoderModelTest {

    private File modelZipFile;
    private String modelId;
    private String modelName;
    private FunctionName functionName;
    private String version;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private TextSimilarityCrossEncoderModel textSimilarityCrossEncoderModel;
    private Path mlCachePath;
    private Path mlConfigPath;
    private TextSimilarityInputDataSet inputDataSet;
    private int dimension = 384;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        modelId = "test_model_id";
        modelName = "test_model_name";
        functionName = FunctionName.TEXT_SIMILARITY;
        version = "1";
        model = MLModel.builder()
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .name("test_model_name")
                .modelId("test_model_id")
                .algorithm(FunctionName.TEXT_SIMILARITY)
                .version("1.0.0")
                .modelState(MLModelState.TRAINED)
                .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("TinyBERT-CE.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        textSimilarityCrossEncoderModel = new TextSimilarityCrossEncoderModel();

        inputDataSet = TextSimilarityInputDataSet.builder()
            .pairs(Arrays.asList(
                Pair.of("today is sunny", "That is a happy dog"), 
                Pair.of("today is sunny", "it's summer")))
            .build();
    }

    @Test
    public void test_TextSimilarity_Translator_ProcessInput() throws URISyntaxException, IOException {
        TextSimilarityTranslator textSimilarityTranslator = new TextSimilarityTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        textSimilarityTranslator.prepare(translatorContext);

        NDManager manager = mock(NDManager.class);
        when(translatorContext.getNDManager()).thenReturn(manager);
        Input input = mock(Input.class);
        String testSentence = "hello world";
        when(input.getAsString(0)).thenReturn(testSentence);
        when(input.getAsString(1)).thenReturn(testSentence);
        NDArray indiceNdArray = mock(NDArray.class);
        when(indiceNdArray.toLongArray()).thenReturn(new long[]{102l, 101l});
        when(manager.create((long[]) any())).thenReturn(indiceNdArray);
        doNothing().when(indiceNdArray).setName(any());
        NDList outputList = textSimilarityTranslator.processInput(translatorContext, input);
        assertEquals(3, outputList.size());
        Iterator<NDArray> iterator = outputList.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            long [] output = ndArray.toLongArray();
            assertEquals(2, output.length);
        }
    }

    @Test
    public void test_TextSimilarity_Translator_ProcessOutput() throws URISyntaxException, IOException {
        TextSimilarityTranslator textSimilarityTranslator = new TextSimilarityTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        textSimilarityTranslator.prepare(translatorContext);

        NDArray ndArray = mock(NDArray.class);
        Shape shape = mock(Shape.class);
        when(ndArray.nonzero()).thenReturn(ndArray);
        when(ndArray.squeeze()).thenReturn(ndArray);
        when(ndArray.getFloat(any())).thenReturn(1.0f);
        when(ndArray.toArray()).thenReturn(new Number[]{1.245f});
        when(ndArray.getName()).thenReturn("output");
        when(ndArray.getShape()).thenReturn(shape);
        when(shape.getShape()).thenReturn(new long[]{1});
        when(ndArray.getDataType()).thenReturn(DataType.FLOAT32);
        List<NDArray> ndArrayList = Collections.singletonList(ndArray);
        NDList ndList = new NDList(ndArrayList);
        Output output = textSimilarityTranslator.processOutput(translatorContext, ndList);
        log.info(output.toString());
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        log.info(tensorOutput.toString());
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        log.info(modelTensorsList.toString());
        assertEquals(1, modelTensorsList.size());
        ModelTensor modelTensor = modelTensorsList.get(0);
        assertEquals("output", modelTensor.getName());
        Number[] data = modelTensor.getData();
        assertEquals(1, data.length);
    }

    @Test
    public void initModel_predict_TorchScript_CrossEncoder() throws URISyntaxException {
        textSimilarityCrossEncoderModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textSimilarityCrossEncoderModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(1, mlModelTensors.get(0).getData().length);
        }
        textSimilarityCrossEncoderModel.close();
    }

}
