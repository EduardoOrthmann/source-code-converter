package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;
import tsystems.janus.sourcecodeconverter.infrastructure.util.JsonHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Component
public class LlmParser {

    private LlmParser() {
    }

    public static List<LlmReplacementsResponse> parseLlmResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty() || !JsonHelper.isValidJson(llmResponse)) {
            throw new IllegalArgumentException("Invalid LLM response format. Expected a valid JSON array.");
        }

        return JsonHelper.parseJsonArray(llmResponse, new ObjectMapper().getTypeFactory().constructCollectionType(List.class, LlmReplacementsResponse.class));
    }

    public static List<LlmReplacementsResponse> parseLlmResponse(File llmResponseFile) {
        try {
            if (!llmResponseFile.exists()) {
                throw new IllegalArgumentException("LLM response file does not exist: " + llmResponseFile.getAbsolutePath());
            }

            String llmResponse = new String(Files.readAllBytes(llmResponseFile.toPath()));
            return parseLlmResponse(llmResponse);
        } catch (IOException e) {
            throw new RuntimeException("Error reading LLM response file: " + llmResponseFile.getAbsolutePath(), e);
        }
    }
}
