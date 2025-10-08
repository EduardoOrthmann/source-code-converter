package tsystems.janus.sourcecodeconverter.infrastructure.codeQL;

import org.springframework.stereotype.Component;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerContainerManager;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Component
public class CodeQLRunner {

    private final DockerContainerManager containerManager;

    public CodeQLRunner(DockerContainerManager containerManager) {
        this.containerManager = containerManager;
    }

    public void createDatabase(String containerName, String projectPathInContainer, String dbPathInContainer, String language, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("Running CodeQL database creation for language " + language + " in container " + containerName + "...");
        containerManager.executeCommandInContainer(
                containerName,
                null,
                List.of("bash", "-c",
                        "rm -rf ~/.codeql/packages/codeql/java-all/7.3.0/ext/ && " +
                                "codeql database create " + dbPathInContainer +
                                " --language=" + language +
                                " --source-root=" + projectPathInContainer +
//                                " --command=/opt/custom_build.sh" +
                                " --overwrite"
                ),
                logConsumer
        );
        logConsumer.accept("✅ CodeQL database created at " + dbPathInContainer + " in container " + containerName);
    }

    public void runQuery(String containerName, String qlFilePathInContainer, String dbPathInContainer, String resultPathInContainer, String queryDirInContainer, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("✅ CodeQL query executed successfully in container " + containerName + ". Results at " + resultPathInContainer + ".");
        containerManager.executeCommandInContainer(
                containerName,
                null,
                List.of("bash", "-c",
                        "cd " + queryDirInContainer + " && " +
                                "codeql pack install && " +
                                "codeql query run " + qlFilePathInContainer +
                                " --database=" + dbPathInContainer +
                                " --output=" + resultPathInContainer
                ),
                logConsumer
        );
        logConsumer.accept("✅ CodeQL query executed successfully in container " + containerName + ". Results at " + resultPathInContainer + ".");
    }

    public void decodeResultsInContainer(String containerName, String bqrsPathInContainer, String jsonOutputPathInContainer, Consumer<String> logConsumer) throws IOException, InterruptedException {
        logConsumer.accept("Decoding BQRS results from '" + bqrsPathInContainer + "' to JSON '" + jsonOutputPathInContainer + "' in container '" + containerName + "'...");
        containerManager.executeCommandInContainer(
                containerName,
                null,
                List.of(
                        "codeql", "bqrs", "decode",
                        "--format=json",
                        "--output", jsonOutputPathInContainer,
                        bqrsPathInContainer
                ),
                logConsumer
        );
        logConsumer.accept("✅ BQRS results decoded to JSON at " + jsonOutputPathInContainer + " in container " + containerName + ".");
    }
}
