package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DockerCodeQLAnalyzer {

    private static final String IMAGE_NAME = "codeql-runner-image";
    private static final String CONTAINER_NAME = "temp-code-converter";
    private static final String DOCKERFILE_DIR = "C:/Users/orthm/Documents/Projetos/t-systems/source-code-converter/src/codeql-docker";
    private static final String CONTAINER_PROJECT_PATH = "/app/project";
    private static final String CONTAINER_QUERY_PATH = "/app/queries/sql-detection.ql";
    private static final String CONTAINER_DB_PATH = "/app/db";
    private static final String CONTAINER_RESULT_PATH = "/app/output/results.bqrs";
    private static final String CONTAINER_QUERY_DIR = "/app/queries";

    public void runCodeQLAnalysis(File projectDir, File qlFile, File outputDir, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("📦 Building Docker image...");
        buildImageIfNecessary(logConsumer);
        logConsumer.accept("✅ Docker image built.");

        String projectVolume = projectDir.getAbsolutePath() + ":" + CONTAINER_PROJECT_PATH;
        String queryDirVolume = qlFile.getParentFile().getAbsolutePath() + ":" + CONTAINER_QUERY_DIR;
        String outputVolume = outputDir.getAbsolutePath() + ":/app/output";

        List<String> startCommand = List.of(
                "docker", "run", "-d",
                "--name", CONTAINER_NAME,
                "-v", projectVolume,
                "-v", queryDirVolume,
                "-v", outputVolume,
                IMAGE_NAME,
                "tail", "-f", "/dev/null"
        );
        logConsumer.accept("Starting Docker container...");
        runCommand(startCommand, logConsumer);
        logConsumer.accept("✅ Docker container started: " + CONTAINER_NAME);

        registerShutdownHook(logConsumer);

        String analysisCommand =
                "cd " + CONTAINER_QUERY_DIR + " && " +
                        "codeql pack install && " +
                        "rm -rf ~/.codeql/packages/codeql/java-all/7.3.0/ext/ && " +
                        "codeql database create " + CONTAINER_DB_PATH + " --language=java --source-root=" + CONTAINER_PROJECT_PATH + " --overwrite && " +
                        "codeql query run " + CONTAINER_QUERY_PATH + " --database=" + CONTAINER_DB_PATH + " --output=" + CONTAINER_RESULT_PATH;

        List<String> execCommand = List.of(
                "docker", "exec", CONTAINER_NAME,
                "bash", "-c", analysisCommand
        );

        logConsumer.accept("Executing CodeQL analysis inside container...");
        runCommand(execCommand, logConsumer);
        logConsumer.accept("✅ CodeQL query executed successfully inside container.");
        logConsumer.accept("📄 Results written to: " + new File(outputDir, "results.bqrs").getAbsolutePath());
    }

    private void registerShutdownHook(Consumer<String> logConsumer) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanupContainer(logConsumer)));
    }

    private void cleanupContainer(Consumer<String> logConsumer) {
        try {
            logConsumer.accept("\n🧹 Cleaning up Docker container...");

            runCommand(List.of("docker", "rm", "-f", CONTAINER_NAME), logConsumer);
            logConsumer.accept("✅ Container stopped and removed.");
        } catch (IOException | InterruptedException e) {
            logConsumer.accept("⚠️ Failed to clean up container: " + e.getMessage());
            System.err.println("⚠️ Failed to clean up container: " + e.getMessage()); // Also print to stderr
        }
    }

    private void runCommand(List<String> command, Consumer<String> logConsumer) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logConsumer.accept(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String fullCommand = String.join(" ", command);
            logConsumer.accept("❌ Command failed with exit code " + exitCode + ": " + fullCommand);
            throw new RuntimeException("Command failed with exit code " + exitCode);
        }
    }

    private void buildImageIfNecessary(Consumer<String> logConsumer) throws IOException, InterruptedException {
        File dockerfileDir = new File(DOCKERFILE_DIR);
        if (!dockerfileDir.exists()) {
            logConsumer.accept("❌ Dockerfile directory not found: " + dockerfileDir.getAbsolutePath());
            throw new RuntimeException("❌ Dockerfile directory not found: " + dockerfileDir.getAbsolutePath());
        }

        ProcessBuilder checkImage = new ProcessBuilder("docker", "images", "-q", IMAGE_NAME);

        Process checkProcess = checkImage.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
        String line = reader.readLine();
        boolean imageExists = (line != null && !line.isEmpty());
        checkProcess.waitFor();

        if (imageExists) {
            logConsumer.accept("Docker image '" + IMAGE_NAME + "' already exists. Skipping build.");
            return;
        }

        logConsumer.accept("Building Docker image from: " + dockerfileDir.getAbsolutePath());
        runCommand(List.of("docker", "build", "-t", IMAGE_NAME, dockerfileDir.getAbsolutePath()), logConsumer);
    }
}
