package com.adyen.backoffice.dto;

import java.util.ArrayList;
import java.util.List;

/** What the wizard's run produced. Carries no secret: the values it created are stored, never returned. */
public class ProvisionReportWsDTO {

    private boolean complete;
    private List<ProvisionStepWsDTO> steps = new ArrayList<>();

    public boolean isComplete() { return complete; }

    public void setComplete(final boolean complete) { this.complete = complete; }

    public List<ProvisionStepWsDTO> getSteps() { return steps; }

    public void setSteps(final List<ProvisionStepWsDTO> steps) { this.steps = steps; }

    public void add(final String name, final boolean done, final String detail) {
        steps.add(new ProvisionStepWsDTO(name, done, detail));
    }
}
