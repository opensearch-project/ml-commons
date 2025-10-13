// Quick test to verify the InputValidator compiles and basic functionality works
import org.opensearch.ml.common.agent.*;
import org.opensearch.ml.common.exception.MLValidationException;
import java.util.Arrays;

public class TestValidation {
    public static void main(String[] args) {
        InputValidator validator = new InputValidator();
        
        // Test 1: Valid text input
        try {
            AgentInput textInput = new AgentInput("Hello world");
            validator.validateAgentInput(textInput);
            System.out.println("✅ Text input validation passed");
        } catch (MLValidationException e) {
            System.out.println("❌ Text input validation failed: " + e.getMessage());
        }
        
        // Test 2: Valid content block
        try {
            ContentBlock textBlock = new ContentBlock("Test text");
            AgentInput blockInput = new AgentInput(Arrays.asList(textBlock));
            validator.validateAgentInput(blockInput);
            System.out.println("✅ Content block validation passed");
        } catch (MLValidationException e) {
            System.out.println("❌ Content block validation failed: " + e.getMessage());
        }
        
        // Test 3: Invalid input (null)
        try {
            validator.validateAgentInput(null);
            System.out.println("❌ Null input should have failed");
        } catch (MLValidationException e) {
            System.out.println("✅ Null input correctly rejected: " + e.getMessage());
        }
        
        System.out.println("Basic validation tests completed");
    }
}