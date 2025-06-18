package tsystems.janus.sourcecodeconverter.adapter.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsystems.aiecommon.auth.AIEAuthContextWrapper;
import com.tsystems.aiecommon.auth.AIEContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tsystems.janus.sourcecodeconverter.application.service.CodeConversionService;
import tsystems.janus.sourcecodeconverter.domain.model.CodeQLResult;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLTraceProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.llm.LlmConversionService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class CodeConversionController {

    @Value("${aie.project}")
    private String aieProject;

    @Value("${aie.username}")
    private String aieUsername;

    @Value("${aie.password}")
    private String aiePassword;

    private final CodeConversionService codeConversionService;
    private final CodeQLResultProcessor codeQLResultProcessor;
    private final CodeQLTraceProcessor sqlTraceProcessor;
    private final LlmConversionService llmConversionService;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public CodeConversionController(CodeConversionService codeConversionService, CodeQLResultProcessor codeQLResultProcessor, CodeQLTraceProcessor sqlTraceProcessor, LlmConversionService llmConversionService, ObjectMapper objectMapper) {
        this.codeConversionService = codeConversionService;
        this.codeQLResultProcessor = codeQLResultProcessor;
        this.sqlTraceProcessor = sqlTraceProcessor;
        this.llmConversionService = llmConversionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/conversion-logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter convertRepository(@RequestParam String repoUrl) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 minutes

        sseExecutor.execute(() -> {
            try {
                codeConversionService.convertRepository(repoUrl, logMessage -> {
                    try {
                        emitter.send(SseEmitter.event().name("log").data(logMessage));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                        System.err.println("Error sending SSE log: " + e.getMessage());
                    }
                });
                emitter.send(SseEmitter.event().name("completion").data("Conversion process finished."));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("Conversion failed: " + e.getMessage()));
                } catch (IOException ioException) {
                    System.err.println("Error sending SSE error event: " + ioException.getMessage());
                }
                emitter.completeWithError(e);
                System.err.println("Error during code conversion (SSE): " + e.getMessage());
                e.printStackTrace();
            }
        });

        emitter.onCompletion(() -> System.out.println("SSE Emitter completed."));
        emitter.onTimeout(() -> {
            System.err.println("SSE Emitter timed out.");
            emitter.complete();
        });
        emitter.onError(e -> System.err.println("SSE Emitter error: " + e.getMessage()));

        return emitter;
    }

    @GetMapping("/results")
    public ResponseEntity<List<ConversionTask>> getConversionResults() {
        try {
            File resultsFile = new File("src/main/resources/output/results.json");
            List<CodeQLResult> initialResults = sqlTraceProcessor.loadResultsFromFile(resultsFile);
            List<ConversionTask> conversionTasks = sqlTraceProcessor.processResults(initialResults);
            File structuredTasksFile = new File("src/main/resources/output/structured_tasks.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(structuredTasksFile, conversionTasks);

            List<ConversionTask> results = codeQLResultProcessor.loadConversionTasks(structuredTasksFile);

            return ResponseEntity.ok(results);
        } catch (FileNotFoundException e) {
            System.err.println("Results file not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.emptyList());
        } catch (IOException e) {
            System.err.println("Error reading results file: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<CodeQLResultProcessor.ProcessingStats> getConversionStats() {
        try {
            File resultsFile = new File("src/main/resources/output/results.json");
            CodeQLResultProcessor.ProcessingStats stats = codeQLResultProcessor.analyzeResults(resultsFile);
            return ResponseEntity.ok(stats);
        } catch (FileNotFoundException e) {
            System.err.println("Results file not found for stats: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new CodeQLResultProcessor.ProcessingStats());
        } catch (IOException e) {
            System.err.println("Error reading results file for stats: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new CodeQLResultProcessor.ProcessingStats());
        }
    }

    @GetMapping("/convert")
    public ResponseEntity<String> convertSql() {
        AIEContext.setContext(new AIEAuthContextWrapper(
                aieProject,
                aieUsername,
                aiePassword.toCharArray()
        ));

        try {
            File resultsFile = new File("src/main/resources/output/results.json");
            List<CodeQLResult> initialResults = sqlTraceProcessor.loadResultsFromFile(resultsFile);
            List<ConversionTask> conversionTasks = sqlTraceProcessor.processResults(initialResults);

            String result = "";

            for (ConversionTask task : conversionTasks) {
                System.out.println("Processing task for sink: " + task.getSink().getFilePath());

                String llmResponse = llmConversionService.convertSql(task);

                result += "### Conversion for " + task.getSink().getFilePath() + ":\n";
                result += llmResponse + "\n\n";
            }

            File outputFile = new File("src/main/resources/output/converted_sql.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("Error during SQL conversion: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Conversion failed: " + e.getMessage());
        } finally {
            AIEContext.clear();
        }
    }
}

