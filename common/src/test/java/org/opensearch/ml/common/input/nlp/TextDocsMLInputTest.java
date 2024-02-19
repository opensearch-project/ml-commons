package org.opensearch.ml.common.input.nlp;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.dataset.AsymmetricTextEmbeddingParameters.EmbeddingContentType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TextDocsMLInputTest {

    MLInput input;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private final FunctionName algorithm = FunctionName.TEXT_EMBEDDING;

    @Before
    public void setUp() throws Exception {
        ModelResultFilter resultFilter = ModelResultFilter.builder().returnBytes(true).returnNumber(true)
                .targetResponse(Arrays.asList("field1")).targetResponsePositions(Arrays.asList(2)).build();
        MLInputDataset inputDataset = TextDocsInputDataSet.builder().docs(Arrays.asList("doc1", "doc2"))
                .resultFilter(resultFilter).build();
        input = new TextDocsMLInput(algorithm, inputDataset);
    }

    @Test
    public void parseTextDocsMLInput() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        System.out.println(jsonStr);
        parseMLInput(jsonStr, 2);
    }

    @Test
    public void parseTextDocsMLInput_OldWay() throws IOException {
        String jsonStr = "{\"text_docs\": [ \"doc1\", \"doc2\", null ],\"return_number\": true, \"return_bytes\": true,\"target_response\": [ \"field1\" ], \"target_response_positions\": [2]}";
        parseMLInput(jsonStr, 3);
    }

    @Test
    public void parseTextDocsMLInput_NewWay() throws IOException {
        String jsonStr = "{\"text_docs\":[\"doc1\",\"doc2\"],\"result_filter\":{\"return_bytes\":true,\"return_number\":true,\"target_response\":[\"field1\"], \"target_response_positions\": [2]}}";
        parseMLInput(jsonStr, 2);
    }

    @Test
    public void serializationRoundTrip() throws IOException {
        MLInput mlInput = TextDocsMLInput.builder().inputDataset(
            TextDocsInputDataSet.builder().docs(Arrays.asList("doc1", "doc2")).build()).algorithm(algorithm)
            .parameters(new AsymmetricTextEmbeddingParameters(EmbeddingContentType.QUERY))
            .build();

        String mlInput_json = mlInput.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS).toString();

        MLInput mlInput_2 = parseMLInputJson(mlInput_json);

        assertEquals(mlInput.getParameters(), mlInput_2.getParameters());
        assertEquals(mlInput.getAlgorithm(), mlInput_2.getAlgorithm());
        assertEquals(mlInput.getInputDataset().getInputDataType(), mlInput_2.getInputDataset().getInputDataType());

    }

    private void parseMLInput(String jsonStr, int docSize) throws IOException {
        TextDocsMLInput parsedInput = parseMLInputJson(jsonStr);
        assertTrue(parsedInput instanceof TextDocsMLInput);
        assertEquals(input.getFunctionName(), parsedInput.getFunctionName());
        assertEquals(input.getInputDataset().getInputDataType(), parsedInput.getInputDataset().getInputDataType());
        TextDocsInputDataSet inputDataset = (TextDocsInputDataSet) parsedInput.getInputDataset();
        assertEquals(docSize, inputDataset.getDocs().size());
        assertEquals("doc1", inputDataset.getDocs().get(0));
        assertEquals("doc2", inputDataset.getDocs().get(1));
        if (inputDataset.getDocs().size() > 2) {
            assertNull(inputDataset.getDocs().get(2));
        }
        assertNotNull(inputDataset.getResultFilter());
        assertTrue(inputDataset.getResultFilter().isReturnBytes());
        assertTrue(inputDataset.getResultFilter().isReturnNumber());
    }

    private TextDocsMLInput parseMLInputJson(String jsonStr) throws IOException {
        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                        Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();

        TextDocsMLInput parsedInput = (TextDocsMLInput) MLInput.parse(parser, input.getFunctionName().name());
        return parsedInput;
    }

}
