package org.opensearch.ml.common.input.nlp;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.Strings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.FunctionName;
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
import static org.junit.Assert.assertTrue;

public class TextDocsMLInputTest {

    MLInput input;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private final FunctionName algorithm = FunctionName.TEXT_EMBEDDING;

    @Before
    public void setUp() throws Exception {
        ModelResultFilter resultFilter = ModelResultFilter.builder().returnBytes(true).returnNumber(true).targetResponse(Arrays.asList("field1")).targetResponsePositions(Arrays.asList(2)).build();
        MLInputDataset inputDataset = TextDocsInputDataSet.builder().docs(Arrays.asList("doc1", "doc2")).resultFilter(resultFilter).build();
        input = new TextDocsMLInput(algorithm, inputDataset);
    }

    @Test
    public void parseTextDocsMLInput() throws IOException {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = Strings.toString(builder);
        System.out.println(jsonStr);
        parseMLInput(jsonStr);
    }

    @Test
    public void parseTextDocsMLInput_OldWay() throws IOException {
        String jsonStr = "{\"text_docs\": [ \"doc1\", \"doc2\" ],\"return_number\": true, \"return_bytes\": true,\"target_response\": [ \"field1\" ], \"target_response_positions\": [2]}";
        parseMLInput(jsonStr);
    }

    @Test
    public void parseTextDocsMLInput_NewWay() throws IOException {
        String jsonStr = "{\"text_docs\":[\"doc1\",\"doc2\"],\"result_filter\":{\"return_bytes\":true,\"return_number\":true,\"target_response\":[\"field1\"], \"target_response_positions\": [2]}}";
        parseMLInput(jsonStr);
    }

    private void parseMLInput(String jsonStr) throws IOException {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();

        MLInput parsedInput = MLInput.parse(parser, input.getFunctionName().name());
        assertTrue(parsedInput instanceof TextDocsMLInput);
        assertEquals(input.getFunctionName(), parsedInput.getFunctionName());
        assertEquals(input.getInputDataset().getInputDataType(), parsedInput.getInputDataset().getInputDataType());
        TextDocsInputDataSet inputDataset = (TextDocsInputDataSet) parsedInput.getInputDataset();
        assertEquals(2, inputDataset.getDocs().size());
        assertEquals("doc1", inputDataset.getDocs().get(0));
        assertEquals("doc2", inputDataset.getDocs().get(1));
        assertNotNull(inputDataset.getResultFilter());
        assertTrue(inputDataset.getResultFilter().isReturnBytes());
        assertTrue(inputDataset.getResultFilter().isReturnNumber());
    }

}
