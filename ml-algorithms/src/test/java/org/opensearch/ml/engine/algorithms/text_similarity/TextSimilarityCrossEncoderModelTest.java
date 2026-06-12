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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.DLModel.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.exception.MLException;
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
import org.opensearch.ml.engine.utils.FileUtils;

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

@Log4j2
public class TextSimilarityCrossEncoderModelTest {

    private File modelZipFile;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private TextSimilarityCrossEncoderModel textSimilarityCrossEncoderModel;
    private Path mlCachePath;
    private TextSimilarityInputDataSet inputDataSet;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_model_name")
            .modelId("test_model_id")
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("TinyBERT-CE-torch_script.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        textSimilarityCrossEncoderModel = new TextSimilarityCrossEncoderModel();

        inputDataSet = TextSimilarityInputDataSet
            .builder()
            .textDocs(Arrays.asList("That is a happy dog", "it's summer"))
            .queryText("it's summer")
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
        when(indiceNdArray.toLongArray()).thenReturn(new long[] { 102l, 101l });
        when(manager.create((long[]) any())).thenReturn(indiceNdArray);
        doNothing().when(indiceNdArray).setName(any());
        NDList outputList = textSimilarityTranslator.processInput(translatorContext, input);
        assertEquals(3, outputList.size());
        Iterator<NDArray> iterator = outputList.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            long[] output = ndArray.toLongArray();
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
        when(ndArray.toArray()).thenReturn(new Number[] { 1.245f });
        when(ndArray.getName()).thenReturn("output");
        when(ndArray.getShape()).thenReturn(shape);
        when(shape.getShape()).thenReturn(new long[] { 1 });
        when(ndArray.getDataType()).thenReturn(DataType.FLOAT32);
        List<NDArray> ndArrayList = Collections.singletonList(ndArray);
        NDList ndList = new NDList(ndArrayList);
        Output output = textSimilarityTranslator.processOutput(translatorContext, ndList);
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        assertEquals(1, modelTensorsList.size());
        ModelTensor modelTensor = modelTensorsList.get(0);
        assertEquals("similarity", modelTensor.getName());
        Number[] data = modelTensor.getData();
        assertEquals(1, data.length);
    }

    @Test
    public void test_TextSimilarity_Translator_BatchProcessInput() throws URISyntaxException, IOException {
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
        when(indiceNdArray.toLongArray()).thenReturn(new long[] { 102l, 101l });
        when(manager.create((long[][]) any())).thenReturn(indiceNdArray);
        doNothing().when(indiceNdArray).setName(any());
        List<Input> inputList = new ArrayList<>(1);
        inputList.add(input);
        NDList outputList = textSimilarityTranslator.batchProcessInput(translatorContext, inputList);
        assertEquals(3, outputList.size());
        Iterator<NDArray> iterator = outputList.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            long[] output = ndArray.toLongArray();
            assertEquals(2, output.length);
        }
    }

    @Test
    public void test_TextSimilarity_Translator_BatchProcessOutput() throws URISyntaxException, IOException {
        TextSimilarityTranslator textSimilarityTranslator = new TextSimilarityTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        textSimilarityTranslator.prepare(translatorContext);

        NDArray batchArray = mock(NDArray.class);
        Shape batchShape = mock(Shape.class);
        when(batchArray.getShape()).thenReturn(batchShape);
        when(batchShape.get(0)).thenReturn(2L);

        NDArray itemArray1 = mock(NDArray.class);
        NDArray itemArray2 = mock(NDArray.class);
        Shape itemShape = mock(Shape.class);
        when(itemShape.getShape()).thenReturn(new long[] { 1 });
        when(itemArray1.getShape()).thenReturn(itemShape);
        when(itemArray2.getShape()).thenReturn(itemShape);
        when(itemArray1.toArray()).thenReturn(new Number[] { 1.0f });
        when(itemArray2.toArray()).thenReturn(new Number[] { 2.0f });
        when(itemArray1.getDataType()).thenReturn(DataType.FLOAT32);
        when(itemArray2.getDataType()).thenReturn(DataType.FLOAT32);
        when(itemArray1.toByteBuffer()).thenReturn(ByteBuffer.allocate(4));
        when(itemArray2.toByteBuffer()).thenReturn(ByteBuffer.allocate(4));
        when(batchArray.get(0)).thenReturn(itemArray1);
        when(batchArray.get(1)).thenReturn(itemArray2);

        NDList ndList = new NDList(batchArray);
        List<Output> outputs = textSimilarityTranslator.batchProcessOutput(translatorContext, ndList);
        assertEquals(2, outputs.size());
        for (Output output : outputs) {
            byte[] bytes = output.getData().getAsBytes();
            ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
            List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
            assertEquals(1, modelTensorsList.size());
            ModelTensor modelTensor = modelTensorsList.get(0);
            assertEquals("similarity", modelTensor.getName());
            assertEquals(1, modelTensor.getData().length);
        }
    }

    @Test
    public void initModel_predict_TorchScript_CrossEncoder() throws URISyntaxException {
        textSimilarityCrossEncoderModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textSimilarityCrossEncoderModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(1, mlModelTensors.get(0).getData().length);
        }
        textSimilarityCrossEncoderModel.close();
    }

    @Test
    public void initModel_predict_ONNX_CrossEncoder() throws URISyntaxException {
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.ONNX)
            .name("test_model_name")
            .modelId("test_model_id")
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();
        modelZipFile = new File(getClass().getResource("TinyBERT-CE-onnx.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);

        textSimilarityCrossEncoderModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textSimilarityCrossEncoderModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(1, mlModelTensors.get(0).getData().length);
        }
        textSimilarityCrossEncoderModel.close();
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("TinyBERT-CE-torch_script.zip").toURI()));
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model helper is null"));
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("TinyBERT-CE-torch_script.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("ML engine is null"));
    }

    @Test
    public void initModel_NullModelId() {
        model.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        MLException e = assertThrows(MLException.class, () -> textSimilarityCrossEncoderModel.initModel(model, params, encryptor));
        Throwable rootCause = e.getCause();
        assert (rootCause instanceof IllegalArgumentException);
        assert (rootCause.getMessage().equals("found multiple models"));
    }

    @Test
    public void initModel_WrongFunctionName() {
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("wrong function name"));
    }

    @Test
    public void predict_NullModelHelper() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel
                .predict(MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(inputDataSet).build())
        );
        assert (e.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_NullModelId() {
        model.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> textSimilarityCrossEncoderModel
                .predict(MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(inputDataSet).build())
        );
        assert (e2.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_AfterModelClosed() {
        textSimilarityCrossEncoderModel.initModel(model, params, encryptor);
        textSimilarityCrossEncoderModel.close();
        MLException e = assertThrows(
            MLException.class,
            () -> textSimilarityCrossEncoderModel
                .predict(MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(inputDataSet).build())
        );
        log.info(e.getMessage());
        assert (e.getMessage().startsWith("Failed to inference TEXT_SIMILARITY"));
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }

}
