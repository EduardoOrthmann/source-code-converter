package tsystems.janus.sourcecodeconverter.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.CodeQLResult;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLTraceProcessor;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class AnalysisResultService {

    private final CodeQLTraceProcessor sqlTraceProcessor;
    private final CodeQLResultProcessor codeQLResultProcessor;
    private final ObjectMapper objectMapper;
    private final File outputDir;
    private final String resultsJsonFile;
    private final String structuredTasksJsonFile;


    public AnalysisResultService(CodeQLTraceProcessor sqlTraceProcessor,
                                 CodeQLResultProcessor codeQLResultProcessor,
                                 ObjectMapper objectMapper,
                                 @Value("${conversion.output.directory}") String outputDir,
                                 @Value("${conversion.output.results-json}") String resultsJsonFile,
                                 @Value("${conversion.output.structured-tasks-json}") String structuredTasksJsonFile) {
        this.sqlTraceProcessor = sqlTraceProcessor;
        this.codeQLResultProcessor = codeQLResultProcessor;
        this.objectMapper = objectMapper;
        this.outputDir = new File(outputDir);
        this.resultsJsonFile = resultsJsonFile;
        this.structuredTasksJsonFile = structuredTasksJsonFile;
    }

    public List<ConversionTask> structureConversionTasks() throws IOException {
        File resultsFile = new File(outputDir, resultsJsonFile);
        List<CodeQLResult> initialResults = sqlTraceProcessor.loadResultsFromFile(resultsFile);
        List<ConversionTask> conversionTasks = sqlTraceProcessor.processResults(initialResults);

        File structuredTasksFile = new File(outputDir, structuredTasksJsonFile);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(structuredTasksFile, conversionTasks);

        return conversionTasks;
    }

    public CodeQLResultProcessor.ProcessingStats getProcessingStats() throws IOException {
        File resultsFile = new File(outputDir, resultsJsonFile);
        return codeQLResultProcessor.analyzeResults(resultsFile);
    }
}
