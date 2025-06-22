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
        Map<String, List<CodeQLResult>> groupedByMethod = results.stream()
                .collect(Collectors.groupingBy(
                        result -> result.getPath() + "::" + result.getMethodName()
                ));

        List<ConversionTask> conversionTasks = new ArrayList<>();
        for (List<CodeQLResult> group : groupedByMethod.values()) {
            conversionTasks.add(createTaskFromGroup(group));
        }

        return conversionTasks;
    }

    private ConversionTask createTaskFromGroup(List<CodeQLResult> group) {
        group.sort(Comparator.comparingInt(CodeQLResult::getStartLine));

        ConversionTask task = new ConversionTask();
        task.setConstructionTrace(new ArrayList<>());

        CodeQLResult firstResult = group.get(0);
        task.setSink(new Sink(firstResult.getPath(), firstResult.getMethodName()));

        StringBuilder inferredSqlBuilder = new StringBuilder();

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
            inferredSqlBuilder.append(result.getCode()).append(" ");
        }

        task.setInferredPreConversionSql(inferredSqlBuilder.toString().trim());

        task.getSink().setApproximateEndLine(group.get(group.size() - 1).getStartLine());

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
