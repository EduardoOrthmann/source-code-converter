package tsystems.janus.sourcecodeconverter.infrastructure.codeQL;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import tsystems.janus.sourcecodeconverter.domain.model.CodeQLResult;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionUnit;
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
        Map<String, List<CodeQLResult>> groupedByFile = results.stream()
                .collect(Collectors.groupingBy(CodeQLResult::getPath));
        List<ConversionTask> conversionTasks = new ArrayList<>();

        for (Map.Entry<String, List<CodeQLResult>> entry : groupedByFile.entrySet()) {
            conversionTasks.add(createTaskFromFileGroup(entry.getKey(), entry.getValue()));
        }

        return conversionTasks;
    }

    private ConversionTask createTaskFromFileGroup(String filePath, List<CodeQLResult> fileResults) {
        fileResults.sort(Comparator.comparingInt(CodeQLResult::getStartLine));

        Sink sink = new Sink(filePath);
        ConversionTask task = new ConversionTask(sink, new ArrayList<>());

        Map<String, List<CodeQLResult>> groupedByMethod = fileResults.stream()
                .collect(Collectors.groupingBy(result -> result.getClassName() + "." + result.getMethodName()));

        for (Map.Entry<String, List<CodeQLResult>> methodEntry : groupedByMethod.entrySet()) {
            ConversionUnit unit = new ConversionUnit();
            unit.setUnitId(methodEntry.getKey());
            unit.setComponents(new ArrayList<>());

            for (CodeQLResult result : methodEntry.getValue()) {
                ConversionUnit.Component component = new ConversionUnit.Component();
                component.setCode(result.getCode());
                component.setType(result.getSourceExpressionType());
                component.setLocation(new ConversionUnit.Location(
                        result.getStartLine(),
                        result.getStartColumn(),
                        result.getEndLine(),
                        result.getEndColumn()
                ));
                unit.getComponents().add(component);
            }
            task.getConversionUnits().add(unit);
        }

        return task;
    }

    public List<CodeQLResult> loadResultsFromFile(File jsonFile) throws IOException {
        return objectMapper.readValue(jsonFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, CodeQLResult.class));
    }
}
