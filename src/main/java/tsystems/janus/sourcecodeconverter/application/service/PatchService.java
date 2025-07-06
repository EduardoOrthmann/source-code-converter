package tsystems.janus.sourcecodeconverter.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.git.PatchApplierService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;

import static tsystems.janus.sourcecodeconverter.infrastructure.llm.LlmParser.parseLlmResponse;

@Service
public class PatchService {

    private final CodeQLResultProcessor codeQLResultProcessor;
    private final PatchApplierService patchApplierService;
    private final File outputDir;
    private final String convertedSqlJsonFile;
    private final String structuredTasksJsonFile;
    private final String patchFile;

    public PatchService(CodeQLResultProcessor codeQLResultProcessor,
                        PatchApplierService patchApplierService,
                        @Value("${conversion.output.directory}") String outputDir,
                        @Value("${conversion.output.converted-sql-json}") String convertedSqlJsonFile,
                        @Value("${conversion.output.structured-tasks-json}") String structuredTasksJsonFile,
                        @Value("${conversion.output.patch-diff}") String patchFile) {
        this.codeQLResultProcessor = codeQLResultProcessor;
        this.patchApplierService = patchApplierService;
        this.outputDir = new File(outputDir);
        this.convertedSqlJsonFile = convertedSqlJsonFile;
        this.structuredTasksJsonFile = structuredTasksJsonFile;
        this.patchFile = patchFile;
    }

    public String generatePatch() throws IOException, InterruptedException {
        File llmResultsFile = new File(outputDir, convertedSqlJsonFile);
        File structuredTasksFile = new File(outputDir, structuredTasksJsonFile);

        List<LlmReplacementsResponse> replacements = parseLlmResponse(llmResultsFile);
        List<ConversionTask> originalTasks = codeQLResultProcessor.loadConversionTasks(structuredTasksFile);

        if (replacements.isEmpty()) {
            throw new NoSuchElementException("No replacements found in the converted SQL file.");
        }

        StringBuilder patchBuilder = new StringBuilder();

        for (LlmReplacementsResponse response : replacements) {
            ConversionTask originalTask = originalTasks.stream()
                    .filter(task -> task.getSink().getFilePath().equals(response.getFile()))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Original task not found for file: " + response.getFile()));

            patchApplierService.applyPatch(response, originalTask);
            String gitPatch = patchApplierService.generatePatchForFile(response.getFile());

            if (gitPatch != null && !gitPatch.isEmpty()) {
                patchBuilder.append(gitPatch).append("\n");
            }
        }

        String finalPatch = patchBuilder.toString().trim();

        if (finalPatch.isEmpty()) {
            throw new NoSuchElementException("No changes to apply, generated patch is empty.");
        }

        Files.write(Paths.get(outputDir.getAbsolutePath(), patchFile), finalPatch.getBytes());

        return finalPatch;
    }
}
