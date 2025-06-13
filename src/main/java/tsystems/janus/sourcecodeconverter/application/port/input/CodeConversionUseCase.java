package tsystems.janus.sourcecodeconverter.application.port.input;

import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLResultProcessor;
import tsystems.janus.sourcecodeconverter.infrastructure.codeQL.CodeQLRunner;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerCodeQLAnalyzer;
import tsystems.janus.sourcecodeconverter.infrastructure.git.GitCloner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class CodeConversionUseCase {
    private final GitCloner gitCloner;
    private final DockerCodeQLAnalyzer dockerCodeQLAnalyzer;
    private final CodeQLRunner codeQLRunner;
    private final CodeQLResultProcessor resultProcessor;

    public CodeConversionUseCase(GitCloner gitCloner, DockerCodeQLAnalyzer dockerCodeQLAnalyzer, CodeQLRunner codeQLRunner, CodeQLResultProcessor resultProcessor) {
        this.gitCloner = gitCloner;
        this.dockerCodeQLAnalyzer = dockerCodeQLAnalyzer;
        this.codeQLRunner = codeQLRunner;
        this.resultProcessor = resultProcessor;
    }

    public void convertRepository(String repoUrl, Consumer<String> logConsumer) throws Exception {
        logConsumer.accept("Starting code conversion for repository: " + repoUrl);

        File projectDir = gitCloner.cloneRepository(repoUrl);
        logConsumer.accept("✅ Repository cloned to: " + projectDir.getAbsolutePath());

        File qlFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("queries/sql-detection.ql")).toURI());
        logConsumer.accept("Found CodeQL query file: " + qlFile.getAbsolutePath());

        File outputDir = new File("src/main/resources/output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            logConsumer.accept("Created output directory: " + outputDir.getAbsolutePath());
        } else {
            logConsumer.accept("Output directory already exists: " + outputDir.getAbsolutePath());
        }

        logConsumer.accept("Running CodeQL analysis with Docker...");
        dockerCodeQLAnalyzer.runCodeQLAnalysis(projectDir, qlFile, outputDir, logConsumer);
        File bqrsFile = new File(outputDir, "results.bqrs");
        logConsumer.accept("CodeQL analysis complete. BQRS file generated: " + bqrsFile.getAbsolutePath());

        File rawJsonFile = new File(outputDir, "raw-results.json");
        if (rawJsonFile.exists()) {
            rawJsonFile.delete();
            logConsumer.accept("Deleted existing raw JSON file: " + rawJsonFile.getAbsolutePath());
        }

        logConsumer.accept("Decoding BQRS results to raw JSON...");
        File generatedJson = codeQLRunner.decodeResultsToFile(bqrsFile);
        Files.move(generatedJson.toPath(), rawJsonFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logConsumer.accept("✅ Raw CodeQL JSON: " + rawJsonFile.getAbsolutePath());

        logConsumer.accept("Processing raw JSON to clean JSON...");
        File cleanJsonFile = resultProcessor.extractCleanJsonFromTuples(rawJsonFile);
        logConsumer.accept("Extracted clean JSON objects.");

        File finalJsonFile = new File(outputDir, "results.json");
        if (finalJsonFile.exists()) {
            finalJsonFile.delete();
            logConsumer.accept("Deleted existing final JSON file: " + finalJsonFile.getAbsolutePath());
        }

        Files.move(cleanJsonFile.toPath(), finalJsonFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logConsumer.accept("✅ Final clean JSON output: " + finalJsonFile.getAbsolutePath());

        logConsumer.accept("Analyzing results and generating statistics...");

        CodeQLResultProcessor.ProcessingStats stats = resultProcessor.analyzeResults(finalJsonFile);
        logConsumer.accept("Processing Stats:\n" +
                "  Total Queries: " + stats.totalQueries + "\n" +
                "  Static: " + stats.staticQueries + "\n" +
                "  Dynamic: " + stats.dynamicQueries + "\n" +
                "  Parameterized: " + stats.parameterizedQueries + "\n" +
                "  Files: " + stats.fileCount.size() + "\n" +
                "  Classes: " + stats.classCount.size());
        logConsumer.accept("✅ Code conversion process completed.");
    }
}
