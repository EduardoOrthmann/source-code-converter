package tsystems.janus.sourcecodeconverter.infrastructure.git;

import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.domain.model.ConversionUnit;
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

        String originalFileContent = containerManager.readFileFromContainer(containerName, workDir, filePathInContainer);
        String modifiedFileContent = originalFileContent.replace("\\r\\n", "\n");

        for (LlmReplacement replacement : llmResponse.getReplacements()) {
            Optional<ConversionUnit.Component> originalComponentOpt = originalTask.getConversionUnits().stream()
                    .flatMap(unit -> unit.getComponents().stream())
                    .filter(component -> component.getLocation().getStartLine() == replacement.getLocation().getStartLine() &&
                                         component.getLocation().getStartColumn() == replacement.getLocation().getStartColumn())
                    .findFirst();

            if (originalComponentOpt.isPresent()) {
                String originalCode = originalComponentOpt.get().getCode();
                String normalizedOriginalCode = originalCode.replace("\\r\\n", "\n");
                String convertedCode = replacement.getConvertedCode();

                if (modifiedFileContent.contains(normalizedOriginalCode)) {
                    modifiedFileContent = modifiedFileContent.replace(normalizedOriginalCode, convertedCode);
                    System.out.println("Queued replacement for: " + originalCode.substring(0, Math.min(originalCode.length(), 50)) + "...");
                } else {
                    System.err.println("Could not find code block in file content. Skipping replacement.");
                }
            } else {
                System.err.println("Could not find original code for blockId: " + replacement.getLocation());
            }
        }

        if (!originalFileContent.replaceAll("\\r\\n", "\n").equals(modifiedFileContent)) {
            System.out.println("Writing patched file back to container: " + filePathInContainer);
            containerManager.writeFileToContainer(containerName, workDir, filePathInContainer, modifiedFileContent);
        } else {
            System.out.println("No changes were applied to the file.");
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

    public void commit(String filePath, String message) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        String relativePath = filePath.replace(workDir + "/", "");

        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "config", "user.email", "conversion-bot@example.com"), s -> {
        });
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "config", "user.name", "Conversion Bot"), s -> {
        });
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "add", relativePath), System.out::println);
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "commit", "-m", message), System.out::println);
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

    public void createAndCheckoutBranch(String branchName) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "checkout", "-b", branchName), System.out::println);
        System.out.println("Created and switched to new branch: " + branchName);
    }

    public void resetAndCleanBranch(String branchName) throws IOException, InterruptedException {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "checkout", "main"), System.out::println);
        containerManager.executeCommandInContainer(containerName, workDir, List.of("git", "branch", "-D", branchName), System.out::println);
        System.out.println("Reset to main and deleted temporary branch: " + branchName);
    }
}
