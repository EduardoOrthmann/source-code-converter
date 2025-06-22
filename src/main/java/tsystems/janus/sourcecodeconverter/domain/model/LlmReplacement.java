package tsystems.janus.sourcecodeconverter.domain.model;

public class LlmReplacement {
    private String blockId;
    private String convertedCode;

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getConvertedCode() {
        return convertedCode;
    }

    public void setConvertedCode(String convertedCode) {
        this.convertedCode = convertedCode;
    }
}
