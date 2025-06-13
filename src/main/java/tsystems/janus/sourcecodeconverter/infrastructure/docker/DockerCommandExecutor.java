package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DockerCommandExecutor {

    public void execute(List<String> command, Consumer<String> logConsumer) throws IOException, InterruptedException {
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

    public String executeAndCaptureOutput(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
        return output.toString().trim();
    }
}
