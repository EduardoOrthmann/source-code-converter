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
    private final DockerShutdown dockerShutdown;
    private final CodeQLDockerConfig config;

    public CodeQLDockerAnalysisRunner(DockerImageBuilder imageBuilder, DockerContainerManager containerManager, DockerShutdown dockerShutdown, CodeQLDockerConfig config) {
        this.imageBuilder = imageBuilder;
        this.containerManager = containerManager;
        this.dockerShutdown = dockerShutdown;
        this.config = config;
    }

    public String prepareAnalysisEnvironment(File projectDir, File qlFile, File outputDir, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("📦 Building Docker image...");
        imageBuilder.buildImageIfNecessary(config.getImageName(), config.getDockerfileDir(), logConsumer);
        logConsumer.accept("✅ Docker image built.");

        List<String> volumes = prepareVolumes(projectDir, qlFile, outputDir, logConsumer);

        String containerName = config.getContainerName();
        containerManager.startContainer(config.getImageName(), containerName, volumes, logConsumer);

        addGitAttributes(containerName, logConsumer);

        logConsumer.accept("Docker environment prepared. Container: '" + containerName + "'");
        return containerName;
    }


    public void cleanupAnalysisEnvironment(String containerName, Consumer<String> logConsumer) {
        dockerShutdown.cleanupContainer(containerName, config.getDbVolumeName(), config.isPersistDbVolume(), logConsumer);
    }

    private void addGitAttributes(String containerName, Consumer<String> logConsumer) {
        try {
            logConsumer.accept("✍️ Creating .gitattributes to normalize line endings...");
            String command = "echo '* text=auto' > .gitattributes";
            containerManager.executeCommandInContainer(
                    containerName,
                    config.getContainerProjectPath(),
                    List.of("bash", "-c", command),
                    logConsumer
            );
            logConsumer.accept("✅ .gitattributes file created successfully.");
        } catch (Exception e) {
            logConsumer.accept("⚠️ Could not create .gitattributes file. Patches may have line ending issues.");
        }
    }

    private List<String> prepareVolumes(File projectDir, File qlFile, File outputDir, Consumer<String> logConsumer) throws IOException, InterruptedException {
        List<String> volumes = new ArrayList<>();
        volumes.add(projectDir.getAbsolutePath() + ":" + config.getContainerProjectPath());
        volumes.add(qlFile.getParentFile().getAbsolutePath() + ":" + config.getContainerQueryDir());
        volumes.add(outputDir.getAbsolutePath() + ":" + config.getContainerOutputDir());

        logConsumer.accept("Ensuring CodeQL database volume '" + config.getDbVolumeName() + "' exists...");
        boolean exists = containerManager.volumeExists(config.getDbVolumeName());

        if (!exists) {
            logConsumer.accept("📦 Creating CodeQL database volume: " + config.getDbVolumeName());
            containerManager.createVolume(config.getDbVolumeName(), logConsumer);
        } else {
            logConsumer.accept("✅ CodeQL database volume already exists: " + config.getDbVolumeName());
        }

        volumes.add(config.getDbVolumeName() + ":" + config.getContainerDbPath());

        return volumes;
    }
}
