/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input;

import lombok.NonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataframe.*;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.nlp.TextSimilarityMLInput;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.*;

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
        final ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("test", ColumnType.DOUBLE) };
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new ColumnValue[] { new DoubleValue(1.0) }));
        rows.add(new Row(new ColumnValue[] { new DoubleValue(2.0) }));
        rows.add(new Row(new ColumnValue[] { new DoubleValue(3.0) }));
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

    @Test
    public void parse_LinearRegression() throws IOException {
        String indexName = "index1";
        SearchQueryInputDataset inputDataset = SearchQueryInputDataset.builder()
                .indices(Arrays.asList(indexName))
                .searchSourceBuilder(new SearchSourceBuilder().query(new MatchAllQueryBuilder()).size(1))
                .build();
        String expectedInputStr = "{\"algorithm\":\"LINEAR_REGRESSION\",\"input_index\":[\"index1\"],\"input_query\":{\"size\":1,\"query\":{\"match_all\":{\"boost\":1.0}}}}";
        testParse(FunctionName.LINEAR_REGRESSION, inputDataset, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getInputDataset());
            assertEquals(1, ((SearchQueryInputDataset) parsedInput.getInputDataset()).getIndices().size());
            assertEquals(indexName, ((SearchQueryInputDataset) parsedInput.getInputDataset()).getIndices().get(0));
        });

        @NonNull
        DataFrame dataFrame = new DefaultDataFrame(
                new ColumnMeta[] { ColumnMeta.builder().name("value").columnType(ColumnType.FLOAT).build() });
        dataFrame.appendRow(new Float[] { 1.0f });
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder().dataFrame(dataFrame).build();
        expectedInputStr = "{\"algorithm\":\"LINEAR_REGRESSION\",\"input_data\":{\"column_metas\":[{\"name\":\"value\",\"column_type\":\"FLOAT\"}],\"rows\":[{\"values\":[{\"column_type\":\"FLOAT\",\"value\":1.0}]}]}}";
        testParse(FunctionName.LINEAR_REGRESSION, dataFrameInputDataset, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getInputDataset());
            assertEquals(1, ((DataFrameInputDataset) parsedInput.getInputDataset()).getDataFrame().size());
            assertEquals(1.0f, ((DataFrameInputDataset) parsedInput.getInputDataset()).getDataFrame().getRow(0)
                    .getValue(0).floatValue(), 1e-5);
        });
    }

    private void parse_NLPModel(FunctionName functionName) throws IOException {
        String sentence = "test sentence";
        String column = "column1";
        Integer position = 1;
        ModelResultFilter resultFilter = ModelResultFilter.builder()
                .targetResponse(Arrays.asList(column))
                .targetResponsePositions(Arrays.asList(position))
                .build();

        TextDocsInputDataSet inputDataset = TextDocsInputDataSet.builder().docs(Arrays.asList(sentence)).resultFilter(resultFilter).build();
        String expectedInputStr = "{\"algorithm\":\"functionName\",\"text_docs\":[\"test sentence\"],\"return_bytes\":false,\"return_number\":false,\"target_response\":[\"column1\"],\"target_response_positions\":[1]}";
        expectedInputStr = expectedInputStr.replace("functionName", functionName.toString());
        testParse(functionName, inputDataset, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getInputDataset());
            TextDocsInputDataSet parsedInputDataSet = (TextDocsInputDataSet) parsedInput.getInputDataset();
            assertEquals(1, parsedInputDataSet.getDocs().size());
            assertEquals(sentence, parsedInputDataSet.getDocs().get(0));
            assertEquals(1, parsedInputDataSet.getResultFilter().getTargetResponse().size());
            assertEquals(column, parsedInputDataSet.getResultFilter().getTargetResponse().get(0));
            assertEquals(position, parsedInputDataSet.getResultFilter().getTargetResponsePositions().get(0));
        });
    }

    @Test
    public void parse_NLP_Related() throws IOException {
        parse_NLPModel(FunctionName.TEXT_EMBEDDING);
        parse_NLPModel(FunctionName.SPARSE_TOKENIZE);
        parse_NLPModel(FunctionName.SPARSE_ENCODING);
    }

    private void parse_NLPModel_NullResultFilter(FunctionName functionName) throws IOException {
        String sentence = "test sentence";
        TextDocsInputDataSet inputDataset = TextDocsInputDataSet.builder().docs(Arrays.asList(sentence)).build();
        String expectedInputStr = "{\"algorithm\":\"functionName\",\"text_docs\":[\"test sentence\"]}";
        expectedInputStr = expectedInputStr.replace("functionName", functionName.toString());
        testParse(functionName, inputDataset, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getInputDataset());
            assertEquals(1, ((TextDocsInputDataSet) parsedInput.getInputDataset()).getDocs().size());
            assertEquals(sentence, ((TextDocsInputDataSet) parsedInput.getInputDataset()).getDocs().get(0));
        });
    }


    @Test
    public void parse_NLPRelated_NullResultFilter() throws IOException {
        parse_NLPModel_NullResultFilter(FunctionName.TEXT_EMBEDDING);
        parse_NLPModel_NullResultFilter(FunctionName.SPARSE_TOKENIZE);
        parse_NLPModel_NullResultFilter(FunctionName.SPARSE_ENCODING);
    }

    @Test
    public void parse_Remote_Model() throws IOException {
        Map<String, String> parameters = Map.of("TransformJobName", "new name");
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet.builder()
                .parameters(parameters)
                .actionType(ConnectorAction.ActionType.PREDICT)
                .build();

        String expectedInputStr = "{\"algorithm\":\"REMOTE\",\"parameters\":{\"TransformJobName\":\"new name\"},\"action_type\":\"PREDICT\"}";

        testParse(FunctionName.REMOTE, remoteInferenceInputDataSet, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getInputDataset());
            RemoteInferenceInputDataSet parsedInputDataSet = (RemoteInferenceInputDataSet) parsedInput.getInputDataset();
            assertEquals(ConnectorAction.ActionType.PREDICT, parsedInputDataSet.getActionType());
        });
    }

    @Test
    public void parse_Remote_Model_With_ActionType() throws IOException {
        Map<String, String> parameters = Map.of("TransformJobName", "new name");
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet.builder()
                .parameters(parameters)
                .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
                .build();

        String expectedInputStr = "{\"algorithm\":\"REMOTE\",\"parameters\":{\"TransformJobName\":\"new name\"},\"action_type\":\"BATCH_PREDICT\"}";

        testParseWithActionType(FunctionName.REMOTE, remoteInferenceInputDataSet, ConnectorAction.ActionType.BATCH_PREDICT, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getInputDataset());
            RemoteInferenceInputDataSet parsedInputDataSet = (RemoteInferenceInputDataSet) parsedInput.getInputDataset();
            assertEquals(ConnectorAction.ActionType.BATCH_PREDICT, parsedInputDataSet.getActionType());
        });
    }

    private void testParse(FunctionName algorithm, MLInputDataset inputDataset, String expectedInputStr, Consumer<MLInput> verify) throws IOException {
        MLInput input = MLInput.builder().inputDataset(inputDataset).algorithm(algorithm).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedInputStr, jsonStr);

        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                        Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLInput parsedInput = MLInput.parse(parser, algorithm.name());
        assertEquals(input.getFunctionName(), parsedInput.getFunctionName());
        assertEquals(input.getInputDataset().getInputDataType(), parsedInput.getInputDataset().getInputDataType());
        verify.accept(parsedInput);
    }

    private void testParseWithActionType(FunctionName algorithm, MLInputDataset inputDataset, ConnectorAction.ActionType actionType, String expectedInputStr, Consumer<MLInput> verify) throws IOException {
        MLInput input = MLInput.builder().inputDataset(inputDataset).algorithm(algorithm).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedInputStr, jsonStr);

        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                        Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLInput parsedInput = MLInput.parse(parser, algorithm.name(), actionType);
        assertEquals(input.getFunctionName(), parsedInput.getFunctionName());
        assertEquals(input.getInputDataset().getInputDataType(), parsedInput.getInputDataset().getInputDataType());
        verify.accept(parsedInput);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(input, parsedInput -> {
            assertEquals(input.getInputDataset().getInputDataType(), parsedInput.getInputDataset().getInputDataType());
        });
    }

    @Test
    public void readInputStream_NullFields() throws IOException {
        MLInput input = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).build();
        readInputStream(input, parsedInput -> {
            assertNull(parsedInput.getParameters());
            assertNull(parsedInput.getInputDataset());
        });
    }

    private void readInputStream(MLInput input, Consumer<MLInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLInput parsedInput = new MLInput(streamInput);
        assertEquals(input.getFunctionName(), parsedInput.getFunctionName());
        verify.accept(parsedInput);
    }

    @Test
    public void testParse_TextSimilarity() throws IOException {
        List<String> docs = List.of("That is a happy dog", "it's summer");
        String queryText = "today is sunny";
        MLInputDataset dataset = TextSimilarityInputDataSet.builder().textDocs(docs).queryText(queryText).build();
        input = new TextSimilarityMLInput(FunctionName.TEXT_SIMILARITY, dataset);
        MLInput inp = new MLInput(FunctionName.TEXT_SIMILARITY, null, dataset);
        String expected = "{\"algorithm\":\"TEXT_SIMILARITY\",\"query_text\":\"today is sunny\",\"text_docs\":[\"That is a happy dog\",\"it's summer\"]}";

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inp.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expected, jsonStr);
    }

}
