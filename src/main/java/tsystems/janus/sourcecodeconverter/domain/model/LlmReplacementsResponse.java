package tsystems.janus.sourcecodeconverter.domain.model;

import java.util.List;

public class LlmReplacementsResponse {
    private String file;
    private String explanation;
    private List<LlmReplacement> replacements;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<LlmReplacement> getReplacements() {
        return replacements;
    }

    public void setReplacements(List<LlmReplacement> replacements) {
        this.replacements = replacements;
    }
}
