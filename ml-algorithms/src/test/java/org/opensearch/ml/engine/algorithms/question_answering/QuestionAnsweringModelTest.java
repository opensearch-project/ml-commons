/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.question_answering;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
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
import ai.djl.translate.TranslatorContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class QuestionAnsweringModelTest {

    private File modelZipFile;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private QuestionAnsweringModel questionAnsweringModel;
    private Path mlCachePath;
    private QuestionAnsweringInputDataSet inputDataSet;
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
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("question_answering_pt.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        questionAnsweringModel = new QuestionAnsweringModel();

        inputDataSet = QuestionAnsweringInputDataSet.builder().question("What color is apple").context("Apples are red").build();
    }

    @Test
    public void test_QuestionAnswering_ProcessInput_ProcessOutput() throws URISyntaxException, IOException {
        QuestionAnsweringTranslator questionAnsweringTranslator = new QuestionAnsweringTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        questionAnsweringTranslator.prepare(translatorContext);

        NDManager manager = mock(NDManager.class);
        when(translatorContext.getNDManager()).thenReturn(manager);
        Input input = mock(Input.class);
        String question = "What color is apple";
        String context = "Apples are red";
        when(input.getAsString(0)).thenReturn(question);
        when(input.getAsString(1)).thenReturn(context);
        NDArray indiceNdArray = mock(NDArray.class);
        when(indiceNdArray.toLongArray()).thenReturn(new long[] { 102l, 101l });
        when(manager.create((long[]) any())).thenReturn(indiceNdArray);
        doNothing().when(indiceNdArray).setName(any());
        NDList outputList = questionAnsweringTranslator.processInput(translatorContext, input);
        assertEquals(2, outputList.size());
        Iterator<NDArray> iterator = outputList.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            long[] output = ndArray.toLongArray();
            assertEquals(2, output.length);
        }

        NDArray startLogits = mock(NDArray.class);
        NDArray endLogits = mock(NDArray.class);
        when(startLogits.argMax()).thenReturn(startLogits);
        when(startLogits.getLong()).thenReturn(3L);
        when(endLogits.argMax()).thenReturn(endLogits);
        when(endLogits.getLong()).thenReturn(7L);

        List<NDArray> ndArrayList = new ArrayList<>();
        ndArrayList.add(startLogits);
        ndArrayList.add(endLogits);
        NDList ndList = new NDList(ndArrayList);

        Output output = questionAnsweringTranslator.processOutput(translatorContext, ndList);
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        assertEquals(1, modelTensorsList.size());
    }

    @Test
    public void initModel_predict_TorchScript_QuestionAnswering() throws URISyntaxException {
        questionAnsweringModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) questionAnsweringModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(1, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        questionAnsweringModel.close();
    }

    // ONNX is working fine but the model is too big to upload to git. Trying to find small models @Test
    @Test
    public void initModel_predict_ONNX_QuestionAnswering() throws URISyntaxException {
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.ONNX)
            .name("test_model_name")
            .modelId("test_model_id")
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();
        modelZipFile = new File(getClass().getResource("question_answering_onnx.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);

        questionAnsweringModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) questionAnsweringModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(1, mlModelOutputs.size());
        for (int i = 1; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        questionAnsweringModel.close();
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("question_answering_pt.zip").toURI()));
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model helper is null"));
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("question_answering_pt.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("ML engine is null"));
    }

    @Test
    public void initModel_NullModelId() {
        model.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        MLException e = assertThrows(MLException.class, () -> questionAnsweringModel.initModel(model, params, encryptor));
        Throwable rootCause = e.getCause();
        assert (rootCause instanceof IllegalArgumentException);
        assert (rootCause.getMessage().equals("found multiple models"));
    }

    @Test
    public void initModel_WrongFunctionName() {
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("wrong function name"));
    }

    @Test
    public void predict_NullModelHelper() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel
                .predict(MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build())
        );
        assert (e.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_NullModelId() {
        model.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel
                .predict(MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build())
        );
        assert (e2.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_AfterModelClosed() {
        questionAnsweringModel.initModel(model, params, encryptor);
        questionAnsweringModel.close();
        MLException e = assertThrows(
            MLException.class,
            () -> questionAnsweringModel
                .predict(MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build())
        );
        log.info(e.getMessage());
        assert (e.getMessage().startsWith("Failed to inference QUESTION_ANSWERING"));
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }

}
