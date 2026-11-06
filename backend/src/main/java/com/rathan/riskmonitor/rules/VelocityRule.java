package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class VelocityRule implements Rule {

    public static final String NAME = "VelocityRule";
    private final RuleProperties properties;

    public VelocityRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction transaction, RuleContext context) {
        int windowMinutes = properties.getVelocity().getWindowMinutes();
        int thresholdMedium = properties.getVelocity().getThresholdMedium();
        int thresholdHigh = properties.getVelocity().getThresholdHigh();

        Instant windowStart = transaction.getTxnTime().minus(windowMinutes, ChronoUnit.MINUTES);
        long count = context.countTransactionsInWindow(transaction.getAccount().getId(), windowStart, transaction.getTxnTime());

        if (count > thresholdHigh) {
            String reason = String.format("High transaction velocity: %d transactions on account %d within %d minutes (threshold > %d)",
                    count, transaction.getAccount().getId(), windowMinutes, thresholdHigh);
            return Optional.of(new RuleHit(NAME, AlertSeverity.HIGH, reason));
        } else if (count > thresholdMedium) {
            String reason = String.format("Elevated transaction velocity: %d transactions on account %d within %d minutes (threshold > %d)",
                    count, transaction.getAccount().getId(), windowMinutes, thresholdMedium);
            return Optional.of(new RuleHit(NAME, AlertSeverity.MEDIUM, reason));
        }

        return Optional.empty();
    }
}
