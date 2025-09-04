package tsystems.janus.sourcecodeconverter.infrastructure.buildTesting;

import org.springframework.stereotype.Service;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.CodeQLDockerConfig;
import tsystems.janus.sourcecodeconverter.infrastructure.docker.DockerContainerManager;

import java.util.List;

@Service
public class BuildTestService {

    private final DockerContainerManager containerManager;
    private final CodeQLDockerConfig dockerConfig;

    public BuildTestService(DockerContainerManager containerManager, CodeQLDockerConfig dockerConfig) {
        this.containerManager = containerManager;
        this.dockerConfig = dockerConfig;
    }

    public boolean testSingleFile(String filePath) {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        String relativePath = filePath.replace(workDir + "/", "");

        try {
            System.out.println("==========================================================");
            System.out.println("🔬 INSPECTING FILE CONTENT INSIDE CONTAINER BEFORE BUILD  🔬");
            System.out.println("File: " + relativePath);
            System.out.println("----------------------------------------------------------");
            // This command will print the file's true content as the container sees it
            containerManager.executeCommandInContainer(containerName, workDir, List.of("cat", relativePath), System.out::println);
            System.out.println("==========================================================");

            System.out.println("🧪 Starting Maven compilation test for: " + relativePath);
            String mavenCommand = String.format("mvn compiler:compile -Dmaven.compiler.includes=%s", relativePath);

            System.out.println("Executing command: " + mavenCommand + " for file: " + relativePath);

            containerManager.executeCommandInContainer(containerName, workDir, List.of("bash", "-c", mavenCommand), System.out::println);
            System.out.println("✅ Maven compilation successful for: " + relativePath);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Maven compilation failed for: " + relativePath);
            return false;
        }
    }

    public boolean testFullBuild() {
        String containerName = dockerConfig.getContainerName();
        String workDir = dockerConfig.getContainerProjectPath();
        try {
            System.out.println("🧪 Starting full Maven project compilation...");
            containerManager.executeCommandInContainer(containerName, workDir, List.of("mvn", "clean", "compile"), System.out::println);
            System.out.println("✅ Full Maven compilation successful.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Full Maven compilation failed.");
            return false;
        }
    }
}
