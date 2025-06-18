package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import com.tsystems.aiecommon.service.chat.AIEChatService;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;

import java.io.IOException;

@Service
public class LlmConversionService {

    private final AIEChatService chatService;

    public LlmConversionService(AIEChatService chatService) {
        this.chatService = chatService;
    }

    public String convertSql(ConversionTask task) throws IOException {
        String prompt = buildPromptFromTask(task);

        // Using the chat service as an example
        return chatService.executeWorkflowChat(
                prompt,
                "You are an expert software engineer specializing in database migration from DB2 to PostgreSQL.",
                false // 'false' ensures each conversion is a fresh, independent task
        );
    }

    private String buildPromptFromTask(ConversionTask task) {
        // This is where you implement the logic to convert the 'task' object
        // into the final, formatted string prompt for the LLM.
        // (e.g., iterating through the constructionTrace, adding block_ids, etc.)

        // Simplified example:
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("### Conversion Task ###\n\n");
        promptBuilder.append("Analyze the following trace and convert the tagged blocks from DB2 to PostgreSQL.\n\n");
        promptBuilder.append("--- TRACE ---\n");
        task.getConstructionTrace().forEach(step -> {
            promptBuilder.append(String.format("%d. File: %s, Method: %s\n", step.getOrder(), step.getFilePath(), step.getMethodName()));
            promptBuilder.append(String.format("// [%s]\n", step.getBlockId()));
            promptBuilder.append(step.getCodeSnippet()).append("\n");
            promptBuilder.append(String.format("// [/%s]\n\n", step.getBlockId()));
        });
        promptBuilder.append("--- END TRACE ---\n\n");
        promptBuilder.append("Your output must be a single, valid JSON object containing an array of replacements for the blocks that need to change.");

        return promptBuilder.toString();
    }
}
