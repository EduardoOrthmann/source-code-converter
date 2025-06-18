package tsystems.janus.sourcecodeconverter.infrastructure.codeQL;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import tsystems.janus.sourcecodeconverter.domain.model.CodeQLResult;
import tsystems.janus.sourcecodeconverter.domain.model.ConstructionStep;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.Sink;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CodeQLTraceProcessor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ConversionTask> processResults(List<CodeQLResult> results) {
        // Step 1: Group results by a composite key of their file path and method name.
        // This is our core assumption: all SQL in one method belongs to one query.
        Map<String, List<CodeQLResult>> groupedByMethod = results.stream()
                .collect(Collectors.groupingBy(
                        result -> result.getPath() + "::" + result.getMethodName()
                ));

        // Step 2: Process each group into a single ConversionTask.
        List<ConversionTask> conversionTasks = new ArrayList<>();
        for (List<CodeQLResult> group : groupedByMethod.values()) {
            conversionTasks.add(createTaskFromGroup(group));
        }

        return conversionTasks;
    }

    private ConversionTask createTaskFromGroup(List<CodeQLResult> group) {
        // Sort the fragments by their starting line number to ensure correct order.
        group.sort(Comparator.comparingInt(CodeQLResult::getStartLine));

        ConversionTask task = new ConversionTask();
        task.setConstructionTrace(new ArrayList<>());

        // Use the first result to populate high-level info.
        CodeQLResult firstResult = group.get(0);
        task.setSink(new Sink(firstResult.getPath(), firstResult.getMethodName()));

        StringBuilder inferredSqlBuilder = new StringBuilder();

        // Create a construction step for each SQL fragment.
        int order = 1;
        for (CodeQLResult result : group) {
            ConstructionStep step = new ConstructionStep();
            step.setOrder(order++);
            step.setFilePath(result.getPath());
            step.setMethodName(result.getMethodName());
            step.setCodeSnippet(result.getCode());
            step.setDescription("SQL fragment found in class " + result.getClassName());
            step.setBlockId(String.format("%S_BLOCK_%s",
                    result.getMethodName().toUpperCase(),
                    result.getStartLine()
            ));

            task.getConstructionTrace().add(step);
            inferredSqlBuilder.append(result.getCode()).append(" "); // Append for inferred SQL
        }

        // Set the final inferred SQL and other context
        task.setInferredPreConversionSql(inferredSqlBuilder.toString().trim());

        // The sink's line can be approximated by the last step's line
        task.getSink().setApproximateEndLine(group.get(group.size() - 1).getStartLine());

        // Populate supplemental context
        Map<String, Object> context = new HashMap<>();
        context.put("sourceClassName", firstResult.getClassName());
        task.setSupplementalContext(context);

        return task;
    }

    public List<CodeQLResult> loadResultsFromFile(File jsonFile) throws IOException {
        return objectMapper.readValue(jsonFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, CodeQLResult.class));
    }
}
