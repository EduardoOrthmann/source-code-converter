package tsystems.janus.sourcecodeconverter.infrastructure.docker;

import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@Component
public class AnalysisPathProvider {

    private static final String QUERY_RESOURCE_PATH = "queries/sql-detection.ql";
    private static final String OUTPUT_BASE_DIR = "src/main/resources/output";
    private static final String BQRS_FILENAME = "results.bqrs";
    private static final String RAW_JSON_FILENAME = "raw-results.json";
    private static final String FINAL_JSON_FILENAME = "results.json";
    private static final String CONTAINER_RAW_JSON_PATH = "/app/output/" + RAW_JSON_FILENAME;

    public File getQueryFile() throws URISyntaxException {
        URI uri = Objects.requireNonNull(getClass().getClassLoader().getResource(QUERY_RESOURCE_PATH)).toURI();
        return new File(uri);
    }

    public File getOutputDirectory() {
        File outputDir = new File(OUTPUT_BASE_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return outputDir;
    }

    public File getBQRSFile(File outputDir) {
        return new File(outputDir, BQRS_FILENAME);
    }

    public File getRawJsonFile(File outputDir) {
        return new File(outputDir, RAW_JSON_FILENAME);
    }

    public File getFinalJsonFile(File outputDir) {
        return new File(outputDir, FINAL_JSON_FILENAME);
    }

    public String getContainerRawJsonPath() {
        return CONTAINER_RAW_JSON_PATH;
    }
}
