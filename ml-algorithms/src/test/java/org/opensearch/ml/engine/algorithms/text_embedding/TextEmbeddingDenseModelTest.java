/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.ML_ENGINE;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.MODEL_HELPER;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.MODEL_ZIP_FILE;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.SENTENCE_EMBEDDING;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.dataset.AsymmetricTextEmbeddingParameters.EmbeddingContentType;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.FileUtils;

public class TextEmbeddingDenseModelTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private File modelZipFile;
    private String modelId;
    private String modelName;
    private FunctionName functionName;
    private String version;
    private TextEmbeddingModelConfig modelConfig;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private TextEmbeddingDenseModel textEmbeddingDenseModel;
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
        functionName = FunctionName.TEXT_EMBEDDING;
        version = "1";
        modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .embeddingDimension(dimension)
            .frameworkType(SENTENCE_TRANSFORMERS)
            .build();
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_model_name")
            .modelId("test_model_id")
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .version("1.0.0")
            .modelConfig(modelConfig)
            .modelState(MLModelState.TRAINED)
            .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("all-MiniLM-L6-v2_torchscript_sentence-transformer.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        textEmbeddingDenseModel = new TextEmbeddingDenseModel();

        inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny", "That is a happy dog")).build();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_SmallModel() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("traced_small_model.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        TextEmbeddingModelConfig modelConfig = this.modelConfig.toBuilder().embeddingDimension(768).build();
        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).build();
        textEmbeddingDenseModel.initModel(smallModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingDenseModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(modelConfig.getEmbeddingDimension().intValue(), mlModelTensors.get(position).getData().length);
        }
        textEmbeddingDenseModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer() {
        textEmbeddingDenseModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingDenseModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(4, mlModelTensors.size());
            assertEquals(dimension, mlModelTensors.get(position).getData().length);
        }
        textEmbeddingDenseModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_ResultFilter() {
        textEmbeddingDenseModel.initModel(model, params, encryptor);
        ModelResultFilter resultFilter = ModelResultFilter
            .builder()
            .returnNumber(true)
            .targetResponse(Arrays.asList(SENTENCE_EMBEDDING))
            .build();
        TextDocsInputDataSet textDocsInputDataSet = inputDataSet.toBuilder().resultFilter(resultFilter).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingDenseModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(dimension, mlModelTensors.get(position).getData().length);
        }
        textEmbeddingDenseModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_Huggingface() throws URISyntaxException {
        String modelFile = "all-MiniLM-L6-v2_torchscript_huggingface.zip";
        String modelType = "bert";
        TextEmbeddingModelConfig.PoolingMode poolingMode = TextEmbeddingModelConfig.PoolingMode.MEAN;
        boolean normalize = true;
        int modelMaxLength = 512;
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        initModel_predict_HuggingfaceModel(modelFile, modelType, poolingMode, normalize, modelMaxLength, modelFormat, dimension);
    }

    @Test
    public void initModel_predict_ONNX_bert() throws URISyntaxException {
        String modelFile = "all-MiniLM-L6-v2_onnx.zip";
        String modelType = "bert";
        TextEmbeddingModelConfig.PoolingMode poolingMode = TextEmbeddingModelConfig.PoolingMode.MEAN;
        boolean normalize = true;
        int modelMaxLength = 512;
        MLModelFormat modelFormat = MLModelFormat.ONNX;
        initModel_predict_HuggingfaceModel(modelFile, modelType, poolingMode, normalize, modelMaxLength, modelFormat, dimension);
    }

    @Test
    public void initModel_predict_ONNX_albert() throws URISyntaxException {
        String modelFile = "paraphrase-albert-small-v2_onnx.zip";
        String modelType = "albert";
        TextEmbeddingModelConfig.PoolingMode poolingMode = TextEmbeddingModelConfig.PoolingMode.MEAN;
        boolean normalize = false;
        int modelMaxLength = 512;
        MLModelFormat modelFormat = MLModelFormat.ONNX;
        initModel_predict_HuggingfaceModel(modelFile, modelType, poolingMode, normalize, modelMaxLength, modelFormat, 768);
    }

    private void initModel_predict_HuggingfaceModel(
        String modelFile,
        String modelType,
        TextEmbeddingModelConfig.PoolingMode poolingMode,
        boolean normalizeResult,
        Integer modelMaxLength,
        MLModelFormat modelFormat,
        int dimension
    ) throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource(modelFile).toURI()));
        params.put(ML_ENGINE, mlEngine);
        TextEmbeddingModelConfig onnxModelConfig = modelConfig
            .toBuilder()
            .frameworkType(HUGGINGFACE_TRANSFORMERS)
            .modelType(modelType)
            .poolingMode(poolingMode)
            .normalizeResult(normalizeResult)
            .modelMaxLength(modelMaxLength)
            .build();
        MLModel mlModel = model.toBuilder().modelFormat(modelFormat).modelConfig(onnxModelConfig).build();
        textEmbeddingDenseModel.initModel(mlModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) textEmbeddingDenseModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(dimension, mlModelTensors.get(position).getData().length);
        }
        textEmbeddingDenseModel.close();

    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_SmallModel_With_Asymmetric_Prompts_HappyPath() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("traced_small_model.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        TextEmbeddingModelConfig asymmetricModelConfig = this.modelConfig
            .toBuilder()
            .embeddingDimension(768)
            .queryPrefix("query >> ")
            .passagePrefix("passage >> ")
            .build();
        MLModel asymmetricSmallModel = model.toBuilder().modelConfig(asymmetricModelConfig).build();
        textEmbeddingDenseModel.initModel(asymmetricSmallModel, params, encryptor);
        MLInput asymmetricMlInputQueries = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("what is the meaning of life?", "who won this year's us open")).build()
            )
            .parameters(new AsymmetricTextEmbeddingParameters(EmbeddingContentType.QUERY))
            .build();
        MLInput asymmetricMlInputPassages = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("The meaning of life is 42", "I won this year's us open")).build()
            )
            .parameters(new AsymmetricTextEmbeddingParameters(EmbeddingContentType.PASSAGE))
            .build();

        ModelTensorOutput asymmetricQueryEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(asymmetricMlInputQueries);
        ModelTensorOutput asymmetricPassageEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(asymmetricMlInputPassages);

        TextEmbeddingModelConfig symmetricModelConfig = this.modelConfig.toBuilder().embeddingDimension(768).build();
        MLModel smallModel = model.toBuilder().modelConfig(symmetricModelConfig).build();
        textEmbeddingDenseModel.initModel(smallModel, params, encryptor);
        MLInput symmetricMlInputQueries = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("what is the meaning of life?", "who won this year's us open")).build()
            )
            .build();
        MLInput symmetricMlInputPassages = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("The meaning of life is 42", "I won this year's us open")).build()
            )
            .build();

        ModelTensorOutput symmetricQueryEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(symmetricMlInputQueries);
        ModelTensorOutput symmetricPassageEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(symmetricMlInputPassages);

        assertTrue(
            "asymmetric and symmetric query embeddings should be different",
            areTensorsDifferent(
                asymmetricQueryEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                symmetricQueryEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                0.1f
            )
        );
        assertTrue(
            "asymmetric and symmetric passage embeddings should be different",
            areTensorsDifferent(
                asymmetricPassageEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                symmetricPassageEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                0.1f
            )
        );

        textEmbeddingDenseModel.close();
    }

    private boolean areTensorsDifferent(ModelTensor tensor1, ModelTensor tensor2, float delta) {

        if (!Arrays.equals(tensor1.getShape(), tensor2.getShape())) {
            return true; // Tensors are different if they have different lengths
        }

        List<Number> vectorA = Arrays.asList(tensor1.getData());
        List<Number> vectorB = Arrays.asList(tensor2.getData());

        for (int i = 0; i < vectorA.size(); i++) {
            if (Math.abs(vectorA.get(i).floatValue() - vectorB.get(i).floatValue()) > delta) {
                return true; // Vectors are different if any pair of corresponding elements differ by more than the tolerance
            }
        }
        return false; // Vectors are the same

    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_SmallModel_With_Asymmetric_Prompts_HappyPath2()
        throws URISyntaxException {
        // only the query embeddings need a prefix
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("traced_small_model.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        TextEmbeddingModelConfig asymmetricModelConfig = this.modelConfig
            .toBuilder()
            .embeddingDimension(768)
            .queryPrefix("query >> ")
            .build();
        MLModel asymmetricSmallModel = model.toBuilder().modelConfig(asymmetricModelConfig).build();
        textEmbeddingDenseModel.initModel(asymmetricSmallModel, params, encryptor);
        MLInput asymmetricMlInputQueries = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("what is the meaning of life?", "who won this year's us open")).build()
            )
            .parameters(new AsymmetricTextEmbeddingParameters(EmbeddingContentType.QUERY))
            .build();
        MLInput asymmetricMlInputPassages = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("The meaning of life is 42", "I won this year's us open")).build()
            )
            .parameters(new AsymmetricTextEmbeddingParameters(EmbeddingContentType.PASSAGE))
            .build();

        ModelTensorOutput asymmetricQueryEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(asymmetricMlInputQueries);
        ModelTensorOutput asymmetricPassageEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(asymmetricMlInputPassages);

        TextEmbeddingModelConfig symmetricModelConfig = this.modelConfig.toBuilder().embeddingDimension(768).build();
        MLModel smallModel = model.toBuilder().modelConfig(symmetricModelConfig).build();
        textEmbeddingDenseModel.initModel(smallModel, params, encryptor);
        MLInput symmetricMlInputQueries = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("what is the meaning of life?", "who won this year's us open")).build()
            )
            .build();
        MLInput symmetricMlInputPassages = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("The meaning of life is 42", "I won this year's us open")).build()
            )
            .build();

        ModelTensorOutput symmetricQueryEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(symmetricMlInputQueries);
        ModelTensorOutput symmetricPassageEmbeddings = (ModelTensorOutput) textEmbeddingDenseModel.predict(symmetricMlInputPassages);

        assertTrue(
            "asymmetric and symmetric query embeddings should be different",
            areTensorsDifferent(
                asymmetricQueryEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                symmetricQueryEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                0.1f
            )
        );
        assertTrue(
            "asymmetric and symmetric passage embeddings should be equal",
            !areTensorsDifferent(
                asymmetricPassageEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                symmetricPassageEmbeddings.getMlModelOutputs().get(0).getMlModelTensors().get(0),
                0.1f
            )
        );

        textEmbeddingDenseModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_SmallModel_With_Asymmetric_Prompts_SadPath1() throws URISyntaxException {
        // asymmetric model, no parameter passed
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("traced_small_model.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        TextEmbeddingModelConfig asymmetricModelConfig = this.modelConfig
            .toBuilder()
            .embeddingDimension(768)
            .queryPrefix("query >> ")
            .passagePrefix("passage >>")
            .build();
        MLModel asymmetricSmallModel = model.toBuilder().modelConfig(asymmetricModelConfig).build();
        textEmbeddingDenseModel.initModel(asymmetricSmallModel, params, encryptor);

        MLInput symmetricMlInputQueries = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("what is the meaning of life?", "who won this year's us open")).build()
            )
            .build();

        try {
            textEmbeddingDenseModel.predict(symmetricMlInputQueries);
        } catch (MLException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals(
                "The embedding model chosen is asymmetric. To use it, you must declare whether the input is of type `QUERY` or of type `PASSAGE`.",
                e.getCause().getMessage()
            );
            return;
        } finally {
            textEmbeddingDenseModel.close();
        }

        fail("Expected exception not thrown");

    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_SmallModel_With_Asymmetric_Prompts_SadPath2() throws URISyntaxException {
        // symmetric model, asymmetric parameter passed
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("traced_small_model.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        TextEmbeddingModelConfig symmetricModelConfig = this.modelConfig.toBuilder().embeddingDimension(768).build();
        MLModel symmetricSmallModel = model.toBuilder().modelConfig(symmetricModelConfig).build();
        textEmbeddingDenseModel.initModel(symmetricSmallModel, params, encryptor);

        MLInput asymmetricMlInputQueries = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(
                TextDocsInputDataSet.builder().docs(Arrays.asList("what is the meaning of life?", "who won this year's us open")).build()
            )
            .parameters(new AsymmetricTextEmbeddingParameters(EmbeddingContentType.QUERY))
            .build();

        try {
            textEmbeddingDenseModel.predict(asymmetricMlInputQueries);
        } catch (MLException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals(
                "When passing AsymmetricTextEmbeddingParameters, the model requires to be registered with at least one of `query_prefix` or `passage_prefix`.",
                e.getCause().getMessage()
            );
            return;
        } finally {
            textEmbeddingDenseModel.close();
        }

        fail("Expected exception not thrown");

    }

    @Test
    public void initModel_NullModelZipFile() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingDenseModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("all-MiniLM-L6-v2_onnx.zip").toURI()));
        textEmbeddingDenseModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML engine is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("all-MiniLM-L6-v2_onnx.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingDenseModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");
        model.setModelId(null);
        textEmbeddingDenseModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(MODEL_HELPER, modelHelper);
            params.put(MODEL_ZIP_FILE, new File(getClass().getResource("wrong_zip_with_2_pt_file.zip").toURI()));
            params.put(ML_ENGINE, mlEngine);
            textEmbeddingDenseModel.initModel(model, params, encryptor);
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
        textEmbeddingDenseModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void predict_NullModelHelper() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        textEmbeddingDenseModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        model.setModelId(null);
        try {
            textEmbeddingDenseModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals("model id is null", e.getMessage());
        }
        textEmbeddingDenseModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_AfterModelClosed() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to inference TEXT_EMBEDDING");
        textEmbeddingDenseModel.initModel(model, params, encryptor);
        textEmbeddingDenseModel.close();
        textEmbeddingDenseModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        textEmbeddingDenseModel.parseModelTensorOutput(null, null);
    }

    @Test
    public void predict_BeforeInitingModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        textEmbeddingDenseModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), model);
    }

    @Test
    public void test_async_inference() {
        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage("Method is not implemented");
        textEmbeddingDenseModel
            .asyncPredict(
                MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(),
                mock(ActionListener.class)
            );
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }

    private int findSentenceEmbeddingPosition(ModelTensors modelTensors) {
        List<ModelTensor> mlModelTensors = modelTensors.getMlModelTensors();
        for (int i = 0; i < mlModelTensors.size(); i++) {
            ModelTensor mlModelTensor = mlModelTensors.get(i);
            if (SENTENCE_EMBEDDING.equals(mlModelTensor.getName())) {
                return i;
            }
        }
        throw new ResourceNotFoundException("no sentence embedding found");
    }
}
