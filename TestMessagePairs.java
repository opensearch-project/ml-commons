import java.util.List;
import java.util.ArrayList;

// Simple test to verify the message pair extraction logic
public class TestMessagePairs {
    
    // Simplified classes for testing
    static class Message {
        private String role;
        private List<ContentBlock> content;
        
        public Message(String role, String text) {
            this.role = role;
            this.content = List.of(new ContentBlock(text));
        }
        
        public String getRole() { return role; }
        public List<ContentBlock> getContent() { return content; }
    }
    
    static class ContentBlock {
        private String text;
        
        public ContentBlock(String text) {
            this.text = text;
        }
        
        public String getText() { return text; }
    }
    
    static class MessagePair {
        private final String userMessage;
        private final String assistantMessage;
        
        public MessagePair(String userMessage, String assistantMessage) {
            this.userMessage = userMessage;
            this.assistantMessage = assistantMessage;
        }
        
        public String getUserMessage() { return userMessage; }
        public String getAssistantMessage() { return assistantMessage; }
    }
    
    // Simplified version of the extraction logic
    public static List<MessagePair> extractMessagePairs(List<Message> messages) {
        List<MessagePair> pairs = new ArrayList<>();
        
        // Process messages in pairs, excluding the last message if it's a user message
        for (int i = 0; i < messages.size() - 1; i += 2) {
            Message userMessage = messages.get(i);
            
            // Ensure we have a user message followed by an assistant message
            if (i + 1 < messages.size()) {
                Message assistantMessage = messages.get(i + 1);
                
                // Validate roles (flexible validation - just ensure they're different)
                if (!userMessage.getRole().equals(assistantMessage.getRole())) {
                    String userText = userMessage.getContent().get(0).getText();
                    String assistantText = assistantMessage.getContent().get(0).getText();
                    
                    pairs.add(new MessagePair(userText, assistantText));
                }
            }
        }
        
        return pairs;
    }
    
    public static String extractQuestionText(List<Message> messages) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be empty");
        }
        
        Message lastMessage = messages.get(messages.size() - 1);
        return lastMessage.getContent().get(0).getText();
    }
    
    public static void main(String[] args) {
        // Test case: 3 messages (user -> assistant -> user)
        List<Message> messages = List.of(
            new Message("user", "I like red"),
            new Message("assistant", "Thanks for telling me that! I'll remember it."),
            new Message("user", "What colour do I like?")
        );
        
        List<MessagePair> pairs = extractMessagePairs(messages);
        String currentQuestion = extractQuestionText(messages);
        
        System.out.println("Message pairs found: " + pairs.size());
        for (int i = 0; i < pairs.size(); i++) {
            MessagePair pair = pairs.get(i);
            System.out.println("Pair " + (i + 1) + ":");
            System.out.println("  User: " + pair.getUserMessage());
            System.out.println("  Assistant: " + pair.getAssistantMessage());
        }
        
        System.out.println("Current question: " + currentQuestion);
        
        // Expected output:
        // Message pairs found: 1
        // Pair 1:
        //   User: I like red
        //   Assistant: Thanks for telling me that! I'll remember it.
        // Current question: What colour do I like?
    }
}