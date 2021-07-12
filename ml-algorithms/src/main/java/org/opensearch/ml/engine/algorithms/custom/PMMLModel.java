package org.opensearch.ml.engine.algorithms.custom;

import org.opensearch.ml.common.dataframe.ColumnMeta;
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
        ColumnMeta[] columnMetas = dataFrame.columnMetas();
        try {
            // retrieve the model from byte array
            org.pmml4s.model.Model pmmlModel = org.pmml4s.model.Model.fromBytes(model.getContent());
            // turn dataFrame into pmml4s compatible format
            Object[][] inputArray = prepareInput(dataFrame);
            Object[] predictions = pmmlModel.predict(inputArray);
            // output in proper format
            List<Map<String, Object>> listPrediction = new ArrayList<>();
            for (Object e : predictions) {
                listPrediction.add(Collections.singletonMap("Prediction", e));
            }
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

    private Object[][] prepareInput(DataFrame dataFrame) {
        int numRows = dataFrame.size();
        int numColumns = dataFrame.getRow(0).size();
        Object[][] dataArray = new Object[numRows][numColumns];
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
