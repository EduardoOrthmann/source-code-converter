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
                
                 3. You MUST ONLY include the JSON if there are changes. If no changes are needed, don't add it at all.

                 **Global Conversion Context:**
                 - Source DB: DB2 LUW
                 - Target DB: PostgreSQL 14+

                 ---
                
                 **PRIMARY CONVERSION RULES:**
                 Analyze the `conversionUnits` and use the `type` and file path of each `component` to guide your logic.
                
                 **1. If `type` is "String (from XML)":**
                    -   This code is **ALWAYS** real SQL located in an `.xml` file.
                    -   You **MUST** convert its syntax from DB2 to PostgreSQL. The output should be the raw converted SQL.
                    -   For example, convert DB2-style parameters (`@param{VAR}`) to PostgreSQL-style positional placeholders (`$1`, `$2`, etc.).
                
                 **2. If `type` is "String":**
                    -   This code is almost always a **lookup key**, NOT real SQL. These keys are used to find the actual SQL in an XML file.
                    -   These keys often follow a pattern of words separated by dots (e.g., `INSERT.ERRORVALUES`, `SELECT.ERRORCONFIG.ALL`).
                    -   **CRITICAL RULE: You MUST NOT modify these keys.** Do not "fix" the dots or change the syntax in any way. Preserve them exactly as they are. They are identifiers, not SQL commands.
                
                    -   **THE ONLY EXCEPTION:** You should only convert a component with `type: "String"` if the `code` value is a complete, self-contained SQL statement.
                
                    -   **Examples to guide you:**
                        -   **DO NOT CHANGE:** `private static final String SELECT_ALL = "SELECT.ERRORCONFIG.ALL";`
                            -   **Reason:** The `code` is `"SELECT.ERRORCONFIG.ALL"`. This is a dot-notation key. Leave it alone.
                        -   **DO NOT CHANGE:** `private static final String INSERT_SCANNED = "INSERT.SCANNED";`
                            -   **Reason:** The `code` is `"INSERT.SCANNED"`. This is a dot-notation key. Leave it alone.
                        -   **DO CHANGE:** `String sql = "SELECT * FROM Employees FETCH FIRST 5 ROWS ONLY WITH UR";`
                            -   **Reason:** The `code` is `"SELECT * FROM Employees FETCH FIRST 5 ROWS ONLY WITH UR"`. This is a full, valid DB2 SQL statement. Convert it to `"SELECT * FROM Employees LIMIT 5"`.
                **3. Ensure Syntactic Correctness for the Target File:**
                   -   The `convertedCode` you provide must be a **syntactically valid replacement** for the original code within its target file.
                   -   **For `.java` files:** If the original code being replaced is a Java String literal (i.e., enclosed in double quotes), your `convertedCode` **MUST ALSO** be a valid Java String literal, including the surrounding double quotes.
                
                   -   **CRITICAL EXAMPLE FOR JAVA FILES:**
                       -   **Original Code (`code` field in the input):** `"\\"SELECT * FROM Employees FETCH FIRST 5 ROWS ONLY WITH UR\\""`
                       -   **INCORRECT `convertedCode`:** `"SELECT * FROM Employees LIMIT 5"` (This is raw SQL and will cause a Java compilation error).
                       -   **CORRECT `convertedCode`:** `"\\"SELECT * FROM Employees LIMIT 5\\""` (This is a valid Java String literal and will compile correctly).
                
                **4. General Rules:**
                   -   **DO NOT CHANGE VARIABLES OR PLACEHOLDERS:** Maintain all Java variable names and existing placeholders (like `?`). Only change the SQL syntax itself.
                   -   **Focus on the `code` field:** The code you need to analyze and potentially replace is always inside the `code` field of each component.
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
