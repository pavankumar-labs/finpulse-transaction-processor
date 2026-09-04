package com.finpulse.fraud.rule;

import com.finpulse.entity.AccountType;
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
public class FanInRule implements FraudRule{

    private static final int MIN_HISTORY_REQUIRED = 30;
    private static final int RECENT_HISTORY_SAMPLE = 50;
    private static final int HISTORY_TRIM_HOURS = 48;

    private static final int PERSONAL_1H_FLOOR = 5;
    private static final int PERSONAL_6H_FLOOR = 8;
    private static final int PERSONAL_24H_FLOOR = 10;
    private static final int PERSONAL_1H_BUFFER = 2;
    private static final int PERSONAL_6H_BUFFER = 3;
    private static final double PERSONAL_24H_MULTIPLIER = 1.3;

    private static final int BUSINESS_1H_BUFFER = 3;
    private static final int BUSINESS_6H_BUFFER = 7;
    private static final double BUSINESS_24H_MULTIPLIER = 1.5;

    private static final int SYSTEM_DAILY_CEILING = 20;

    @Override
    public String ruleCode() {
        return "FAN_IN";
    }

    @Override
    public RuleResult evaluate(String receiverAccount, AnalysisContext context){

        List<Transaction> todaysTransactions = context.transactionsInFileForReceiver(receiverAccount);
        if (todaysTransactions.isEmpty()) {
            return notTriggered();
        }

        AccountType accountType = todaysTransactions.get(0).getReceiverAccountType();

        List<Transaction> priorHistory = context.receiverHistoryExcludingToday(receiverAccount);
        boolean hasEnoughHistory = priorHistory.size() >= MIN_HISTORY_REQUIRED;

        long totalDistinctToday = todaysTransactions.stream()
                .map(Transaction::getSenderAccount)
                .distinct()
                .count();

        boolean thresholdIsUncapped = accountType == AccountType.BUSINESS && hasEnoughHistory;
        if (!thresholdIsUncapped && totalDistinctToday < PERSONAL_1H_FLOOR) {
            return notTriggered();
        }

        LocalDateTime latestAnchor = todaysTransactions.stream()
                .map(Transaction::getTransactionTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();
        LocalDateTime historyFloor = latestAnchor.minusHours(HISTORY_TRIM_HOURS);

        List<Transaction> recentHistory = context.receiverHistory(receiverAccount).stream()
                .filter(t -> !t.getTransactionTime().isBefore(historyFloor))
                .toList();

        List<Transaction> recentSample = hasEnoughHistory
                ? priorHistory.stream()
                .limit(RECENT_HISTORY_SAMPLE)
                .sorted(Comparator.comparing(Transaction::getTransactionTime))
                .toList()
                : List.of();

        RuleResult best = evaluateWindow("1h", accountType, hasEnoughHistory, recentHistory, todaysTransactions,
                recentSample, Duration.ofHours(1),
                PERSONAL_1H_FLOOR, PERSONAL_1H_BUFFER, BUSINESS_1H_BUFFER);

        if (thresholdIsUncapped || totalDistinctToday >= PERSONAL_6H_FLOOR) {
            RuleResult result = evaluateWindow("6h", accountType, hasEnoughHistory, recentHistory, todaysTransactions,
                    recentSample, Duration.ofHours(6),
                    PERSONAL_6H_FLOOR, PERSONAL_6H_BUFFER, BUSINESS_6H_BUFFER);
            best = betterOf(best, result);
        }

        if (thresholdIsUncapped || totalDistinctToday >= PERSONAL_24H_FLOOR) {
            RuleResult result = evaluateWindow24h(accountType, hasEnoughHistory, recentHistory, todaysTransactions,
                    recentSample);
            best = betterOf(best, result);
        }

        return best != null ? best : notTriggered();




    }

    private RuleResult evaluateWindow(String label, AccountType accountType, boolean hasEnoughHistory,
                                      List<Transaction> recentHistory, List<Transaction> todaysTransactions,
                                      List<Transaction> recentSample, Duration window,
                                      int personalFloor, int personalBuffer, int businessBuffer) {

        int threshold;
        if (!hasEnoughHistory) {
            // Shared fallback for ANY account type with insufficient history — a self-declared
            // "business" label with no track record yet does not earn relaxed tolerance.
            threshold = personalFloor;
        } else if (accountType == AccountType.PERSONAL) {
            threshold = clamp(historicalDistinctPeak(recentSample, window) + personalBuffer,
                    personalFloor, SYSTEM_DAILY_CEILING);
        } else {
            threshold = historicalDistinctPeak(recentSample, window) + businessBuffer;
        }

        int actual = peakDistinctSendersInWindow(recentHistory, todaysTransactions, window);
        if (actual < threshold) {
            return null;
        }
        return trigger(label, actual, threshold);
    }


    private RuleResult evaluateWindow24h(AccountType accountType, boolean hasEnoughHistory,
                                         List<Transaction> recentHistory, List<Transaction> todaysTransactions,
                                         List<Transaction> recentSample) {

        Duration window = Duration.ofHours(24);
        int threshold;
        if (!hasEnoughHistory) {
            threshold = PERSONAL_24H_FLOOR;
        } else if (accountType == AccountType.PERSONAL) {
            threshold = clamp((int) Math.ceil(historicalDistinctPeak(recentSample, window) * PERSONAL_24H_MULTIPLIER),
                    PERSONAL_24H_FLOOR, SYSTEM_DAILY_CEILING);
        } else {
            threshold = (int) Math.ceil(historicalDistinctPeak(recentSample, window) * BUSINESS_24H_MULTIPLIER);
        }

        int actual = peakDistinctSendersInWindow(recentHistory, todaysTransactions, window);
        if (actual < threshold) {
            return null;
        }
        return trigger("24h", actual, threshold);
    }

    private int peakDistinctSendersInWindow(List<Transaction> recentHistory,
                                            List<Transaction> todaysTransactions, Duration window) {
        int maxDistinct = 0;
        for (Transaction anchorTxn : todaysTransactions) {
            LocalDateTime anchor = anchorTxn.getTransactionTime();
            LocalDateTime cutoff = anchor.minus(window);

            int distinctCount = (int) recentHistory.stream()
                    .filter(t -> !t.getTransactionTime().isBefore(cutoff) && !t.getTransactionTime().isAfter(anchor))
                    .map(Transaction::getSenderAccount)
                    .distinct()
                    .count();

            if (distinctCount > maxDistinct) {
                maxDistinct = distinctCount;
            }
        }
        return maxDistinct;
    }

    private int historicalDistinctPeak(List<Transaction> ascendingHistory, Duration window) {
        int maxDistinct = 0;
        int left = 0;
        Map<String, Integer> senderCounts = new HashMap<>();

        for (int right = 0; right < ascendingHistory.size(); right++) {
            Transaction rightTxn = ascendingHistory.get(right);
            senderCounts.merge(rightTxn.getSenderAccount(), 1, Integer::sum);

            while (ascendingHistory.get(left).getTransactionTime()
                    .isBefore(rightTxn.getTransactionTime().minus(window))) {
                Transaction leftTxn = ascendingHistory.get(left);
                senderCounts.computeIfPresent(leftTxn.getSenderAccount(), (k, v) -> v > 1 ? v - 1 : null);
                left++;
            }

            int distinct = senderCounts.size();
            if (distinct > maxDistinct) {
                maxDistinct = distinct;
            }
        }
        return maxDistinct;
    }

    private int clamp(int value, int floor, int ceiling) {
        return Math.max(floor, Math.min(value, ceiling));
    }

    private RuleResult notTriggered() {
        return RuleResult.builder().ruleCode(ruleCode()).triggered(false).build();
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
                "%s severity — received from %d distinct senders in the last %s, exceeding this account's expected threshold of %d.",
                severity, actualCount, window, threshold);

        return RuleResult.builder()
                .ruleCode(ruleCode())
                .triggered(true)
                .score(score)
                .reason(reason)
                .build();
    }

    private RuleResult betterOf(RuleResult a, RuleResult b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.getScore() > a.getScore() ? b : a;
    }



}
