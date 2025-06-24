package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.IOException;
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

    public void executeCommandInContainer(String containerName, List<String> command, Consumer<String> logConsumer) throws IOException, InterruptedException {
        List<String> execCommand = new ArrayList<>(List.of(
                "docker", "exec", containerName
        ));
        execCommand.addAll(command);

        commandExecutor.execute(execCommand, logConsumer);
    }

    public String executeCommandInContainerAndCaptureOutput(String containerName, String workDir, List<String> command) throws IOException, InterruptedException {
        List<String> execCommand = new ArrayList<>(List.of("docker", "exec"));

        if (workDir != null && !workDir.isEmpty()) {
            execCommand.add("-w");
            execCommand.add(workDir);
        }

        execCommand.add(containerName);
        execCommand.addAll(command);
        return commandExecutor.executeAndCaptureOutput(execCommand);
    }

    public void createVolume(String volumeName, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("Creating Docker volume '" + volumeName + "' if it does not exist...");

        String existingVolumes = commandExecutor.executeAndCaptureOutput(List.of("docker", "volume", "ls", "-q", "--filter", "name=" + volumeName));
        if (existingVolumes.isEmpty()) {
            commandExecutor.execute(List.of("docker", "volume", "create", volumeName), logConsumer);
            logConsumer.accept("✅ Docker volume '" + volumeName + "' created.");
        } else {
            logConsumer.accept("✅ Docker volume '" + volumeName + "' already exists. Skipping creation.");
        }
    }

    public boolean volumeExists(String volumeName) {
        try {
            Process process = new ProcessBuilder("docker", "volume", "inspect", volumeName)
                    .redirectErrorStream(true)
                    .start();

            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error checking Docker volume existence", e);
        }
    }
}
