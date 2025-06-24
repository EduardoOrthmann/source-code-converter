package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import com.tsystems.aiecommon.service.chat.AIEChatService;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConstructionStep;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;

import java.io.IOException;
import java.util.List;

import static tsystems.janus.sourcecodeconverter.infrastructure.llm.LlmParser.parseLlmResponse;

@Service
public class LlmPromptExecutor {

    private final AIEChatService chatService;

    public LlmPromptExecutor(AIEChatService chatService) {
        this.chatService = chatService;
    }

    public List<LlmReplacementsResponse> convertSql(List<ConversionTask> tasks) throws IOException {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPromptFromTask(tasks);

        return parseLlmResponse(chatService.executeWorkflowChat(
                userPrompt,
                systemPrompt,
                true
        ));
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
                    - `replacements`: an array of JSON objects, each with:
                      - `blockId`: the identifier of the block
                      - `convertedCode`: the rewritten SQL code
                    - `explanation`: a short explanation of the change
                
                    Here is an example of the JSON structure you must produce:
                    ```json
                    [
                        {
                            "file": "path/to/file.java",
                            "replacements": [
                                {
                                    "blockId": "BLOCK_ID_1",
                                    "convertedCode": "converted code here"
                                }
                            ],
                            "explanation": "Your concise explanation of the changes made."
                        }
                    ]
                    ```
                 3. You MUST ONLY include the JSON if there are changes. If no changes are needed, don't add it at all.
                
                 **Objective:**
                 Your task is to analyze a "Construction Trace" which shows how a final SQL query is built across multiple files and methods. Code blocks that are candidates for conversion are marked with `[CONVERSION_BLOCK_ID]` but it is not guaranteed that all blocks will be converted. You must analyze the code and convert it to PostgreSQL syntax, ensuring that the final SQL query remains functionally equivalent.
                
                 **Global Conversion Context:**
                 - Source DB: DB2 LUW
                 - Target DB: PostgreSQL 14+
                 - Functions: `FETCH FIRST n ROWS ONLY` -> `LIMIT n`, `CURRENT TIMESTAMP` -> `NOW()`.
                
                 ---
                
                """;
    }

    private String buildUserPromptFromTask(List<ConversionTask> tasks) {
        StringBuilder pb = new StringBuilder();

        pb.append("""
                <|USER|>
                Analyze the following trace of how an SQL query is constructed and executed. The code is Java and the SQL is DB2. Rewrite the tagged `[CONVERSION_BLOCK_ID]` blocks for PostgreSQL compatibility.
                
                **Cross-File Construction Trace:**
                """);

        String inferredPreConversionSql = "";

        for (ConversionTask task : tasks) {
            for (ConstructionStep step : task.getConstructionTrace()) {
                if (step.getCodeSnippet() == null || step.getCodeSnippet().isEmpty()) {
                    continue;
                }

                pb.append(String.format("""
                                %d. **File:** `%s`
                                   * **Method:** `%s`
                                   * **Action:** %s
                                   * **Code:**
                                     ```java
                                     // [%s]
                                     %s
                                     // [/%s]
                                     ```
                                """,
                        step.getOrder(),
                        step.getFilePath(),
                        step.getMethodName(),
                        step.getDescription(),
                        step.getBlockId(),
                        step.getCodeSnippet().trim(),
                        step.getBlockId()
                ));
            }

            if (task.getInferredPreConversionSql() != null && !task.getInferredPreConversionSql().isEmpty()) {
                inferredPreConversionSql = task.getInferredPreConversionSql();
            }
        }

        pb.append(String.format("""
                **Inferred Final DB2 SQL:** `%s`
                
                ---
                **Your Analysis and Conversion:**
                """, inferredPreConversionSql));

        return pb.toString();
    }
}
