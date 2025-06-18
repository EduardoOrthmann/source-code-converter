package tsystems.janus.sourcecodeconverter.domain.model;

public class Sink {
    public String filePath;
    public String methodName;
    public int approximateEndLine;

    public Sink(String filePath, String methodName) {
        this.filePath = filePath;
        this.methodName = methodName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int getApproximateEndLine() {
        return approximateEndLine;
    }

    public void setApproximateEndLine(int approximateEndLine) {
        this.approximateEndLine = approximateEndLine;
    }
}
