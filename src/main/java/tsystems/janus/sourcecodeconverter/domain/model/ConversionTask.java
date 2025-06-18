package tsystems.janus.sourcecodeconverter.domain.model;

import java.util.List;
import java.util.Map;

public class ConversionTask {
    private Sink sink;
    private List<ConstructionStep> constructionTrace;
    private String inferredPreConversionSql;
    private Map<String, Object> supplementalContext;

    public ConversionTask(Sink sink, List<ConstructionStep> constructionTrace, String inferredPreConversionSql, Map<String, Object> supplementalContext) {
        this.sink = sink;
        this.constructionTrace = constructionTrace;
        this.inferredPreConversionSql = inferredPreConversionSql;
        this.supplementalContext = supplementalContext;
    }

    public ConversionTask() {

    }

    public Sink getSink() {
        return sink;
    }

    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public List<ConstructionStep> getConstructionTrace() {
        return constructionTrace;
    }

    public void setConstructionTrace(List<ConstructionStep> constructionTrace) {
        this.constructionTrace = constructionTrace;
    }

    public String getInferredPreConversionSql() {
        return inferredPreConversionSql;
    }

    public void setInferredPreConversionSql(String inferredPreConversionSql) {
        this.inferredPreConversionSql = inferredPreConversionSql;
    }

    public Map<String, Object> getSupplementalContext() {
        return supplementalContext;
    }

    public void setSupplementalContext(Map<String, Object> supplementalContext) {
        this.supplementalContext = supplementalContext;
    }
}
