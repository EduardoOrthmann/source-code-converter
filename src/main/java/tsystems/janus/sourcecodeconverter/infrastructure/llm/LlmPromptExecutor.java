package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsystems.aiecommon.service.chat.AIEChatService;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionUnit;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static tsystems.janus.sourcecodeconverter.infrastructure.llm.LlmParser.parseLlmResponse;

@Service
public class LlmPromptExecutor {

    private final AIEChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmPromptExecutor(AIEChatService chatService) {
        this.chatService = chatService;
    }

    public List<LlmReplacementsResponse> convertSql(List<ConversionTask> tasks) throws IOException {
        List<LlmReplacementsResponse> allResponses = new ArrayList<>();
        String systemPrompt = buildSystemPrompt();

        for (ConversionTask task : tasks) {
            String userPrompt = buildUserPromptFromTask(task);
            String llmResponse = chatService.executeWorkflowChat(userPrompt, systemPrompt, true);
            var parsedResponse = parseLlmResponse(llmResponse);
            allResponses.addAll(parsedResponse);

            System.out.println("LLM Response: " + llmResponse);
            System.out.println("Parsed Responses: " + parsedResponse);
            System.out.println("All Responses So Far: " + allResponses);
        }

        return allResponses;
    }

    private String buildSystemPrompt() {
        return """
                 <|SYSTEM|>
                 You are an expert software engineer specializing in database migration from DB2 to Postgres. Your primary goal is to convert code with 100% functional equivalence.
                
                 **CRITICAL FORMAT REQUIREMENTS:**
                 1. Your output MUST be a **single valid array of JSON objects**.
                    - Each object in the array represents a file where at least one conversion occurred.
                    - The array must be formatted as valid JSON, with no additional text or comments outside the JSON structure.
                 2. Each JSON must contain:
                    - `file`: a string (the path of the file where at least one conversion occurred)
                    - `explanation`: a short explanation of the change
                    - `replacements`: an array of JSON objects, each with:
                      - `location`: an object with `startLine`, `startColumn`, `endLine`, and `endColumn` of the code to be replaced.
                      - `convertedCode`: the rewritten SQL code
                
                    Here is an example of the JSON structure you must produce:
                    ```json
                    [
                       {
                           "file": "path/to/file.java",
                           "explanation": "Your concise explanation of the changes made.",
                           "replacements": [
                               {
                                   "location": {
                                       "startLine": 12,
                                       "startColumn": 25,
                                       "endLine": 12,
                                       "endColumn": 118
                                   },
                                   "convertedCode": "SELECT * FROM users WHERE user_id = ? LIMIT 1"
                               }
                           ]
                       }
                    ]
                    ```
                 3. You MUST ONLY include the JSON if there are changes. If no changes are needed, don't add it at all.
                
                 **Objective:**
                 Your task is to analyze a "Construction Trace" which shows how a final SQL query is built across multiple files and methods. You must analyze the code and convert it to PostgreSQL syntax, ensuring that the final SQL query remains functionally equivalent.
                
                 **Global Conversion Context:**
                 - Source DB: DB2 LUW
                 - Target DB: PostgreSQL 14+
                
                 ---
                
                """;
    }

    private String buildUserPromptFromTask(ConversionTask task) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ConversionTask", e);
        }
    }
}
