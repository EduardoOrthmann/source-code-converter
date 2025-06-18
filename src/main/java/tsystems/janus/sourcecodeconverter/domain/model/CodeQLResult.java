package tsystems.janus.sourcecodeconverter.domain.model;

public class CodeQLResult {
    private String id;
    private String path;
    private int startLine;
    private String methodName;
    private String code;
    private String sourceExpressionType;
    private String className;
    private String type;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSourceExpressionType() {
        return sourceExpressionType;
    }

    public void setSourceExpressionType(String sourceExpressionType) {
        this.sourceExpressionType = sourceExpressionType;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
