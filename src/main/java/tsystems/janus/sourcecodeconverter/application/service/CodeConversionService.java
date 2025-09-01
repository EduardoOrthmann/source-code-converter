package tsystems.janus.sourcecodeconverter.application.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLRunner;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.AnalysisPathProvider;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.CodeQLDockerAnalysisRunner;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.CodeQLDockerConfig;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerContainerManager;
import tsystems.janus.sourcecodeconverter.infrastructure.git.GitCloner;
import tsystems.janus.sourcecodeconverter.infrastructure.sse.LoggableTask;
import tsystems.janus.sourcecodeconverter.infrastructure.sse.SseLogExecutor;

@Service
public class CodeConversionService {

    private final GitCloner repositoryCloner;
    private final CodeQLDockerAnalysisRunner codeQLDockerAnalysisRunner;
    private final CodeQLRunner codeQLCliExecutor;
    private final DockerContainerManager dockerContainerManager;
    private final CodeQLResultProcessor resultProcessor;
    private final AnalysisPathProvider pathProvider;
    private final CodeQLDockerConfig dockerConfig;
    private final CodeQLDockerConfig codeQLDockerConfig;
    private final SseLogExecutor sseLogExecutor;

    public CodeConversionService(GitCloner repositoryCloner, CodeQLDockerAnalysisRunner codeQLDockerAnalysisRunner,
                                 CodeQLRunner codeQLCliExecutor, DockerContainerManager dockerContainerManager,
                                 CodeQLResultProcessor resultProcessor, AnalysisPathProvider pathProvider, CodeQLDockerConfig dockerConfig,
                                 CodeQLDockerConfig codeQLDockerConfig, SseLogExecutor sseLogExecutor) {
        this.repositoryCloner = repositoryCloner;
        this.codeQLDockerAnalysisRunner = codeQLDockerAnalysisRunner;
        this.codeQLCliExecutor = codeQLCliExecutor;
        this.dockerContainerManager = dockerContainerManager;
        this.resultProcessor = resultProcessor;
        this.pathProvider = pathProvider;
        this.dockerConfig = dockerConfig;
        this.codeQLDockerConfig = codeQLDockerConfig;
        this.sseLogExecutor = sseLogExecutor;
    }

    private void convertRepository(String repoUrl, Consumer<String> logConsumer) throws Exception {
        logConsumer.accept("Starting code conversion for repository: " + repoUrl);

        File projectDir = repositoryCloner.cloneRepository(repoUrl);
        logConsumer.accept("✅ Repository cloned to: " + projectDir.getAbsolutePath());

        File qlFile = pathProvider.getQueryFile();
        logConsumer.accept("Found CodeQL query file: " + qlFile.getAbsolutePath());

        File outputDir = pathProvider.getOutputDirectory();
        logConsumer.accept("Output directory: " + outputDir.getAbsolutePath());

        String containerName = null;

        try {
            logConsumer.accept("Preparing Docker environment for CodeQL analysis...");
            containerName = codeQLDockerAnalysisRunner.prepareAnalysisEnvironment(projectDir, qlFile, outputDir, logConsumer);
            logConsumer.accept("✅ Docker environment ready. Container: " + containerName);

            logConsumer.accept("🔍 Checking if CodeQL database already exists in volume...");

            if (dockerContainerManager.isMountedVolumeInContainerEmpty(codeQLDockerConfig.getContainerName(), codeQLDockerConfig.getContainerBasePath(),
                    codeQLDockerConfig.getContainerDbPath())) {
                logConsumer.accept("📦 No existing database found. Creating new CodeQL database...");
                codeQLCliExecutor.createDatabase(
                        containerName,
                        codeQLDockerConfig.getContainerProjectPath(),
                        codeQLDockerConfig.getContainerDbPath(),
                        "java",
                        logConsumer);
                logConsumer.accept("✅ CodeQL database created successfully.");
            } else {
                logConsumer.accept("✅ CodeQL database already exists: " + codeQLDockerConfig.getDbVolumeName());
            }

            logConsumer.accept("Running CodeQL query inside Docker container...");
            codeQLCliExecutor.runQuery(
                    containerName,
                    dockerConfig.getContainerQueryPath(),
                    dockerConfig.getContainerDbPath(),
                    dockerConfig.getContainerResultPath(),
                    dockerConfig.getContainerQueryDir(),
                    logConsumer);

            File bqrsFile = pathProvider.getBQRSFile(outputDir);
            logConsumer.accept("CodeQL analysis complete. BQRS file generated: " + bqrsFile.getAbsolutePath());

            File rawJsonFile = pathProvider.getRawJsonFile(outputDir);
            if (rawJsonFile.exists()) {
                Files.delete(rawJsonFile.toPath());
                logConsumer.accept("Deleted existing raw JSON file: " + rawJsonFile.getAbsolutePath());
            }

            logConsumer.accept("Decoding BQRS results to raw JSON...");
            codeQLCliExecutor.decodeResultsInContainer(
                    containerName,
                    dockerConfig.getContainerResultPath(),
                    pathProvider.getContainerRawJsonPath(),
                    logConsumer);
            logConsumer.accept("✅ Raw CodeQL JSON: " + rawJsonFile.getAbsolutePath());

            logConsumer.accept("Processing raw JSON to clean JSON...");
            File cleanJsonFile = resultProcessor.extractCleanJsonFromTuples(rawJsonFile);
            logConsumer.accept("Extracted clean JSON objects.");

            File finalJsonFile = pathProvider.getFinalJsonFile(outputDir);
            if (finalJsonFile.exists()) {
                Files.delete(finalJsonFile.toPath());
                logConsumer.accept("Deleted existing final JSON file: " + finalJsonFile.getAbsolutePath());
            }

            Files.move(cleanJsonFile.toPath(), finalJsonFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logConsumer.accept("✅ Final clean JSON output: " + finalJsonFile.getAbsolutePath());

            logConsumer.accept("Analyzing results and generating statistics...");
            CodeQLResultProcessor.ProcessingStats stats = resultProcessor.analyzeResults(finalJsonFile);
            logConsumer.accept("✅ Analysis complete. Statistics:" + stats.toString());
            logConsumer.accept("✅ Code conversion process completed.");
        } catch (Exception e) {
            logConsumer.accept("❌ Error during code conversion: " + e.getMessage());
            throw e;
        }
    }

    public SseEmitter convertRepositoryWithSse(String repoUrl) {
        LoggableTask conversionTask = logger -> convertRepository(repoUrl, logger);

        return sseLogExecutor.streamConversionLogs(conversionTask);
    }
}
