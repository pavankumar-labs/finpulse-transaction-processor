package com.finpulse.fraud.rule;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DormantReactivationRule implements FraudRule {

    private static final int MIN_HISTORY_REQUIRED = 30;

    @Value("${fraud.dormant-reactivation.dormancy-days}")
    private long dormancyDays;

    @Override
    public String ruleCode() {
        return "DORMANT_REACTIVATION";
    }

    @Override
    public RuleResult evaluate(String senderAccount, AnalysisContext context){

        List<Transaction> priorHistory = context.senderHistoryExcludingToday(senderAccount);

        if (priorHistory.size() < MIN_HISTORY_REQUIRED) {
            return notTriggered();
        }

        List<Transaction> todayTransactions = context.transactionsInFileForSender(senderAccount);
        if (todayTransactions.isEmpty()) {
            return notTriggered();
        }

        LocalDateTime lastPriorTransactionTime = priorHistory.get(0).getTransactionTime();

        LocalDateTime reactivationTime = todayTransactions.stream()
                .map(Transaction::getTransactionTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        long gapDays = ChronoUnit.DAYS.between(lastPriorTransactionTime, reactivationTime);

        if (gapDays < dormancyDays) {
            return notTriggered();
        }

        double mean = calculateMean(priorHistory);
        double worstRatio = 0;

        for (Transaction txn : todayTransactions) {
            double ratio = txn.getAmount().doubleValue() / mean;
            if (ratio > worstRatio) {
                worstRatio = ratio;
            }
        }

        return RuleResult.builder()
                .ruleCode(ruleCode())
                .triggered(true)
                .score(scoreFor(worstRatio))
                .reason(reasonFor(worstRatio, gapDays))
                .build();

    }

    private RuleResult notTriggered() {
        return RuleResult.builder().ruleCode(ruleCode()).triggered(false).build();
    }

    private double calculateMean(List<Transaction> history) {
        return history.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0.0);
    }

    private int scoreFor(double ratio) {
        if (ratio >= 5) {
            return 30;
        } else if (ratio >= 2) {
            return 20;
        } else {
            return 10;
        }
    }

    private String reasonFor(double ratio, long gapDays) {
        if (ratio >= 5) {
            return String.format(
                    "High severity — account reactivated after %d days of inactivity with an extremely large transaction amount.",
                    gapDays);
        } else if (ratio >= 2) {
            return String.format(
                    "Moderate severity — account reactivated after %d days of inactivity with an unusually large transaction amount.",
                    gapDays);
        } else {
            return String.format(
                    "Low severity — account reactivated after %d days of inactivity with a typical transaction amount.",
                    gapDays);
        }
    }


}
