package tsystems.janus.sourcecodeconverter.infrastructure.codeQL;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class CodeQLRunner {

    private static final String CODEQL_CMD = "codeql";

    public File createDatabase(File projectDir) throws IOException, InterruptedException {
        File dbDir = new File(projectDir.getParentFile(), "codeql-db-" + UUID.randomUUID());

        List<String> command = List.of(
                CODEQL_CMD, "database", "create", dbDir.getAbsolutePath(),
                "--language=java",
                "--source-root", projectDir.getAbsolutePath()
        );

        runCommand(command, projectDir);
        return dbDir;
    }

    public File runQuery(File dbDir, File qlFile) throws IOException, InterruptedException {
        File resultFile = new File(dbDir.getParentFile(), "results.bqrs");

        List<String> command = List.of(
                CODEQL_CMD, "query", "run", qlFile.getAbsolutePath(),
                "--database", dbDir.getAbsolutePath(),
                "--output", resultFile.getAbsolutePath()
        );

        runCommand(command, dbDir);
        return resultFile;
    }

    public File decodeResultsToFile(File resultFile) throws IOException, InterruptedException {
        File outputCsv = new File(resultFile.getParentFile(), "raw-results.json");

        List<String> command = List.of(
                CODEQL_CMD, "bqrs", "decode",
                "--format=json",
                "--output", outputCsv.getAbsolutePath(),
                resultFile.getAbsolutePath()
        );

        runCommand(command, resultFile.getParentFile());
        return outputCsv;
    }

    private void runCommand(List<String> command, File workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("CodeQL command failed: " + String.join(" ", command));
        }
    }
}
