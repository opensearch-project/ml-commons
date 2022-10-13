package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLResultDataType;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.annotation.Function;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.algorithms.text_embedding.MLTextEmbeddingServingTranslator.toFloats;

@Log4j2
@Function(FunctionName.TEXT_EMBEDDING)
public class TextEmbeddingModel implements Predictable {

    public static final String SENTENCE_EMBEDDING = "sentence_embedding";
    private ModelHelper modelHelper;
    private String modelId;
    public static final String MODEL_ZIP_FILE = "model_zip_file";
    public static final String MODEL_HELPER = "model_helper";

    @Override
    public MLOutput predict(MLInputDataset inputDataset, MLModel model) {
        throw new MLException("load model first");
    }

    @Override
    public MLOutput predict(MLInputDataset inputDataset) {
        if (modelHelper == null || modelId == null) {
            throw new MLException("model not loaded");
        }
        return predictTextEmbedding(modelId, inputDataset);
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params) {
        String engine = model.getModelFormat() == MLModelFormat.TORCH_SCRIPT ? "PyTorch" : "OnnxRuntime";
        File modelZipFile = (File)params.get(MODEL_ZIP_FILE);
        modelHelper = (ModelHelper)params.get(MODEL_HELPER);
        modelId = model.getModelId();
        modelHelper.loadModel(
                modelZipFile,
                modelId,
                model.getName(),
                model.getAlgorithm(),
                model.getVersion(),
                model.getModelConfig(),
                engine
        );
    }

    @Override
    public void close() {
        if (modelHelper != null && modelId != null) {
            modelHelper.unloadModel(modelId);
        }
    }

    public ModelTensorOutput predictTextEmbedding(String modelId, MLInputDataset inputDataSet) {
        log.debug("start to predict {}", modelId);
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<ModelTensorOutput>) () -> {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                Predictor<Input, Output> predictor = modelHelper.getPredictor(modelId);
                if (predictor == null) {
                    throw new MLResourceNotFoundException("Model not loaded.");
                }
                List<ModelTensors> tensorOutputs = new ArrayList<>();
                Output output;
                TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;
                ModelResultFilter resultFilter = textDocsInput.getResultFilter();
                if (modelHelper.getFrameworkType(modelId) == SENTENCE_TRANSFORMERS) {
                    for (String doc : textDocsInput.getDocs()) {
                        Input input = new Input();
                        input.add(doc);
                        output = predictor.predict(input);
                        tensorOutputs.add(parseModelTensorOutput(output, resultFilter));
                    }
                } else {
                    for (String doc : textDocsInput.getDocs()) {
                        Input input = new Input();
                        input.add(doc);
                        output = predictor.predict(input);
                        byte[] bytes = output.getData().getAsBytes();
                        Number[] data = toFloats(bytes);
                        List<ModelTensor> modelTensors = new ArrayList<>();
                        ByteBuffer byteBuffer = null;
                        if (resultFilter.isReturnBytes()) {
                            byteBuffer = ByteBuffer.wrap(bytes);
                            byteBuffer.order(ByteOrder.nativeOrder());
                        }
                        ModelTensor modelTensor = new ModelTensor(SENTENCE_EMBEDDING, data, new long[]{1, data.length}, MLResultDataType.FLOAT32, byteBuffer);
                        modelTensors.add(modelTensor);
                        ModelTensors mlModelTensorOutput = new ModelTensors(modelTensors);
                        mlModelTensorOutput.filter(resultFilter);
                        tensorOutputs.add(mlModelTensorOutput);
                    }
                }
                return new ModelTensorOutput(tensorOutputs);
            });
        } catch (PrivilegedActionException e) {
            String errorMsg = "Failed to inference text embedding";
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    private ModelTensors parseModelTensorOutput(Output output, ModelResultFilter resultFilter) {
        if (output == null) {
            throw new MLException("No output generated");
        }
        byte[] bytes = output.getData().getAsBytes();
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try (StreamInput streamInput = BytesReference.fromByteBuffer(byteBuffer).streamInput()) {
            ModelTensors tensorOutput = new ModelTensors(streamInput);
            tensorOutput.filter(resultFilter);
            return tensorOutput;
        } catch (Exception e) {
            String errorMsg = "Failed to parse output";
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }
}
