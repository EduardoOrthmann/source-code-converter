package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DockerContainerManager {

    private final DockerCommandExecutor commandExecutor;

    public DockerContainerManager(DockerCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public String startContainer(String imageName, String containerName, List<String> volumes, Consumer<String> logConsumer) throws IOException, InterruptedException {
        List<String> startCommand = new ArrayList<>(List.of(
                "docker", "run", "-d",
                "--name", containerName
        ));
        for (String volume : volumes) {
            startCommand.add("-v");
            startCommand.add(volume);
        }
        startCommand.add(imageName);
        startCommand.addAll(List.of("tail", "-f", "/dev/null")); // Keep container running

        logConsumer.accept("Starting Docker container...");
        commandExecutor.execute(startCommand, logConsumer);
        logConsumer.accept("✅ Docker container started: " + containerName);

        return containerName;
    }

    public void executeCommandInContainer(String containerName, String workDir, List<String> command, Consumer<String> logConsumer) throws IOException, InterruptedException {
        commandExecutor.execute(executeCommand(containerName, workDir, command), logConsumer);
    }

    public String executeCommandInContainerAndCaptureOutput(String containerName, String workDir, List<String> command) throws IOException, InterruptedException {
        return commandExecutor.executeAndCaptureOutput(executeCommand(containerName, workDir, command));
    }

    private List<String> executeCommand(String containerName, String workDir, List<String> command) {
        List<String> execCommand = new ArrayList<>(List.of("docker", "exec"));

        if (workDir != null && !workDir.isEmpty()) {
            execCommand.add("-w");
            execCommand.add(workDir);
        }

        execCommand.add(containerName);
        execCommand.addAll(command);
        return execCommand;
    }

    public void createVolume(String volumeName, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("Creating Docker volume '" + volumeName + "'");
        commandExecutor.execute(List.of("docker", "volume", "create", volumeName), logConsumer);
        logConsumer.accept("✅ Docker volume '" + volumeName + "' created.");
    }

    public boolean isMountedVolumeInContainerEmpty(String containerName, String wordDir, String mountPath) {
        List<String> command = List.of("sh", "-c", "\"if ls " + mountPath + "/* "+ mountPath +"/.* >/dev/null 2>&1; then echo 'not empty'; else echo 'empty'; fi\"");

        try {
            String output = this.executeCommandInContainerAndCaptureOutput(containerName, wordDir, command);
            System.out.println("Checking mounted volume in container: " + output.trim());
            return "empty".equals(output.trim());
        } catch (Exception e) {
            System.err.println("Error checking mounted volume in container: " + e.getMessage());
            return true;
        }
    }

    public boolean volumeExists(String volumeName) {
        List<String> command = List.of("docker", "volume", "inspect", volumeName);

        try {
            commandExecutor.execute(command, System.out::println);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void copyFileFromContainer(String containerName, String containerPath, String hostPath) throws IOException, InterruptedException {
        System.out.println("Copying file from container '" + containerName + "'...");
        System.out.println("Source: " + containerPath);
        System.out.println("Destination: " + hostPath);

        commandExecutor.execute(
                List.of("docker", "cp", containerName + ":" + containerPath, hostPath),
                System.out::println
        );
    }

    public String readFileFromContainer(String containerName, String workDir, String filePathInContainer) throws IOException, InterruptedException {
        StringBuilder contentBuilder = new StringBuilder();

        String output = executeCommandInContainerAndCaptureOutput(
                containerName,
                workDir,
                List.of("cat", filePathInContainer)
        );
        contentBuilder.append(output);
        return contentBuilder.toString();
    }

    public void writeFileToContainer(String containerName, String filePathInContainer, String content) throws IOException, InterruptedException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("temp-patch-", ".java");

            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            String containerDestination = containerName + ":" + filePathInContainer;
            commandExecutor.execute(
                    List.of("docker", "cp", tempFile.toAbsolutePath().toString(), containerDestination),
                    System.out::println
            );
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    System.err.println("Warning: Failed to delete temporary file: " + tempFile.toAbsolutePath());
                }
            }
        }
    }
}
