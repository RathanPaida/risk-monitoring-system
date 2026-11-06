package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class UnusualAmountRule implements Rule {

    public static final String NAME = "UnusualAmountRule";
    private final RuleProperties properties;

    public UnusualAmountRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction transaction, RuleContext context) {
        double minAmount = properties.getUnusualAmount().getMinAmount();
        double multiplierMedium = properties.getUnusualAmount().getMultiplierMedium();
        double multiplierHigh = properties.getUnusualAmount().getMultiplierHigh();
        int trailingDays = properties.getUnusualAmount().getTrailingDays();

        BigDecimal amount = transaction.getAmount();
        if (amount == null || amount.doubleValue() < minAmount) {
            return Optional.empty();
        }

        Instant start = transaction.getTxnTime().minus(trailingDays, ChronoUnit.DAYS);
        Double avgAmount = context.getTrailingAverageAmount(transaction.getAccount().getId(), start, transaction.getTxnTime());

        if (avgAmount == null || avgAmount <= 0.0) {
            return Optional.empty();
        }

        double ratio = amount.doubleValue() / avgAmount;
        if (ratio > multiplierHigh) {
            String reason = String.format("Extremely unusual amount: $%.2f is %.1fx trailing %d-day average of $%.2f (threshold > %.1fx, min $%.2f)",
                    amount.doubleValue(), ratio, trailingDays, avgAmount, multiplierHigh, minAmount);
            return Optional.of(new RuleHit(NAME, AlertSeverity.HIGH, reason));
        } else if (ratio > multiplierMedium) {
            String reason = String.format("Unusual amount: $%.2f is %.1fx trailing %d-day average of $%.2f (threshold > %.1fx, min $%.2f)",
                    amount.doubleValue(), ratio, trailingDays, avgAmount, multiplierMedium, minAmount);
            return Optional.of(new RuleHit(NAME, AlertSeverity.MEDIUM, reason));
        }

        return Optional.empty();
    }
}
