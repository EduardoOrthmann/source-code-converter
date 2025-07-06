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

    public String generatePatchForFile(String filePath) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();

        List<String> command = List.of(
                "git", "diff", "--ignore-cr-at-eol", "--", filePath.replace(workDir + "/", "")
        );

        return containerManager.executeCommandInContainerAndCaptureOutput(
                containerName,
                workDir,
                command
        );
    }
}
