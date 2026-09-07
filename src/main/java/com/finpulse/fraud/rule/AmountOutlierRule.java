package com.finpulse.fraud.rule;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AmountOutlierRule implements FraudRule {

    private static final int MIN_HISTORY_REQUIRED = 30;

    @Value("${fraud.amount-outlier.zscore-threshold}")
    private double zScoreThreshold;

    @Override
    public String ruleCode() {
        return "AMOUNT_OUTLIER";
    }

    @Override
    public RuleResult evaluate(String senderAccount, AnalysisContext context){

        List<Transaction> baseline = context.senderHistoryExcludingToday(senderAccount);
        if(baseline.size() <MIN_HISTORY_REQUIRED){
            return notTriggered();
        }

        double mean = calculateMean(baseline);
        double stdDev = calculateStdDev(baseline, mean);

        if (stdDev == 0) {
            return notTriggered();
        }

        List<Transaction> todaysTransactions = context.transactionsInFileForSender(senderAccount);

        List<Map<String, Object>> flaggedTransactions = new ArrayList<>();
        double worstZScore = 0;

        for(Transaction txn:todaysTransactions){
            double zScore= (txn.getAmount().doubleValue()-mean)/stdDev;
            if(Math.abs(zScore)>= zScoreThreshold){
                Map<String,Object> entry= new HashMap<>();
                entry.put("transaction",txn.getTransactionId());
                entry.put("amount",txn.getAmount());
                flaggedTransactions.add(entry);

                if (Math.abs(zScore) > Math.abs(worstZScore)) {
                    worstZScore = zScore;
                }
            }
        }

        if (flaggedTransactions.isEmpty()) {
            return notTriggered();
        }

        double absZScore = Math.abs(worstZScore);

        Map<String, Object> evidence = new HashMap<>();
        evidence.put("flaggedTransactions", flaggedTransactions);

        return RuleResult.builder()
                .ruleCode(ruleCode())
                .triggered(true)
                .score(scoreFor(absZScore))
                .reason(reasonFor(absZScore))
                .evidence(evidence)
                .build();

    }

    private RuleResult notTriggered(){
        return RuleResult.builder()
                .triggered(false)
                .ruleCode(ruleCode())
                .build();
    }


    private double calculateMean(List<Transaction> history) {
        return history.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0.0);
    }

    private double calculateStdDev(List<Transaction> history, double mean) {
        double sumSquaredDiffs = history.stream()
                .mapToDouble(t -> Math.pow(t.getAmount().doubleValue() - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiffs / history.size());
    }


    private int scoreFor(double zScore) {
        if (zScore >= 7) {
            return 30;
        } else if (zScore >= 5) {
            return 20;
        } else {
            return 10;
        }
    }

    private String reasonFor(double zScore) {
        if (zScore >= 7) {
            return "High severity — extremely unusual transaction amount for this account.";
        } else if (zScore >= 5) {
            return "Moderate severity — highly unusual transaction amount for this account.";
        } else {
            return "Low severity — unusual transaction amount for this account.";
        }
    }
}
