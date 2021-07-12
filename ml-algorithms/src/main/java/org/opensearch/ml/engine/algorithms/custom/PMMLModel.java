package org.opensearch.ml.engine.algorithms.custom;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.MLAlgoMetaData;
import org.opensearch.ml.engine.Model;

import java.util.*;

// Cannot import the following library since the name repeats with a previous import
// import org.pmml4s.model.Model;

public class PMMLModel implements MLAlgo {

    public PMMLModel() {
    }

    @Override
    public DataFrame predict(DataFrame dataFrame, Model model) {
        if (dataFrame == null || dataFrame.size() == 0) {
            throw new IllegalArgumentException("data frame can't be null or empty");
        }
        if (model == null || model.getContent() == null || model.getContent().length == 0) {
            throw new IllegalArgumentException("model or model content can't be null or empty");
        }
        try {
            // retrieve the model from byte array
            org.pmml4s.model.Model pmmlModel = org.pmml4s.model.Model.fromBytes(model.getContent());
            // turn dataFrame into pmml4s compatible format
            Object[] inputArray = prepareInput(dataFrame);
            // utilize the external pmml4s package to predict
            Object[] predictions = pmmlModel.predict(inputArray);
            // turn output into proper format for data frame builder
            List<Map<String, Object>> listPrediction = prepareOutput(predictions);
            return DataFrameBuilder.load(listPrediction);
        } catch (Exception e) {
            throw new RuntimeException("failed retrieving model from model content or predicting", e);
        }
    }

    @Override
    public Model train(DataFrame dataFrame) {
        throw new RuntimeException("Unsupported train (for custom pmml models).");
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
     * Transforms input data frame to pmml4s predict input format
     * @param dataFrame input data to make predictions
     * @return array of values or value arrays, depending on how many predicting points are passed in (one or many)
     */
    private Object[] prepareInput(DataFrame dataFrame) {
        int numRows = dataFrame.size();
        int numColumns = dataFrame.getRow(0).size();
        if (numRows == 1) {
            // only one row of data, transform data frame into an array of values
            Object[] dataArray = new Object[numColumns];
            Row row = dataFrame.getRow(0);
            for (int j = 0; j < numColumns; j++) {
                dataArray[j] = row.getValue(j);
            }
            return dataArray;
        } else {
            // more than 1 row of data, transform data frame into a double array (matrix) of values
            Object[] dataArray = new Object[numRows];
            for (int i = 0; i < numRows; i++) {
                Row row = dataFrame.getRow(i);
                Object[] objectsRow = new Object[numColumns];
                for (int j = 0; j < numColumns; j++) {
                    objectsRow[j] = row.getValue(j);
                }
                dataArray[i] = objectsRow;
            }
            return dataArray;
        }
    }

    /**
     * Transforms output array from pmml4s predictions to proper format for data frame builder
     * @param predictions pmml4s produced predictions
     * @return list of map, with keys being "Prediction" and values being the prediction values
     */
    private List<Map<String, Object>> prepareOutput(Object[] predictions) {
        List<Map<String, Object>> listPrediction = new ArrayList<>();
        boolean multiplePredictions = predictions[0].getClass().isArray();
        if (!multiplePredictions) {
            // only one row of data
            int decision = ((double) predictions[0]) < 0 ? 1 : 0;
            listPrediction.add(Collections.singletonMap("Prediction", decision));
        } else {
            // more than 1 row of data
            for (Object prediction : predictions) {
                Object[] p = (Object[]) prediction;
                int decision = ((double) p[0]) < 0 ? 1 : 0;
                listPrediction.add(Collections.singletonMap("Prediction", decision));
            }
        }
        return listPrediction;
    }
}
