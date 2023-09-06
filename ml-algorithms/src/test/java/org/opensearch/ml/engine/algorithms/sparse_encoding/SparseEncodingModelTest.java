package org.opensearch.ml.engine.algorithms.sparse_encoding;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.FileUtils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.engine.algorithms.DLModel.*;
import static org.opensearch.ml.engine.algorithms.DLModel.ML_ENGINE;

public class SparseEncodingModelTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private File modelZipFile;
    private String modelId;
    private String modelName;
    private FunctionName functionName;
    private String version;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private SparseEncodingModel sparseEncodingModel;
    private Path mlCachePath;
    private Path mlConfigPath;
    private TextDocsInputDataSet inputDataSet;
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
        functionName = FunctionName.SPARSE_ENCODING;
        version = "1";
        model = MLModel.builder()
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .name("test_model_name")
                .modelId("test_model_id")
                .algorithm(FunctionName.SPARSE_ENCODING)
                .version("1.0.0")
                .modelState(MLModelState.TRAINED)
                .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("sparse_demo.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        sparseEncodingModel = new SparseEncodingModel();

        inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny", "That is a happy dog")).build();
    }

    @Test
    public void initModel_predict_TorchScript_SparseEncoding_SmallModel() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("sparse_demo.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        MLModel smallModel = model.toBuilder().build();
        sparseEncodingModel.initModel(smallModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)sparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        sparseEncodingModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SparseEncoding() {
        sparseEncodingModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)sparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        sparseEncodingModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SparseEncoding_ResultFilter() {
        sparseEncodingModel.initModel(model, params, encryptor);
        ModelResultFilter resultFilter = ModelResultFilter.builder().returnNumber(true).targetResponse(Arrays.asList("output")).build();
        TextDocsInputDataSet textDocsInputDataSet = inputDataSet.toBuilder().resultFilter(resultFilter).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(textDocsInputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)sparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        sparseEncodingModel.close();
    }

    @Test
    public void initModel_NullModelZipFile() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        sparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("sparse_demo.zip").toURI()));
        sparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML engine is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("sparse_demo.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        sparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");
        model.setModelId(null);
        sparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(MODEL_HELPER, modelHelper);
            params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
            params.put(ML_ENGINE, mlEngine);
            sparseEncodingModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals(MLException.class, e.getClass());
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            assertEquals(IllegalArgumentException.class, rootCause.getClass());
            assertEquals("found multiple models", rootCause.getMessage());
        }
    }

    @Test
    public void initModel_WrongFunctionName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong function name");
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        sparseEncodingModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void predict_NullModelHelper() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        sparseEncodingModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        model.setModelId(null);
        try {
            sparseEncodingModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals("model id is null", e.getMessage());
        }
        sparseEncodingModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_AfterModelClosed() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to inference SPARSE_ENCODING");
        sparseEncodingModel.initModel(model, params, encryptor);
        sparseEncodingModel.close();
        sparseEncodingModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build());
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        sparseEncodingModel.parseModelTensorOutput(null, null);
    }

    @Test
    public void predict_BeforeInitingModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        sparseEncodingModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build(), model);
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }
}
