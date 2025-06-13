package tsystems.janus.sourcecodeconverter.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SqlQueryInfo {
    private String type;
    @JsonProperty("query_string")
    private String queryString;
    @JsonProperty("line_start")
    private int lineStart;
    @JsonProperty("line_end")
    private int lineEnd;

    public String getType() {
        return type;
    }

    public String getQueryString() {
        return queryString;
    }

    public int getLineStart() {
        return lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public void setLineStart(int lineStart) {
        this.lineStart = lineStart;
    }

    public void setLineEnd(int lineEnd) {
        this.lineEnd = lineEnd;
    }
}
