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

public class MLUpdateModelGroupInputTest {

    private MLUpdateModelGroupInput mlUpdateModelGroupInput;

    @Before
    public void setUp() throws Exception {

        mlUpdateModelGroupInput = mlUpdateModelGroupInput.builder()
                .modelGroupID("modelGroupId")
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
        mlUpdateModelGroupInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUpdateModelGroupInput parsedInput = new MLUpdateModelGroupInput(streamInput);
        assertEquals(mlUpdateModelGroupInput.getName(), parsedInput.getName());
        assertEquals(mlUpdateModelGroupInput.getTags().get(0), parsedInput.getTags().get(0));
        assertEquals(mlUpdateModelGroupInput.getTags().get(0).getKey(), parsedInput.getTags().get(0).getKey());
        assertEquals(mlUpdateModelGroupInput.getTags().get(0).getType(), parsedInput.getTags().get(0).getType());
        assertEquals(mlUpdateModelGroupInput.getTags().get(1), parsedInput.getTags().get(1));
        assertEquals(mlUpdateModelGroupInput.getTags().get(1).getKey(), parsedInput.getTags().get(1).getKey());
        assertEquals(mlUpdateModelGroupInput.getTags().get(1).getType(), parsedInput.getTags().get(1).getType());
    }
}
