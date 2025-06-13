package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class CodeQLDockerConfig {

    @Value("${codeql.docker.image-name:codeql-runner-image}")
    private String imageName;

    @Value("${codeql.docker.container-name:temp-code-converter}")
    private String containerName;

    @Value("${codeql.docker.dockerfile-dir:C:/Users/orthm/Documents/Projetos/t-systems/source-code-converter/src/codeql-docker}")
    private String dockerfileDir;

    @Value("${codeql.db.persist-volume:true}")
    private boolean persistDbVolume;

    @Value("${codeql.db.volume-name:codeql-test-db-volume}")
    private String dbVolumeName;

    private static final String CONTAINER_PROJECT_PATH = "/app/project";
    private static final String CONTAINER_QUERY_PATH = "/app/queries/sql-detection.ql";
    private static final String CONTAINER_DB_PATH = "/app/db";
    private static final String CONTAINER_RESULT_PATH = "/app/output/results.bqrs";
    private static final String CONTAINER_QUERY_DIR = "/app/queries";
    private static final String CONTAINER_OUTPUT_DIR = "/app/output";

    public String getImageName() {
        return imageName;
    }

    public String getContainerName() {
        return containerName;
    }

    public File getDockerfileDir() {
        return new File(dockerfileDir);
    }

    public String getContainerProjectPath() {
        return CONTAINER_PROJECT_PATH;
    }

    public String getContainerQueryPath() {
        return CONTAINER_QUERY_PATH;
    }

    public String getContainerDbPath() {
        return CONTAINER_DB_PATH;
    }

    public String getContainerResultPath() {
        return CONTAINER_RESULT_PATH;
    }

    public String getContainerQueryDir() {
        return CONTAINER_QUERY_DIR;
    }

    public String getContainerOutputDir() {
        return CONTAINER_OUTPUT_DIR;
    }

    public boolean isPersistDbVolume() {
        return persistDbVolume;
    }

    public String getDbVolumeName() {
        return dbVolumeName;
    }
}
