package tsystems.janus.sourcecodeconverter.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.CodeQLResult;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLTraceProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.llm.LlmPromptExecutor;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class LlmConversionService {

    private final CodeQLTraceProcessor sqlTraceProcessor;
    private final LlmPromptExecutor llmPromptExecutor;
    private final ObjectMapper objectMapper;
    private final File outputDir;
    private final String resultsJsonFile;
    private final String convertedSqlJsonFile;

    public LlmConversionService(CodeQLTraceProcessor sqlTraceProcessor, LlmPromptExecutor llmPromptExecutor,
                                ObjectMapper objectMapper,
                                @Value("${conversion.output.directory}") String outputDir,
                                @Value("${conversion.output.results-json}") String resultsJsonFile,
                                @Value("${conversion.output.converted-sql-json}") String convertedSqlJsonFile) {
        this.sqlTraceProcessor = sqlTraceProcessor;
        this.llmPromptExecutor = llmPromptExecutor;
        this.objectMapper = objectMapper;
        this.outputDir = new File(outputDir);
        this.resultsJsonFile = outputDir + "/" + resultsJsonFile;
        this.convertedSqlJsonFile = convertedSqlJsonFile;
    }

    public List<LlmReplacementsResponse> performAndSaveSqlConversion() throws IOException {
        File resultsFile = new File(resultsJsonFile);
        List<CodeQLResult> initialResults = sqlTraceProcessor.loadResultsFromFile(resultsFile);
        List<ConversionTask> conversionTasks = sqlTraceProcessor.processResults(initialResults);

        List<LlmReplacementsResponse> result = llmPromptExecutor.convertSql(conversionTasks);

        File outputFile = new File(outputDir, convertedSqlJsonFile);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, result);

        return result;
    }
}
