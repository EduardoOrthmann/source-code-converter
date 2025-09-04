package tsystems.janus.sourcecodeconverter.domain.model;

public class Sink {
    public String filePath;

    public Sink(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
