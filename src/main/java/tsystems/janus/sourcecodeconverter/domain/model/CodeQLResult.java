package tsystems.janus.sourcecodeconverter.domain.model;

public class CodeQLResult {
    private String id;
    private String type;
    private FileInfo file;
    private LocationInfo location;
    private String extractedSql;
    private CodeContext codeContext;
    private String methodParameters;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FileInfo getFile() {
        return file;
    }

    public void setFile(FileInfo file) {
        this.file = file;
    }

    public LocationInfo getLocation() {
        return location;
    }

    public void setLocation(LocationInfo location) {
        this.location = location;
    }

    public String getExtractedSql() {
        return extractedSql;
    }

    public void setExtractedSql(String extractedSql) {
        this.extractedSql = extractedSql;
    }

    public CodeContext getCodeContext() {
        return codeContext;
    }

    public void setCodeContext(CodeContext codeContext) {
        this.codeContext = codeContext;
    }

    public String getMethodParameters() {
        return methodParameters;
    }

    public void setMethodParameters(String methodParameters) {
        this.methodParameters = methodParameters;
    }
}
