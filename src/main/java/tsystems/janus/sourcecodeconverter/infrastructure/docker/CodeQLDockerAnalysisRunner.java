package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class CodeQLDockerAnalysisRunner {

    private final DockerImageBuilder imageBuilder;
    private final DockerContainerManager containerManager;
    private final CodeQLDockerConfig config;

    public CodeQLDockerAnalysisRunner(DockerImageBuilder imageBuilder, DockerContainerManager containerManager, CodeQLDockerConfig config) {
        this.imageBuilder = imageBuilder;
        this.containerManager = containerManager;
        this.config = config;
    }

    public String prepareAnalysisEnvironment(File projectDir, File qlFile, File outputDir, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("📦 Building Docker image...");
        imageBuilder.buildImageIfNecessary(config.getImageName(), config.getDockerfileDir(), logConsumer);
        logConsumer.accept("✅ Docker image built.");

        List<String> volumes = new ArrayList<>();
        volumes.add(projectDir.getAbsolutePath() + ":" + config.getContainerProjectPath());
        volumes.add(qlFile.getParentFile().getAbsolutePath() + ":" + config.getContainerQueryDir());
        volumes.add(outputDir.getAbsolutePath() + ":" + config.getContainerOutputDir());

        if (config.isPersistDbVolume()) {
            logConsumer.accept("Ensuring CodeQL database volume '" + config.getDbVolumeName() + "' exists...");
            boolean exists = containerManager.volumeExists(config.getDbVolumeName());

            if (!exists) {
                logConsumer.accept("📦 Creating CodeQL database volume: " + config.getDbVolumeName());
                containerManager.createVolume(config.getDbVolumeName(), logConsumer);
            } else {
                logConsumer.accept("✅ CodeQL database volume already exists: " + config.getDbVolumeName());
            }

            volumes.add(config.getDbVolumeName() + ":" + config.getContainerDbPath());
        } else {
            logConsumer.accept("CodeQL database volume persistence is disabled. The database will be temporary.");
        }

        String containerName = config.getContainerName();
        containerManager.startContainer(config.getImageName(), containerName, volumes, logConsumer);
        containerManager.registerShutdownHook(containerName, config.getDbVolumeName(), config.isPersistDbVolume(), logConsumer);

        logConsumer.accept("Docker environment prepared. Container: '" + containerName + "'" );
        return containerName;
    }

    public void cleanupAnalysisEnvironment(String containerName, Consumer<String> logConsumer) {
        containerManager.cleanupContainer(containerName, config.getDbVolumeName(), config.isPersistDbVolume(), logConsumer);
    }
}
