package tsystems.janus.sourcecodeconverter.infrastructure.codeQL;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;
import tsystems.janus.sourcecodeconverter.domain.model.CodeQLResult;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CodeQLResultProcessor {

    private final ObjectMapper objectMapper;

    public CodeQLResultProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public File extractCleanJsonFromTuples(File rawJsonFile) throws IOException {
        JsonNode rootNode = objectMapper.readTree(rawJsonFile);

        JsonNode selectNode = rootNode.get("#select");
        if (selectNode == null) {
            throw new IllegalArgumentException("Expected CodeQL tuple format not found");
        }

        JsonNode tuplesNode = selectNode.get("tuples");
        if (tuplesNode == null || !tuplesNode.isArray()) {
            throw new IllegalArgumentException("Tuples array not found in CodeQL output");
        }

        List<JsonNode> cleanResults = new ArrayList<>();

        for (JsonNode tuple : tuplesNode) {
            if (tuple.isArray() && !tuple.isEmpty()) {
                String jsonString = tuple.get(0).asText();

                try {
                    JsonNode jsonObject = objectMapper.readTree(jsonString);
                    cleanResults.add(jsonObject);
                } catch (Exception e) {
                    System.err.println("Error parsing JSON from tuple: " + e.getMessage());
                    System.err.println("Problematic JSON string: " + jsonString);
                }
            }
        }

        File outputFile = new File(rawJsonFile.getParent(), "clean-results.json");

        ArrayNode arrayNode = objectMapper.createArrayNode();
        cleanResults.forEach(arrayNode::add);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, arrayNode);

        System.out.println("✅ Extracted " + cleanResults.size() + " clean JSON objects");
        System.out.println("📄 Clean results written to: " + outputFile.getAbsolutePath());

        return outputFile;
    }

    public ProcessingStats analyzeResults(File cleanJsonFile) throws IOException {
        JsonNode rootNode = objectMapper.readTree(cleanJsonFile);

        ProcessingStats stats = new ProcessingStats();

        if (rootNode.isArray()) {
            stats.totalQueries = rootNode.size();

            for (JsonNode result : rootNode) {
                String queryType = result.path("type").asText();

                switch (queryType.toLowerCase()) {
                    case "static":
                        stats.staticQueries++;
                        break;
                    case "dynamic":
                        stats.dynamicQueries++;
                        break;
                    case "parameterized":
                        stats.parameterizedQueries++;
                        break;
                }

                String fileName = result.path("path").asText();
                stats.fileCount.merge(fileName, 1, Integer::sum);

                String className = result.path("className").asText();
                stats.classCount.merge(className, 1, Integer::sum);
            }
        }

        return stats;
    }

    public List<CodeQLResult> loadResults(File resultsFile) throws IOException {
        if (!resultsFile.exists()) {
            throw new FileNotFoundException("Results file not found in path: " + resultsFile.getAbsolutePath());
        }
        return objectMapper.readValue(resultsFile, new TypeReference<List<CodeQLResult>>() {});
    }

    public List<ConversionTask> loadConversionTasks(File tasksFile) throws IOException {
        if (!tasksFile.exists()) {
            throw new FileNotFoundException("Tasks file not found in path: " + tasksFile.getAbsolutePath());
        }
        return objectMapper.readValue(tasksFile, new TypeReference<List<ConversionTask>>() {});
    }

    public static class ProcessingStats {
        public int totalQueries = 0;
        public int staticQueries = 0;
        public int dynamicQueries = 0;
        public int parameterizedQueries = 0;
        public Map<String, Integer> fileCount = new HashMap<>();
        public Map<String, Integer> classCount = new HashMap<>();

        @Override
        public String toString() {
            return String.format(
                    "Processing Stats:\n" +
                            "  Total Queries: %d\n" +
                            "  Static: %d\n" +
                            "  Dynamic: %d\n" +
                            "  Parameterized: %d\n" +
                            "  Files: %d\n" +
                            "  Classes: %d",
                    totalQueries, staticQueries, dynamicQueries, parameterizedQueries,
                    fileCount.size(), classCount.size()
            );
        }
    }
}
