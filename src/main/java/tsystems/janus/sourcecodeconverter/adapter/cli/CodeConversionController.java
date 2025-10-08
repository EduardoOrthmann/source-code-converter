package tsystems.janus.sourcecodeconverter.adapter.cli;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import tsystems.janus.sourcecodeconverter.application.service.*;
import tsystems.janus.sourcecodeconverter.domain.model.*;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class CodeConversionController {
    private final CodeConversionService codeConversionService;
    private final AnalysisResultService analysisResultService;
    private final LlmConversionService llmConversionService;
    private final PatchService patchService;

    public CodeConversionController(CodeConversionService codeConversionService,
                                    AnalysisResultService analysisResultService,
                                    LlmConversionService llmConversionService,
                                    PatchService patchService) {
        this.codeConversionService = codeConversionService;
        this.analysisResultService = analysisResultService;
        this.llmConversionService = llmConversionService;
        this.patchService = patchService;
    }

    // 1 starta o container
    @GetMapping(value = "/conversion-logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter convertRepository(@RequestParam String repoUrl) {
        return codeConversionService.convertRepositoryWithSse(repoUrl);
    }

    // 1 starta o container e usa ZIP file ao invés de URL
    @PostMapping(value = "/conversion-logs-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> convertRepositoryFromZip(@RequestParam("file") MultipartFile projectZip) {
        try {
            System.out.println("Received file: " + projectZip.getOriginalFilename());
            Map<String, Object> result = codeConversionService.convertRepository(projectZip);
            System.out.println("Conversion result: " + result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 2 gera estruturar o arquivo gerado pelo primeiro request
    @GetMapping("/results")
    public ResponseEntity<List<ConversionTask>> getConversionResults() throws IOException {
        List<ConversionTask> conversionTasks = analysisResultService.structureConversionTasks();
        return ResponseEntity.ok(conversionTasks);
    }

    // 3 precisa do primeiro request para gerar o arquivo results.json, não altera o funcionamento da aplicação
    @GetMapping("/stats")
    public ResponseEntity<CodeQLResultProcessor.ProcessingStats> getConversionStats() throws IOException {
        CodeQLResultProcessor.ProcessingStats stats = analysisResultService.getProcessingStats();
        return ResponseEntity.ok(stats);
    }

    // 4 precisa do primeiro e segundo request para gerar o arquivo converted_sql.json
    @PostMapping("/convert")
    public ResponseEntity<List<LlmReplacementsResponse>> convertSql() throws IOException {
        List<LlmReplacementsResponse> result = llmConversionService.performAndSaveSqlConversion();
        return ResponseEntity.ok(result);
    }

    // 5 precisa do primeiro, segundo e quarto request para gerar o patch
    @PostMapping("/generate-patch")
    public ResponseEntity<String> generatePatch() throws IOException, InterruptedException {
        String patch = patchService.generatePatch();
        return ResponseEntity.ok(patch);
    }
}
