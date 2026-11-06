package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class NewDeviceRule implements Rule {

    public static final String NAME = "NewDeviceRule";
    private final RuleProperties properties;

    public NewDeviceRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction transaction, RuleContext context) {
        double minLow = properties.getNewDevice().getMinAmountLow();
        double minMedium = properties.getNewDevice().getMinAmountMedium();

        BigDecimal amount = transaction.getAmount();
        if (amount == null || amount.doubleValue() < minLow) {
            return Optional.empty();
        }

        long priorUsage = context.countPriorAccountDeviceUsage(
                transaction.getAccount().getId(),
                transaction.getDevice().getId(),
                transaction.getTxnTime()
        );

        if (priorUsage == 0) {
            if (amount.doubleValue() >= minMedium) {
                String reason = String.format("New device %d on account %d with elevated amount $%.2f (threshold >= $%.2f)",
                        transaction.getDevice().getId(), transaction.getAccount().getId(), amount.doubleValue(), minMedium);
                return Optional.of(new RuleHit(NAME, AlertSeverity.MEDIUM, reason));
            } else {
                String reason = String.format("New device %d on account %d with amount $%.2f (threshold >= $%.2f)",
                        transaction.getDevice().getId(), transaction.getAccount().getId(), amount.doubleValue(), minLow);
                return Optional.of(new RuleHit(NAME, AlertSeverity.LOW, reason));
            }
        }

        return Optional.empty();
    }
}
