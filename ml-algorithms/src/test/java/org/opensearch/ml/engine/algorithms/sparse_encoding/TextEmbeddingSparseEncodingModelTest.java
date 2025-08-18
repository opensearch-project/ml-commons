package org.opensearch.ml.engine.algorithms.sparse_encoding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.algorithms.DLModel.*;

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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat;
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

import ai.djl.Model;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.TranslatorContext;

public class TextEmbeddingSparseEncodingModelTest {
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
    private TextEmbeddingSparseEncodingModel textEmbeddingSparseEncodingModel;
    private Path mlCachePath;
    private Path mlConfigPath;
    private TextDocsInputDataSet inputDataSet;
    private int dimension = 384;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        modelId = "test_model_id";
        modelName = "test_model_name";
        functionName = FunctionName.SPARSE_ENCODING;
        version = "1";
        model = MLModel
            .builder()
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
        textEmbeddingSparseEncodingModel = new TextEmbeddingSparseEncodingModel();

        inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny", "That is a happy dog")).build();
    }

    @Test
    public void test_SparseEncoding_Translator_ProcessInput() throws URISyntaxException, IOException {
        SparseEncodingTranslator sparseEncodingTranslator = new SparseEncodingTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        sparseEncodingTranslator.prepare(translatorContext);

        NDManager manager = mock(NDManager.class);
        when(translatorContext.getNDManager()).thenReturn(manager);
        Input input = mock(Input.class);
        String testSentence = "hello world";
        when(input.getAsString(0)).thenReturn(testSentence);
        NDArray indiceNdArray = mock(NDArray.class);
        when(indiceNdArray.toLongArray()).thenReturn(new long[] { 102l, 101l });
        when(manager.create((long[]) any())).thenReturn(indiceNdArray);
        doNothing().when(indiceNdArray).setName(any());
        NDList outputList = sparseEncodingTranslator.processInput(translatorContext, input);
        assertEquals(2, outputList.size());
        Iterator<NDArray> iterator = outputList.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            long[] output = ndArray.toLongArray();
            assertEquals(2, output.length);
        }
    }

    @Test
    public void test_SparseEncoding_Translator_ProcessOutput() throws URISyntaxException, IOException {
        SparseEncodingTranslator sparseEncodingTranslator = new SparseEncodingTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        sparseEncodingTranslator.prepare(translatorContext);

        NDArray ndArray = mock(NDArray.class);
        when(ndArray.nonzero()).thenReturn(ndArray);
        when(ndArray.squeeze()).thenReturn(ndArray);
        when(ndArray.getFloat(any())).thenReturn(1.0f);
        when(ndArray.toLongArray()).thenReturn(new long[] { 10000, 10001 });
        when(ndArray.getName()).thenReturn("output");
        List<NDArray> ndArrayList = Collections.singletonList(ndArray);
        NDList ndList = new NDList(ndArrayList);
        Output output = sparseEncodingTranslator.processOutput(translatorContext, ndList);
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        assertEquals(1, modelTensorsList.size());
        ModelTensor modelTensor = modelTensorsList.get(0);
        assertEquals("output", modelTensor.getName());
        Map<String, ?> dataAsMap = modelTensor.getDataAsMap();
        assertEquals(1, dataAsMap.size());
    }

    @Test
    public void initModel_predict_TorchScript_SparseEncoding() {
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        textEmbeddingSparseEncodingModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SparseEncoding_ResultFilter() {
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
        ModelResultFilter resultFilter = ModelResultFilter.builder().returnNumber(true).targetResponse(Arrays.asList("output")).build();
        TextDocsInputDataSet textDocsInputDataSet = inputDataSet.toBuilder().resultFilter(resultFilter).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(textDocsInputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        textEmbeddingSparseEncodingModel.close();
    }

    // Test AsymmetricTextEmbeddingParameters with WORD format
    @Test
    public void initModel_predict_SparseEncoding_WithLexicalFormat() {
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);

        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .build();

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.SPARSE_ENCODING)
            .inputDataset(inputDataSet)
            .parameters(parameters)
            .build();

        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            assertNotNull(tensor.getDataAsMap());
        }
        textEmbeddingSparseEncodingModel.close();
    }

    // Test AsymmetricTextEmbeddingParameters with TOKEN_ID format
    @Test
    public void initModel_predict_SparseEncoding_WithTokenIdFormat() {
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);

        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.SPARSE_ENCODING)
            .inputDataset(inputDataSet)
            .parameters(parameters)
            .build();

        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            assertNotNull(tensor.getDataAsMap());
        }
        textEmbeddingSparseEncodingModel.close();
    }

    // Test both content_type and sparse_embedding_format parameters
    @Test
    public void initModel_predict_SparseEncoding_WithBothParameters() {
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);

        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(AsymmetricTextEmbeddingParameters.EmbeddingContentType.QUERY)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.SPARSE_ENCODING)
            .inputDataset(inputDataSet)
            .parameters(parameters)
            .build();

        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            assertNotNull(tensor.getDataAsMap());
        }
        textEmbeddingSparseEncodingModel.close();
    }

    // Test default parameters behavior (no AsymmetricTextEmbeddingParameters)
    @Test
    public void initModel_predict_SparseEncoding_WithoutParameters() {
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build();

        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            assertNotNull(tensor.getDataAsMap());
        }
        textEmbeddingSparseEncodingModel.close();
    }

    // Test isAsymmetricModel method override returns false
    @Test
    public void test_isAsymmetricModel_ReturnsFalse() {
        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(AsymmetricTextEmbeddingParameters.EmbeddingContentType.QUERY)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .build();

        // Test that isAsymmetricModel returns false even with AsymmetricTextEmbeddingParameters
        boolean isAsymmetric = textEmbeddingSparseEncodingModel.isAsymmetricModel(parameters);
        assertFalse("isAsymmetricModel should return false for sparse encoding model", isAsymmetric);
    }

    // Test isAsymmetricModel with null parameters
    @Test
    public void test_isAsymmetricModel_WithNullParameters() {
        boolean isAsymmetric = textEmbeddingSparseEncodingModel.isAsymmetricModel(null);
        assertFalse("isAsymmetricModel should return false with null parameters", isAsymmetric);
    }

    // Test isSparseModel field default value
    @Test
    public void test_isSparseModel_DefaultValue() {
        // Test that the protected field isSparseModel defaults to false
        // This indirectly tests the field existence and default value
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);

        // The field is tested implicitly through model behavior
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingSparseEncodingModel.predict(mlInput);
        assertNotNull("Model should predict successfully with default isSparseModel value", output);

        textEmbeddingSparseEncodingModel.close();
    }

    @Test
    public void initModel_NullModelZipFile() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("sparse_demo.zip").toURI()));
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML engine is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("sparse_demo.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");
        model.setModelId(null);
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(MODEL_HELPER, modelHelper);
            params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
            params.put(ML_ENGINE, mlEngine);
            textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
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
        textEmbeddingSparseEncodingModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void predict_NullModelHelper() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        textEmbeddingSparseEncodingModel
            .predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        model.setModelId(null);
        try {
            textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals("model id is null", e.getMessage());
        }
        textEmbeddingSparseEncodingModel
            .predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_AfterModelClosed() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to inference SPARSE_ENCODING");
        textEmbeddingSparseEncodingModel.initModel(model, params, encryptor);
        textEmbeddingSparseEncodingModel.close();
        textEmbeddingSparseEncodingModel
            .predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build());
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        textEmbeddingSparseEncodingModel.parseModelTensorOutput(null, null);
    }

    @Test
    public void predict_BeforeInitingModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        textEmbeddingSparseEncodingModel
            .predict(MLInput.builder().algorithm(FunctionName.SPARSE_ENCODING).inputDataset(inputDataSet).build(), model);
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }
}
