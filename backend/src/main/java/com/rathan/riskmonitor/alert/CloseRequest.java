package com.rathan.riskmonitor.alert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CloseRequest {

    @NotBlank(message = "outcome is required")
    @Pattern(regexp = "CONFIRMED|FALSE_POSITIVE", message = "outcome must be CONFIRMED or FALSE_POSITIVE")
    private String outcome;

    private String comment;

    public CloseRequest() {
    }

    public CloseRequest(String outcome, String comment) {
        this.outcome = outcome;
        this.comment = comment;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
