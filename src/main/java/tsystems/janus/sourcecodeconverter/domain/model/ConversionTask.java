package tsystems.janus.sourcecodeconverter.domain.model;

import java.util.List;

public class ConversionTask {
    private Sink sink;
    private List<ConversionUnit> conversionUnits;

    public ConversionTask(Sink sink, List<ConversionUnit> conversionUnits) {
        this.sink = sink;
        this.conversionUnits = conversionUnits;
    }

    public ConversionTask() {

    }

    public Sink getSink() {
        return sink;
    }

    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public List<ConversionUnit> getConversionUnits() {
        return conversionUnits;
    }

    public void setConversionUnits(List<ConversionUnit> conversionUnits) {
        this.conversionUnits = conversionUnits;
    }
}
