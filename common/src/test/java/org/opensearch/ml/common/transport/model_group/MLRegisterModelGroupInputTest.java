package org.opensearch.ml.common.transport.model_group;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.model.ModelGroupTag;

public class MLRegisterModelGroupInputTest {

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    @Before
    public void setUp() throws Exception {
        mlRegisterModelGroupInput =
        MLRegisterModelGroupInput
            .builder()
            .name("name")
            .description("description")
            .backendRoles(Arrays.asList("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .tags(List.of(new ModelGroupTag("tag1", "String"),
                    new ModelGroupTag("tag2", "Number")))
            .build();
    }

    @Test
    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlRegisterModelGroupInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLRegisterModelGroupInput parsedInput = new MLRegisterModelGroupInput(streamInput);
        assertEquals(mlRegisterModelGroupInput.getName(), parsedInput.getName());
    }
}
