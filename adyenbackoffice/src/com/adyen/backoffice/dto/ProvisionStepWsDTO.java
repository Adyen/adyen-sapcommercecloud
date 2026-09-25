package com.adyen.backoffice.dto;

/**
 * One step of the wizard's run against Adyen.
 *
 * <p>Reported individually because the chain creates real, non-reversible things at Adyen: if a later step
 * fails the earlier ones have still happened, and the merchant needs to see which.</p>
 */
public class ProvisionStepWsDTO {

    private String name;
    private boolean done;
    private String detail;

    public ProvisionStepWsDTO() {
        // for deserialization
    }

    public ProvisionStepWsDTO(final String name, final boolean done, final String detail) {
        this.name = name;
        this.done = done;
        this.detail = detail;
    }

    public String getName() { return name; }

    public void setName(final String name) { this.name = name; }

    public boolean isDone() { return done; }

    public void setDone(final boolean done) { this.done = done; }

    public String getDetail() { return detail; }

    public void setDetail(final String detail) { this.detail = detail; }
}
