package tsystems.janus.sourcecodeconverter.infrastructure.git;

import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConstructionStep;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacement;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.CodeQLDockerConfig;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerContainerManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class PatchApplierService {

    private final DockerContainerManager containerManager;
    private final CodeQLDockerConfig dockerConfig;

    public PatchApplierService(DockerContainerManager containerManager, CodeQLDockerConfig dockerConfig) {
        this.containerManager = containerManager;
        this.dockerConfig = dockerConfig;
    }

    public void applyPatch(LlmReplacementsResponse llmResponse, ConversionTask originalTask) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String filePathInContainer = llmResponse.getFile();
        String workDir = dockerConfig.getContainerProjectPath();

        for (LlmReplacement replacement : llmResponse.getReplacements()) {
            Optional<ConstructionStep> originalStepOpt = originalTask.getConstructionTrace().stream()
                    .filter(step -> step.getBlockId().equals(replacement.getBlockId()))
                    .findFirst();

            if (originalStepOpt.isPresent()) {
                String originalCode = originalStepOpt.get().getCodeSnippet();
                String convertedCode = replacement.getConvertedCode();
                String blockId = replacement.getBlockId();
                int lineNumber = Integer.parseInt(blockId.substring(blockId.lastIndexOf('_') + 1));

                String commandToExecute = String.format("sed -i '%ds#%s#%s#' %s",
                        lineNumber,
                        escapeSed(originalCode),
                        escapeSed(convertedCode),
                        filePathInContainer);

                containerManager.executeCommandInContainer(containerName, workDir, List.of("bash", "-c", commandToExecute), System.out::println);

                System.out.println("Replaced with: " + convertedCode);
            } else {
                System.err.println("Could not find original code for blockId: " + replacement.getBlockId());
            }
        }
    }

    public void revertChanges(String filePath) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        String relativePath = filePath.replace(workDir + "/", "");
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "checkout", "--", relativePath), System.out::println);
        System.out.println("Reverted changes in: " + relativePath);
    }

    public void commitChanges(String filePath, String explanation) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        String relativePath = filePath.replace(workDir + "/", "");

        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "config", "user.email", "conversion-bot@example.com"), s -> {
        });
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "config", "user.name", "Conversion Bot"), s -> {
        });
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "add", relativePath), System.out::println);
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "commit", "-m", "Auto-convert DB2 to PostgreSQL", "-m", explanation), System.out::println);
        System.out.println("Committed changes for: " + relativePath);
    }

    public String formatPatch() throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();

        String patchFileName = containerManager.executeCommandInContainerAndCaptureOutput(
                containerName, workDir, List.of("git", "format-patch", "-1", "HEAD")
        ).trim();

        if (patchFileName.isBlank()) {
            System.err.println("❌ Patch file generation failed.");
            return null;
        }

        return patchFileName;
    }

    private String escapeSed(String text) {
        return text.replace("#", "\\#")
                .replace("\"", "\\\"")
                .replace("&", "\\&")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace(".", "\\.")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("|", "\\|");
    }
}
