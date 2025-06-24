package tsystems.janus.sourcecodeconverter.infrastructure.git;

import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConstructionStep;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionTask;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacement;
import tsystems.janus.sourcecodeconverter.domain.model.LlmReplacementsResponse;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.CodeQLDockerConfig;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerContainerManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public void applyPatch(LlmReplacementsResponse llmResponse, ConversionTask originalTask) throws IOException {
        Path filePath = Paths.get(llmResponse.getFile());

        if (!Files.exists(filePath)) {
            System.err.println("File not found, skipping patch: " + filePath);
            return;
        }

        List<String> lines = Files.readAllLines(filePath);
        boolean fileModified = false;

        for (LlmReplacement replacement : llmResponse.getReplacements()) {
            Optional<ConstructionStep> originalStepOpt = originalTask.getConstructionTrace().stream()
                    .filter(step -> step.getBlockId().equals(replacement.getBlockId()))
                    .findFirst();

            if (originalStepOpt.isPresent()) {
                String originalCode = originalStepOpt.get().getCodeSnippet();
                String convertedCode = replacement.getConvertedCode();

                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(originalCode)) {
                        System.out.println("Found original code in " + filePath.getFileName() + " at line " + (i + 1));
                        String modifiedLine = lines.get(i).replace(originalCode, convertedCode);

                        lines.set(i, modifiedLine);
                        fileModified = true;

                        System.out.println("Replaced with: " + convertedCode);
                        break;
                    }
                }
            } else {
                System.err.println("Could not find original code for blockId: " + replacement.getBlockId());
            }
        }


        if (fileModified) {
            Files.write(filePath, lines);
            System.out.println("Successfully patched file: " + filePath.getFileName());
        }
    }

    public String generatePatchForFile(String filePath) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();

        List<String> command = List.of(
                "git", "diff", "--", filePath.replace(dockerConfig.getContainerProjectPath() + "/", "")
        );

        return containerManager.executeCommandInContainerAndCaptureOutput(
                containerName,
                dockerConfig.getContainerProjectPath(),
                command
        );
    }
}
