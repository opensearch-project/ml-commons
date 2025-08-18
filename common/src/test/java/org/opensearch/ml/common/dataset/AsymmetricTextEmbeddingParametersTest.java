package org.opensearch.ml.common.dataset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.opensearch.ml.common.TestHelper.contentObjectToString;
import static org.opensearch.ml.common.TestHelper.testParseFromString;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters.EmbeddingContentType;
import org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat;

public class AsymmetricTextEmbeddingParametersTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    AsymmetricTextEmbeddingParameters params;
    private Function<XContentParser, AsymmetricTextEmbeddingParameters> function = parser -> {
        try {
            return (AsymmetricTextEmbeddingParameters) AsymmetricTextEmbeddingParameters.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse AsymmetricTextEmbeddingParameters", e);
        }
    };

    @Before
    public void setUp() {
        params = AsymmetricTextEmbeddingParameters.builder().embeddingContentType(EmbeddingContentType.QUERY).build();
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters_Passage() throws IOException {
        String paramsStr = contentObjectToString(params);
        testParseFromString(params, paramsStr.replace("QUERY", "PASSAGE"), function);
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters_Invalid() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage(
                "No enum constant org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters.EmbeddingContentType.FU"
            );
        String paramsStr = contentObjectToString(params);
        testParseFromString(params, paramsStr.replace("QUERY", "fu"), function);
    }

    @Test
    public void parse_EmptyAsymmetricTextEmbeddingParameters() throws IOException {
        TestHelper.testParse(AsymmetricTextEmbeddingParameters.builder().build(), function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(params);
    }

    @Test
    public void readInputStream_Success_EmptyParams() throws IOException {
        readInputStream(AsymmetricTextEmbeddingParameters.builder().embeddingContentType(EmbeddingContentType.PASSAGE).build());
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters_WithSparseEmbeddingFormat_LEXICAL() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(EmbeddingContentType.QUERY)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .build();
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters_WithSparseEmbeddingFormat_TOKEN_ID() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(EmbeddingContentType.PASSAGE)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters_OnlySparseEmbeddingFormat() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_AsymmetricTextEmbeddingParameters_SparseEmbeddingFormat_Invalid() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage("No enum constant org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat.INVALID");
        String jsonWithInvalidFormat = "{\"content_type\": \"QUERY\", \"sparse_embedding_format\": \"INVALID\"}";
        testParseFromString(params, jsonWithInvalidFormat, function);
    }

    @Test
    public void constructor_BackwardCompatibility() {
        AsymmetricTextEmbeddingParameters params = new AsymmetricTextEmbeddingParameters(EmbeddingContentType.QUERY);
        assertEquals(EmbeddingContentType.QUERY, params.getEmbeddingContentType());
        assertEquals(SparseEmbeddingFormat.WORD, params.getSparseEmbeddingFormat());
    }

    @Test
    public void constructor_WithSparseEmbeddingFormat() {
        AsymmetricTextEmbeddingParameters params = new AsymmetricTextEmbeddingParameters(
            EmbeddingContentType.PASSAGE,
            SparseEmbeddingFormat.TOKEN_ID
        );
        assertEquals(EmbeddingContentType.PASSAGE, params.getEmbeddingContentType());
        assertEquals(SparseEmbeddingFormat.TOKEN_ID, params.getSparseEmbeddingFormat());
    }

    @Test
    public void constructor_WithNullSparseEmbeddingFormat_DefaultsToLexical() {
        AsymmetricTextEmbeddingParameters params = new AsymmetricTextEmbeddingParameters(EmbeddingContentType.QUERY, null);
        assertEquals(EmbeddingContentType.QUERY, params.getEmbeddingContentType());
        assertEquals(SparseEmbeddingFormat.WORD, params.getSparseEmbeddingFormat());
    }

    @Test
    public void constructor_NullContentType_WithSparseEmbeddingFormat() {
        AsymmetricTextEmbeddingParameters params = new AsymmetricTextEmbeddingParameters(null, SparseEmbeddingFormat.TOKEN_ID);
        assertNull(params.getEmbeddingContentType());
        assertEquals(SparseEmbeddingFormat.TOKEN_ID, params.getSparseEmbeddingFormat());
    }

    @Test
    public void readInputStream_WithSparseEmbeddingFormat() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(EmbeddingContentType.PASSAGE)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();
        readInputStream(params);
    }

    @Test
    public void readInputStream_OnlySparseEmbeddingFormat() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();
        readInputStream(params);
    }

    @Test
    public void readInputStream_VersionCompatibility_Pre_V_3_2_0() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(EmbeddingContentType.QUERY)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();

        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        bytesStreamOutput.setVersion(Version.V_3_1_0);
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        streamInput.setVersion(Version.V_3_1_0);
        AsymmetricTextEmbeddingParameters parsedParams = new AsymmetricTextEmbeddingParameters(streamInput);

        assertEquals(EmbeddingContentType.QUERY, parsedParams.getEmbeddingContentType());
        assertEquals(SparseEmbeddingFormat.WORD, parsedParams.getSparseEmbeddingFormat());
    }

    @Test
    public void toXContent_IncludesSparseEmbeddingFormat() throws IOException {
        AsymmetricTextEmbeddingParameters params = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(EmbeddingContentType.QUERY)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();

        String jsonStr = contentObjectToString(params);
        assert (jsonStr.contains("\"content_type\":\"QUERY\""));
        assert (jsonStr.contains("\"sparse_embedding_format\":\"TOKEN_ID\""));
    }

    private void readInputStream(AsymmetricTextEmbeddingParameters params) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        AsymmetricTextEmbeddingParameters parsedParams = new AsymmetricTextEmbeddingParameters(streamInput);
        assertEquals(params, parsedParams);
    }
}
