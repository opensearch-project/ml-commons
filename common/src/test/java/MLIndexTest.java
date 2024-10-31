import org.junit.Test;
import org.opensearch.ml.common.MLIndex;

public class MLIndexTest {

    /**
     * Mappings are initialised during runtime when the MLIndex enum is referenced.
     * We want to catch any failure in mapping assignment before runtime.
     * This test simply references the enums to fetch the mapping. It will fail in case the enum is not initialized.
    **/
    @Test
    public void testValidateMappingsForSystemIndices() {
        for (MLIndex index : MLIndex.values()) {
            String mapping = index.getMapping();
        }
    }
}
