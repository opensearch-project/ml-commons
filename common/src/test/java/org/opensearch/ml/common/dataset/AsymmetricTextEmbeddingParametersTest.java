package org.opensearch.ml.common.dataset;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.util.function.Function;
import org.opensearch.ml.common.dataset.AsymmetricTextEmbeddingParameters.EmbeddingContentType;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.common.TestHelper.contentObjectToString;
import static org.opensearch.ml.common.TestHelper.testParseFromString;

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
    params = AsymmetricTextEmbeddingParameters.builder()
        .embeddingContentType(EmbeddingContentType.QUERY)
        .build();
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
    exceptionRule.expectMessage("No enum constant org.opensearch.ml.common.dataset.AsymmetricTextEmbeddingParameters.EmbeddingContentType.FU");
    String paramsStr = contentObjectToString(params);
    testParseFromString(params, paramsStr.replace("QUERY","fu"), function);
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

  private void readInputStream(AsymmetricTextEmbeddingParameters params) throws IOException {
    BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
    params.writeTo(bytesStreamOutput);

    StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
    AsymmetricTextEmbeddingParameters parsedParams = new AsymmetricTextEmbeddingParameters(streamInput);
    assertEquals(params, parsedParams);
  }
}
