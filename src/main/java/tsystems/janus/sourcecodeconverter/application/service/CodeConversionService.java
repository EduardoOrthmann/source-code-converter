package tsystems.janus.sourcecodeconverter.application.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;
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
import tsystems.janus.sourcecodeconverter.infrastructure.util.ZipExtractor;

@Service
public class CodeConversionService {

    private final GitCloner repositoryCloner;
    private final CodeQLDockerAnalysisRunner codeQLDockerAnalysisRunner;
    private final CodeQLRunner codeQLCliExecutor;
    private final DockerContainerManager dockerContainerManager;
    private final CodeQLResultProcessor resultProcessor;
    private final AnalysisPathProvider pathProvider;
    private final CodeQLDockerConfig dockerConfig;
    private final SseLogExecutor sseLogExecutor;
    private final ZipExtractor zipExtractor;

    public CodeConversionService(GitCloner repositoryCloner, CodeQLDockerAnalysisRunner codeQLDockerAnalysisRunner,
                                 CodeQLRunner codeQLCliExecutor, DockerContainerManager dockerContainerManager,
                                 CodeQLResultProcessor resultProcessor, AnalysisPathProvider pathProvider, CodeQLDockerConfig dockerConfig,
                                 SseLogExecutor sseLogExecutor, ZipExtractor zipExtractor) {
        this.repositoryCloner = repositoryCloner;
        this.codeQLDockerAnalysisRunner = codeQLDockerAnalysisRunner;
        this.codeQLCliExecutor = codeQLCliExecutor;
        this.dockerContainerManager = dockerContainerManager;
        this.resultProcessor = resultProcessor;
        this.pathProvider = pathProvider;
        this.dockerConfig = dockerConfig;
        this.sseLogExecutor = sseLogExecutor;
        this.zipExtractor = zipExtractor;
    }

    private void startConversion(File projectDir, Consumer<String> logConsumer) throws Exception {
        System.out.println("I am at startConversion method");
        File qlFile = pathProvider.getQueryFile();
        logConsumer.accept("Found CodeQL query file: " + qlFile.getAbsolutePath());

        File outputDir = pathProvider.getOutputDirectory();
        logConsumer.accept("Output directory: " + outputDir.getAbsolutePath());

        String containerName = null;
        try {
            logConsumer.accept("Preparing Docker environment for CodeQL analysis...");
            System.out.println("Preparing Docker environment for CodeQL analysis...");
            containerName = codeQLDockerAnalysisRunner.prepareAnalysisEnvironment(projectDir, qlFile, outputDir, logConsumer);
            logConsumer.accept("✅ Docker environment ready. Container: " + containerName);
            System.out.println("✅ Docker environment ready. Container: " + containerName);

            logConsumer.accept("🔍 Checking if CodeQL database already exists in volume...");

            if (dockerContainerManager.isMountedVolumeInContainerEmpty(dockerConfig.getContainerName(), dockerConfig.getContainerBasePath(), dockerConfig.getContainerDbPath())) {
                logConsumer.accept("📦 No existing database found. Creating new CodeQL database...");
                codeQLCliExecutor.createDatabase(
                        containerName,
                        dockerConfig.getContainerProjectPath(),
                        dockerConfig.getContainerDbPath(),
                        "java",
                        System.out::println);
                logConsumer.accept("✅ CodeQL database created successfully.");
                System.out.println("✅ CodeQL database created successfully.");
            } else {
                logConsumer.accept("✅ CodeQL database already exists: " + dockerConfig.getDbVolumeName());
                System.out.println("✅ CodeQL database already exists: " + dockerConfig.getDbVolumeName());
            }

            System.out.println("Running CodeQL query inside Docker container...");
            logConsumer.accept("Running CodeQL query inside Docker container...");
            System.out.println("Running CodeQL query inside Docker container...");
            codeQLCliExecutor.runQuery(
                    containerName,
                    dockerConfig.getContainerQueryPath(),
                    dockerConfig.getContainerDbPath(),
                    dockerConfig.getContainerResultPath(),
                    dockerConfig.getContainerQueryDir(),
                    System.out::println);

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
                    System.out::println);
            logConsumer.accept("✅ Raw CodeQL JSON: " + rawJsonFile.getAbsolutePath());

            logConsumer.accept("Processing raw JSON to clean JSON...");
            System.out.println("Processing raw JSON to clean JSON...");
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
        LoggableTask conversionTask = logger -> {
            logger.accept("Starting code conversion for repository: " + repoUrl);
            File projectDir = repositoryCloner.cloneRepository(repoUrl);
            logger.accept("✅ Repository cloned to: " + projectDir.getAbsolutePath());
            startConversion(projectDir, logger);
        };
        return sseLogExecutor.streamConversionLogs(conversionTask);
    }

    public Map<String, Object> convertRepository(MultipartFile projectZip) throws Exception {
        final List<String> logs = new ArrayList<>();

        Consumer<String> logConsumer = logs::add;

        logConsumer.accept("Starting synchronous code conversion for uploaded ZIP file: " + projectZip.getOriginalFilename());
        System.out.println("Starting synchronous code conversion for uploaded ZIP file: " + projectZip.getOriginalFilename());

        File projectDir = zipExtractor.unzip(projectZip);
        logConsumer.accept("✅ ZIP file extracted to: " + projectDir.getAbsolutePath());

        startConversion(projectDir, logConsumer);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "Completed");
        result.put("message", "Conversion finished successfully.");
        result.put("logs", logs);

        return result;
    }
}
