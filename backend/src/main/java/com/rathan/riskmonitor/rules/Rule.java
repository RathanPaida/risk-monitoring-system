package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.transaction.Transaction;

import java.util.Optional;

public interface Rule {
    String getName();
    Optional<RuleHit> evaluate(Transaction transaction, RuleContext context);
}
