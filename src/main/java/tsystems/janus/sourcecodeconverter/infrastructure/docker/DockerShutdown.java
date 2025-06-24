package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DockerShutdown {

    @Value("${codeql.db.volume-name}")
    private String dbVolumeName;

    @Value("${codeql.docker.container-name}")
    private String containerName;

    private final DockerCommandExecutor commandExecutor;

    public DockerShutdown(DockerCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @PreDestroy
    public void shutdownAllContainers() {
        System.out.println("--- Spring Application shutting down. Cleaning up all active Docker containers. ---");

        Consumer<String> shutdownLogger = System.out::println;

        cleanupContainer(containerName, dbVolumeName, true, shutdownLogger);
        System.out.println("--- Docker cleanup complete. ---");
    }

    public void cleanupContainer(String containerName, String dbVolumeName, boolean persistDbVolume, Consumer<String> logConsumer) {
        try {
            logConsumer.accept("\n🧹 Cleaning up Docker container...");
            commandExecutor.execute(List.of("docker", "rm", "-f", containerName), logConsumer);
            logConsumer.accept("✅ Container stopped and removed.");

            if (!persistDbVolume) {
                logConsumer.accept("Attempting to remove associated CodeQL database volume '" + dbVolumeName + "'...");
                removeVolume(dbVolumeName, logConsumer);
            } else {
                logConsumer.accept("✅ CodeQL database volume '" + dbVolumeName + "' is configured to persist. Skipping removal.");
            }
        } catch (IOException | InterruptedException e) {
            logConsumer.accept("⚠️ Failed to clean up container: " + e.getMessage());
            System.err.println("⚠️ Failed to clean up container: " + e.getMessage());
        }
    }

    public void removeVolume(String volumeName, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("Removing Docker volume '" + volumeName + "'...");
        commandExecutor.execute(List.of("docker", "volume", "rm", "-f", volumeName), logConsumer);
        logConsumer.accept("✅ Docker volume '" + volumeName + "' removed.");
    }
}
