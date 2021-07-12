package org.opensearch.ml.engine.algorithms.custom;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.MLAlgoMetaData;
import org.opensearch.ml.engine.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Can't import the following library since the name repeats with a previous import
// import org.pmml4s.model.Model;
import org.pmml4s.data.Series;
import org.pmml4s.util.Utils;
import org.pmml4s.common.StructType;
import org.pmml4s.common.StructField;

public class PMMLModel implements MLAlgo {

    public PMMLModel() {
    }

    @Override
    public DataFrame predict(DataFrame dataFrame, Model model) {
        if (dataFrame == null || dataFrame.size() == 0) {
            throw new IllegalArgumentException("data frame can't be null or empty");
        }
        if (model == null || model.getContent() == null || model.getContent().length == 0) {
            throw new IllegalArgumentException("model and model content can't be null or empty");
        }
        try {
            // retrieve the model from byte array
            org.pmml4s.model.Model pmmlModel = org.pmml4s.model.Model.fromBytes(model.getContent());
            // input schema contains a list of input fields with its name and data type
            StructType inputSchema = pmmlModel.inputSchema();
            // make predictions for each data point
            List<Series> predictions = new ArrayList<>();
            for (int i = 0; i < dataFrame.size(); i++) {
                Row row = dataFrame.getRow(i);
                // turn row into pmml4s compatible format, object array
                Object[] inputArray = prepareInput(row, inputSchema);
                // utilize the external pmml4s package to predict
                Series prediction = pmmlModel.predict(Series.fromArray(inputArray));
                predictions.add(prediction);
            }
            // turn output into proper format for data frame builder
            List<Map<String, Object>> listPrediction = prepareOutput(predictions);
            return DataFrameBuilder.load(listPrediction);
        } catch (Exception e) {
            throw new RuntimeException("failed retrieving model or predicting using it", e);
        }
    }

    @Override
    public Model train(DataFrame dataFrame) {
        throw new RuntimeException("PMML models do not support re-training");
    }

    @Override
    public MLAlgoMetaData getMetaData() {
        return MLAlgoMetaData.builder().name("custom_pmml_model")
            .description("Custom model in pmml format.")
            .version("1.0")
            .predictable(true)
            .trainable(false)
            .build();
    }

    /**
     * Transforms row to pmml4s predict input format
     *
     * @param row         a row of input data frame
     * @param inputSchema the input schema of the pmml model
     * @return object array, representing a row of the data frame
     */
    private Object[] prepareInput(Row row, StructType inputSchema) {
        // convert the data to the desired type defined by PMML, and keep the same order as defined in the input schema.
        Object[] values = new Object[inputSchema.size()];
        for (int i = 0; i < values.length; i++) {
            // since values in a data point can be different types, utilize pmml4s to create the object array
            StructField sf = inputSchema.apply(i);
            values[i] = Utils.toVal(row.getValue(i).getValue(), sf.dataType());
        }
        return values;
    }

    /**
     * Transforms output arrays from pmml4s predictions to proper format for data frame builder
     *
     * @param predictions  pmml4s predictions
     * @return list of maps, in which each map represents one prediction
     */
    private List<Map<String, Object>> prepareOutput(List<Series> predictions) {
        List<Map<String, Object>> listPrediction = new ArrayList<>();
        // transform Series into maps
        for (Series prediction : predictions) {
            Map<String, Object> predictionMap = prediction.toJavaMap();
            listPrediction.add(predictionMap);
        }
        return listPrediction;
    }
}
