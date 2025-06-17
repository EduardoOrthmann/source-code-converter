package tsystems.janus.sourcecodeconverter.domain.model;

import java.util.List;

public class CodeContext {
    private String containingClass;
    private String containingMethod;
    private List<VariableBindings> variableBindings;

    public String getContainingClass() {
        return containingClass;
    }

    public void setContainingClass(String containingClass) {
        this.containingClass = containingClass;
    }

    public String getContainingMethod() {
        return containingMethod;
    }

    public void setContainingMethod(String containingMethod) {
        this.containingMethod = containingMethod;
    }

    public List<VariableBindings> getVariableBindings() {
        return variableBindings;
    }

    public void setVariableBindings(List<VariableBindings> variableBindings) {
        this.variableBindings = variableBindings;
    }
}
