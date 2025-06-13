package tsystems.janus.sourcecodeconverter.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CodeQLResult {
    private String file;
    @JsonProperty("class")
    private String clazz;
    private MethodInfo method;

    public String getFile() {
        return file;
    }

    public String getClazz() {
        return clazz;
    }

    public MethodInfo getMethod() {
        return method;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public void setMethod(MethodInfo method) {
        this.method = method;
    }
}
