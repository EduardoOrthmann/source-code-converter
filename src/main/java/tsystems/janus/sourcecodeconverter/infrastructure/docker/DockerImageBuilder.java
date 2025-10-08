package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DockerImageBuilder {

    private final DockerCommandExecutor commandExecutor;

    public DockerImageBuilder(DockerCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void buildImageIfNecessary(String imageName, File dockerfileDir, Consumer<String> logConsumer) throws IOException, InterruptedException {
        if (!dockerfileDir.exists()) {
            System.out.println("❌ Dockerfile directory not found: " + dockerfileDir.getAbsolutePath());
            logConsumer.accept("❌ Dockerfile directory not found: " + dockerfileDir.getAbsolutePath());
            throw new RuntimeException("❌ Dockerfile directory not found: " + dockerfileDir.getAbsolutePath());
        }
        System.out.println("Checking if Docker image '" + imageName + "' exists...");

        logConsumer.accept("Checking if Docker image '" + imageName + "' exists...");
        String existingImages = commandExecutor.executeAndCaptureOutput(List.of("docker", "images", "-q", imageName));
        System.out.println("Existing images output: " + existingImages);

        boolean imageExists = !existingImages.isEmpty();

        if (imageExists) {
            logConsumer.accept("Docker image '" + imageName + "' already exists. Skipping build.");
            return;
        }

        System.out.println("Building Docker image '" + imageName + "' from directory: " + dockerfileDir.getAbsolutePath());
        logConsumer.accept("Building Docker image from: " + dockerfileDir.getAbsolutePath());
        commandExecutor.execute(List.of("docker", "build", "-t", imageName, dockerfileDir.getAbsolutePath()), logConsumer);
        System.out.println("Docker image '" + imageName + "' built successfully.");
    }
}
