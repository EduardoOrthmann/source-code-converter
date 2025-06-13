package tsystems.janus.sourcecodeconverter.domain.model;

public class MethodInfo {
    private String name;
    private int start_line;
    private int end_line;
    private String body;
    private SqlQueryInfo sql_query;

    public String getName() {
        return name;
    }

    public int getStart_line() {
        return start_line;
    }

    public int getEnd_line() {
        return end_line;
    }

    public String getBody() {
        return body;
    }

    public SqlQueryInfo getSql_query() {
        return sql_query;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStart_line(int start_line) {
        this.start_line = start_line;
    }

    public void setEnd_line(int end_line) {
        this.end_line = end_line;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setSql_query(SqlQueryInfo sql_query) {
        this.sql_query = sql_query;
    }
}
