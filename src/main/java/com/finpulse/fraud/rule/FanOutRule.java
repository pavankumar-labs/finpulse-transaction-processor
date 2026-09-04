package com.finpulse.fraud.rule;


import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FanOutRule implements FraudRule{

    private static final int MIN_HISTORY_REQUIRED = 30;
    private static final int RECENT_HISTORY_SAMPLE = 50;
    private static final int HISTORY_TRIM_HOURS = 48;

    private static final int ONE_HOUR_FLOOR = 5;
    private static final int ONE_HOUR_BUFFER = 1;

    private static final int SIX_HOUR_FLOOR = 8;
    private static final int SIX_HOUR_BUFFER = 2;

    private static final int TWENTY_FOUR_HOUR_FLOOR = 10;
    private static final int TWENTY_FOUR_HOUR_BUFFER = 3;

    private static final int SYSTEM_DAILY_CEILING = 20;

    @Override
    public String ruleCode() {
        return "FAN_OUT";
    }

    @Override
    public RuleResult evaluate(String senderAccount, AnalysisContext context){

        List<Transaction> todaysTransactions = context.transactionsInFileForSender(senderAccount);
        if (todaysTransactions.isEmpty()) {
            return notTriggered();
        }

        long totalDistinctToday = todaysTransactions.stream()
                .map(Transaction::getReceiverAccount)
                .distinct()
                .count();

        if (totalDistinctToday < ONE_HOUR_FLOOR) {
            return notTriggered();
        }

        boolean check6h = totalDistinctToday >= SIX_HOUR_FLOOR;
        boolean check24h = totalDistinctToday >= TWENTY_FOUR_HOUR_FLOOR;

        LocalDateTime latestAnchor = todaysTransactions.stream()
                .map(Transaction::getTransactionTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();

        LocalDateTime historyFloor = latestAnchor.minusHours(HISTORY_TRIM_HOURS);

        List<Transaction> recentHistory = context.senderHistory(senderAccount).stream()
                .filter(t -> !t.getTransactionTime().isBefore(historyFloor))
                .toList();

        List<Transaction> priorHistory = context.senderHistoryExcludingToday(senderAccount);

        boolean hasEnoughHistory = priorHistory.size() >= MIN_HISTORY_REQUIRED;
        List<Transaction> recentSample = hasEnoughHistory
                ? priorHistory.stream()
                .limit(RECENT_HISTORY_SAMPLE)
                .sorted(Comparator.comparing(Transaction::getTransactionTime))
                .toList()
                : List.of();

        RuleResult best = evaluateWindow("1h", recentHistory, todaysTransactions, recentSample,
                hasEnoughHistory, Duration.ofHours(1), ONE_HOUR_FLOOR, ONE_HOUR_BUFFER);

        if (check6h) {
            RuleResult result = evaluateWindow("6h", recentHistory, todaysTransactions, recentSample,
                    hasEnoughHistory, Duration.ofHours(6), SIX_HOUR_FLOOR, SIX_HOUR_BUFFER);
            best = betterOf(best, result);
        }
        if (check24h) {
            RuleResult result = evaluateWindow("24h", recentHistory, todaysTransactions, recentSample,
                    hasEnoughHistory, Duration.ofHours(24), TWENTY_FOUR_HOUR_FLOOR, TWENTY_FOUR_HOUR_BUFFER);
            best = betterOf(best, result);
        }

        return best != null ? best : notTriggered();


    }
    private RuleResult betterOf(RuleResult a, RuleResult b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.getScore() > a.getScore() ? b : a;
    }

    private RuleResult evaluateWindow(String windowLabel, List<Transaction> recentHistory,
                                      List<Transaction> todaysTransactions, List<Transaction> recentSample,
                                      boolean hasEnoughHistory, Duration window, int floor, int buffer) {

        int threshold = computeThreshold(recentSample, hasEnoughHistory, window, floor, buffer);
        int actual = peakDistinctReceiversInWindow(recentHistory, todaysTransactions, window);

        if (actual < threshold) {
            return null;
        }
        return trigger(windowLabel, actual, threshold);
    }


    private int computeThreshold(List<Transaction> recentSample, boolean hasEnoughHistory,
                                 Duration window, int floor, int buffer) {
        if (!hasEnoughHistory) {
            return floor;
        }
        int peak = historicalDistinctPeak(recentSample, window);
        return clamp(peak + buffer, floor, SYSTEM_DAILY_CEILING);
    }
    private int clamp(int value, int floor, int ceiling) {
        return Math.max(floor, Math.min(value, ceiling));
    }

    private int peakDistinctReceiversInWindow(List<Transaction> recentHistory,
                                              List<Transaction> todaysTransactions, Duration window) {
        int maxDistinct = 0;
        for (Transaction anchorTxn : todaysTransactions) {
            LocalDateTime anchor = anchorTxn.getTransactionTime();
            LocalDateTime cutoff = anchor.minus(window);

            int distinctCount = (int) recentHistory.stream()
                    .filter(t -> !t.getTransactionTime().isBefore(cutoff) && !t.getTransactionTime().isAfter(anchor))
                    .map(Transaction::getReceiverAccount)
                    .distinct()
                    .count();

            if (distinctCount > maxDistinct) {
                maxDistinct = distinctCount;
            }
        }
        return maxDistinct;
    }

    private RuleResult trigger(String window, int actualCount, int threshold) {
        double ratio = (double) actualCount / threshold;

        int score;
        String severity;
        if (ratio >= 2.0) {
            score = 30;
            severity = "High";
        } else if (ratio >= 1.4) {
            score = 20;
            severity = "Moderate";
        } else {
            score = 10;
            severity = "Low";
        }

        String reason = String.format(
                "%s severity — %d distinct receivers in the last %s, exceeding this account's expected threshold of %d.",
                severity, actualCount, window, threshold);

        return RuleResult.builder()
                .ruleCode(ruleCode())
                .triggered(true)
                .score(score)
                .reason(reason)
                .build();
    }



    private int historicalDistinctPeak(List<Transaction> ascendingHistory, Duration window) {
        int maxDistinct = 0;
        int left = 0;
        Map<String, Integer> receiverCounts = new HashMap<>();

        for (int right = 0; right < ascendingHistory.size(); right++) {
            Transaction rightTxn = ascendingHistory.get(right);
            receiverCounts.merge(rightTxn.getReceiverAccount(), 1, Integer::sum);

            while (ascendingHistory.get(left).getTransactionTime()
                    .isBefore(rightTxn.getTransactionTime().minus(window))) {
                Transaction leftTxn = ascendingHistory.get(left);
                receiverCounts.computeIfPresent(leftTxn.getReceiverAccount(), (k, v) -> v > 1 ? v - 1 : null);
                left++;
            }

            int distinct = receiverCounts.size();
            if (distinct > maxDistinct) {
                maxDistinct = distinct;
            }
        }
        return maxDistinct;
    }

    private RuleResult notTriggered() {
        return RuleResult.builder().ruleCode(ruleCode()).triggered(false).build();
    }

}
