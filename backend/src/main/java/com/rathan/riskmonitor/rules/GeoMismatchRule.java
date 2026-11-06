package com.rathan.riskmonitor.rules;

import com.rathan.riskmonitor.alert.AlertSeverity;
import com.rathan.riskmonitor.transaction.Customer;
import com.rathan.riskmonitor.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class GeoMismatchRule implements Rule {

    public static final String NAME = "GeoMismatchRule";
    private final RuleProperties properties;

    public GeoMismatchRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction transaction, RuleContext context) {
        Customer customer = context.getCustomer();

        // Check 1: Foreign transaction compared to customer home country
        if (customer != null && customer.getHomeCountry() != null) {
            if (!customer.getHomeCountry().equalsIgnoreCase(transaction.getCountry())) {
                String reason = String.format("Geo-mismatch: Transaction country '%s' differs from customer home country '%s'",
                        transaction.getCountry(), customer.getHomeCountry());
                return Optional.of(new RuleHit(NAME, AlertSeverity.MEDIUM, reason));
            }
        }

        // Check 2: Country hop within window (e.g. 1 hour)
        int hopWindowMinutes = properties.getGeoMismatch().getHopWindowMinutes();
        Optional<Transaction> prevTxnOpt = context.getPreviousTransaction(transaction.getAccount().getId(), transaction.getTxnTime());

        if (prevTxnOpt.isPresent()) {
            Transaction prevTxn = prevTxnOpt.get();
            long minutesDiff = ChronoUnit.MINUTES.between(prevTxn.getTxnTime(), transaction.getTxnTime());
            if (minutesDiff <= hopWindowMinutes && !prevTxn.getCountry().equalsIgnoreCase(transaction.getCountry())) {
                String reason = String.format("Sudden geo-hop: Country changed from '%s' to '%s' in %d minutes (window <= %d min)",
                        prevTxn.getCountry(), transaction.getCountry(), minutesDiff, hopWindowMinutes);
                return Optional.of(new RuleHit(NAME, AlertSeverity.MEDIUM, reason));
            }
        }

        return Optional.empty();
    }
}
