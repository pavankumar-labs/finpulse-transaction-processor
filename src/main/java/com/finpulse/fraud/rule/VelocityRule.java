package com.finpulse.fraud.rule;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
public class VelocityRule implements FraudRule {

    private static final int MIN_HISTORY_REQUIRED = 30;
    private static final int RECENT_HISTORY_SAMPLE = 50;
    private static final int HISTORY_TRIM_HOURS = 48;

    private static final int FIVE_MIN_FLAT_THRESHOLD = 4;

    private static final int ONE_HOUR_FLOOR = 6;
    private static final int ONE_HOUR_BUFFER = 2;

    private static final int TWENTY_FOUR_HOUR_FLOOR = 10;
    private static final int TWENTY_FOUR_HOUR_BUFFER = 4;

    private static final int SYSTEM_DAILY_CEILING = 20;

    @Override
    public String ruleCode() {
        return "VELOCITY";
    }

    @Override
    public RuleResult evaluate(String senderAccount, AnalysisContext context) {

        List<Transaction> todaysTransactions = context.transactionsInFileForSender(senderAccount);
        if (todaysTransactions.isEmpty()) {
            return notTriggered();
        }

        LocalDateTime latestAnchor = todaysTransactions.stream()
                .map(Transaction::getTransactionTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();

        LocalDateTime historyFloor = latestAnchor.minusHours(HISTORY_TRIM_HOURS);

        List<Transaction> recentHistory = context.senderHistory(senderAccount).stream()
                .filter(t -> !t.getTransactionTime().isBefore(historyFloor))
                .toList();

        int count5m = peakCountInWindow(recentHistory, todaysTransactions, Duration.ofMinutes(5));
        if (count5m >= FIVE_MIN_FLAT_THRESHOLD) {
            return trigger("5m", count5m, FIVE_MIN_FLAT_THRESHOLD);
        }

        List<Transaction> priorHistory = context.senderHistoryExcludingToday(senderAccount);

        int threshold1h;
        int threshold24h;

        if (priorHistory.size() >= MIN_HISTORY_REQUIRED) {

            List<Transaction> recentSample = priorHistory.stream()
                    .limit(RECENT_HISTORY_SAMPLE)
                    .sorted(Comparator.comparing(Transaction::getTransactionTime))
                    .toList();

            int peak1h = historicalPeak(recentSample, Duration.ofHours(1));
            int peak24h = historicalPeak(recentSample, Duration.ofHours(24));

            threshold1h = clamp(peak1h + ONE_HOUR_BUFFER, ONE_HOUR_FLOOR, SYSTEM_DAILY_CEILING);
            threshold24h = clamp(peak24h + TWENTY_FOUR_HOUR_BUFFER, TWENTY_FOUR_HOUR_FLOOR, SYSTEM_DAILY_CEILING);

        }
        else {
            threshold1h = ONE_HOUR_FLOOR;
            threshold24h = TWENTY_FOUR_HOUR_FLOOR;
        }
        int count1h = peakCountInWindow(recentHistory, todaysTransactions, Duration.ofHours(1));
        int count24h = peakCountInWindow(recentHistory, todaysTransactions, Duration.ofHours(24));

        double ratio1h = (double) count1h / threshold1h;
        double ratio24h = (double) count24h / threshold24h;

        if (ratio1h >= 1.0 && ratio1h >= ratio24h) {
            return trigger("1h", count1h, threshold1h);
        }
        if (ratio24h >= 1.0) {
            return trigger("24h", count24h, threshold24h);
        }

        return notTriggered();
        }

    private int clamp(int value, int floor, int ceiling) {
        return Math.max(floor, Math.min(value, ceiling));
    }


    private int peakCountInWindow(List<Transaction> fullHistory, List<Transaction> todaysTransactions, Duration window) {
        int maxCount = 0;
        for (Transaction anchorTxn : todaysTransactions) {
            LocalDateTime anchor = anchorTxn.getTransactionTime();
            LocalDateTime cutoff = anchor.minus(window);
            int count = (int) fullHistory.stream()
                    .filter(t -> !t.getTransactionTime().isBefore(cutoff) && !t.getTransactionTime().isAfter(anchor))
                    .count();
            if (count > maxCount) {
                maxCount = count;
            }
        }
        return maxCount;
    }



    private RuleResult trigger (String window,int actualCount, int threshold){
            double ratio = (double) actualCount / threshold;

            int score;
            String severity;
            if (ratio >= 2.5) {
                score = 30;
                severity = "High";
            } else if (ratio >= 1.5) {
                score = 20;
                severity = "Moderate";
            } else {
                score = 10;
                severity = "Low";
            }

            String reason = String.format(
                    "%s severity — %d transactions in the last %s, exceeding this account's expected threshold of %d.",
                    severity, actualCount, window, threshold);

            return RuleResult.builder()
                    .ruleCode(ruleCode())
                    .triggered(true)
                    .score(score)
                    .reason(reason)
                    .build();
        }

    private int historicalPeak(List<Transaction> ascendingHistory, Duration window) {
        int maxCount = 0;
        int left = 0;

        for (int right = 0; right < ascendingHistory.size(); right++) {
            LocalDateTime rightTime = ascendingHistory.get(right).getTransactionTime();

            while (ascendingHistory.get(left).getTransactionTime().isBefore(rightTime.minus(window))) {
                left++;
            }

            int currentCount = right - left + 1;
            if (currentCount > maxCount) {
                maxCount = currentCount;
            }
        }
        return maxCount;
    }
    private RuleResult notTriggered() {
        return RuleResult.builder().ruleCode(ruleCode()).triggered(false).build();
    }


    }
