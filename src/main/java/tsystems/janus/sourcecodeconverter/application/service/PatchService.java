package tsystems.janus.sourcecodeconverter.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;
import tsystems.janus.sourcecodeconverter.infrastructure.buildTesting.BuildTestService;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.CodeQLDockerConfig;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerContainerManager;
import tsystems.janus.sourcecodeconverter.infrastructure.git.PatchApplierService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;

import static tsystems.janus.sourcecodeconverter.infrastructure.llm.LlmParser.parseLlmResponse;

@Service
public class PatchService {

    private final CodeQLResultProcessor codeQLResultProcessor;
    private final PatchApplierService patchApplierService;
    private final BuildTestService buildTestService;
    private final DockerContainerManager dockerContainerManager;
    private final CodeQLDockerConfig dockerConfig;
    private final Path patchesDir;
    private final String convertedSqlJsonFile;
    private final String structuredTasksJsonFile;

    public PatchService(CodeQLResultProcessor codeQLResultProcessor,
                        PatchApplierService patchApplierService,
                        BuildTestService buildTestService,
                        DockerContainerManager dockerContainerManager,
                        CodeQLDockerConfig dockerConfig,
                        @Value("${conversion.output.patches-directory}") String patchesDir,
                        @Value("${conversion.output.directory}") String outputDir,
                        @Value("${conversion.output.converted-sql-json}") String convertedSqlJsonFile,
                        @Value("${conversion.output.structured-tasks-json}") String structuredTasksJsonFile) {
        this.codeQLResultProcessor = codeQLResultProcessor;
        this.patchApplierService = patchApplierService;
        this.buildTestService = buildTestService;
        this.dockerContainerManager = dockerContainerManager;
        this.dockerConfig = dockerConfig;
        this.patchesDir = Paths.get(patchesDir);
        this.convertedSqlJsonFile = outputDir + "/" + convertedSqlJsonFile;
        this.structuredTasksJsonFile = outputDir + "/" + structuredTasksJsonFile;
    }

    public String generatePatch() throws IOException, InterruptedException {
        File llmResultsFile = new File(convertedSqlJsonFile);
        File structuredTasksFile = new File(structuredTasksJsonFile);

        List<LlmReplacementsResponse> replacements = parseLlmResponse(llmResultsFile);
        List<ConversionTask> originalTasks = codeQLResultProcessor.loadConversionTasks(structuredTasksFile);

        if (replacements.isEmpty()) {
            throw new NoSuchElementException("No replacements found in the converted SQL file.");
        }

        if (!Files.exists(patchesDir)) {
            Files.createDirectories(patchesDir);
        }

        int appliedPatches = 0;
        int patchCounter = 1;


        for (LlmReplacementsResponse response : replacements) {
            String branchName = "patch-branch-" + patchCounter++;

            patchApplierService.createAndCheckoutBranch(branchName);

            ConversionTask originalTask = originalTasks.stream()
                    .filter(task -> task.getSink().getFilePath().equals(response.getFile()))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Original task not found for file: " + response.getFile()));

            patchApplierService.applyPatch(response, originalTask);

            if (buildTestService.testSingleFile(response.getFile())) {
                patchApplierService.commitChanges(response.getFile(), response.getExplanation());
                String patchFileNameInContainer = patchApplierService.formatPatch();

                if (patchFileNameInContainer != null && !patchFileNameInContainer.isBlank()) {
                    String containerSrcPath = dockerConfig.getContainerProjectPath() + "/" + patchFileNameInContainer;
                    String uniquePatchName = String.format("%04d-%s", (patchCounter - 1), patchFileNameInContainer.substring(5));
                    String hostDestPath = patchesDir.resolve(uniquePatchName).toString();

                    dockerContainerManager.copyFileFromContainer(dockerConfig.getContainerName(), containerSrcPath, hostDestPath);
                    System.out.println("✅ Successfully created patch: " + uniquePatchName);
                    appliedPatches++;
                }
            } else {
                System.err.println("Compilation failed for: " + response.getFile() + ". Reverting changes.");
                patchApplierService.revertChanges(response.getFile());
            }

            patchApplierService.resetAndCleanBranch(branchName);
        }

        // Uncomment the following lines if you want to perform a full project build after applying all patches
//        if (appliedPatches > 0 && buildTestService.testFullBuild()) {
//            System.out.println("✅ Full project build successful after all patches.");
//        } else if (appliedPatches > 0) {
//            System.err.println("❌ Full project build failed. The generated patches may be unstable.");
//        }

        String summary = appliedPatches > 0
                ? "Patch generation complete. " + appliedPatches + " patches created in: " + patchesDir
                : "No patches were applied.";

        System.out.println(summary);
        return summary;
    }
}
