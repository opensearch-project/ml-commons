package org.opensearch.ml.common.output.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class ModelResultFilterTest {

    ModelResultFilter resultFilter;

    @Before
    public void setUp() throws Exception {
        String column = "column1";
        Integer position = 1;
        resultFilter = ModelResultFilter
            .builder()
            .targetResponse(Arrays.asList(column))
            .targetResponsePositions(Arrays.asList(position))
            .build();
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(resultFilter, parsedFilter -> {
            assertArrayEquals(
                resultFilter.getTargetResponse().toArray(new String[0]),
                parsedFilter.getTargetResponse().toArray(new String[0])
            );
            assertFalse(parsedFilter.returnBytes);
            assertFalse(parsedFilter.returnNumber);
        });
    }

    @Test
    public void readInputStream_NullFields() throws IOException {
        ModelResultFilter resultFilter = ModelResultFilter.builder().build();
        readInputStream(resultFilter, parsedFilter -> {
            assertNull(parsedFilter.getTargetResponse());
            assertNull(parsedFilter.getTargetResponsePositions());
            assertFalse(parsedFilter.returnBytes);
            assertFalse(parsedFilter.returnNumber);
        });
    }

    private void readInputStream(ModelResultFilter input, Consumer<ModelResultFilter> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        ModelResultFilter parsedFilter = new ModelResultFilter(streamInput);
        verify.accept(parsedFilter);
    }
}
