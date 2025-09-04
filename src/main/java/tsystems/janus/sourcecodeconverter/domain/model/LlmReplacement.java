package tsystems.janus.sourcecodeconverter.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmReplacement {
    private ConversionUnit.Location location;
    private String convertedCode;

    public ConversionUnit.Location getLocation() {
        return location;
    }

    public void setLocation(ConversionUnit.Location location) {
        this.location = location;
    }

    public String getConvertedCode() {
        return convertedCode;
    }

    public void setConvertedCode(String convertedCode) {
        this.convertedCode = convertedCode;
    }
}
