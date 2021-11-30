package org.opensearch.ml.common.parameter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataframe.DoubleValue;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class MLInputTest {

    MLInput input;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private final FunctionName algorithm = FunctionName.LINEAR_REGRESSION;

    private Function<XContentParser, MLInput> function = parser -> {
        try {
            return MLInput.parse(parser, algorithm.name());
        } catch (IOException e) {
            throw new RuntimeException("failed to parse MLInput", e);
        }
    };

    @Before
    public void setUp() throws Exception {
        final ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("test", ColumnType.DOUBLE)};
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new ColumnValue[]{new DoubleValue(1.0)}));
        rows.add(new Row(new ColumnValue[]{new DoubleValue(2.0)}));
        rows.add(new Row(new ColumnValue[]{new DoubleValue(3.0)}));
        DataFrame dataFrame = new DefaultDataFrame(columnMetas, rows);
        input = MLInput.builder()
                .algorithm(algorithm)
                .parameters(LinearRegressionParams.builder().learningRate(0.1).build())
                .inputDataset(DataFrameInputDataset.builder().dataFrame(dataFrame).build())
                .build();
    }

    @Test
    public void constructor_NullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm can't be null");
        MLInput.builder().build();
    }

}
