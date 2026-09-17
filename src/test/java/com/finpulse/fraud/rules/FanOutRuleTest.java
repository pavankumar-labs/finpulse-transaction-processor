package com.finpulse.fraud.rules;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class FanOutRuleTest {
    private final FanOutRule fanOutRule = new FanOutRule();

    private Transaction txn(String id, String sender, String receiver, LocalDateTime time) {
        return Transaction.builder()
                .transactionId(id)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .transactionTime(time)
                .build();
    }

    private List<Transaction> fanOutCluster(String idPrefix, String sender, String receiverPrefix,
                                            LocalDateTime anchorTime, int count, int gapMinutes) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(txn(idPrefix + i, sender, receiverPrefix + i, anchorTime.minusMinutes((long) i * gapMinutes)));
        }
        return list;
    }

    @Test
    void evaluate_noTransactionsToday_returnsNotTriggered() {
        AnalysisContext context = AnalysisContext.build(List.of(), List.of(), List.of());
        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_belowMinimumDistinctReceivers_returnsNotTriggered(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> today = List.of(
                txn("T1", "ACC-A", "R1", now),
                txn("T2", "ACC-A", "R2", now),
                txn("T3", "ACC-A", "R3", now),
                txn("T4", "ACC-A", "R4", now)
        );
        AnalysisContext context = AnalysisContext.build(today, today, List.of());

        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_onlyOneHourWindowApplicable_floorBreached_triggers(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> today = fanOutCluster("T", "ACC-A", "R", now, 6, 1);

        AnalysisContext context = AnalysisContext.build(today, today, List.of());

        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertTrue(result.getReason().contains("1h"));
    }

    @Test
    void evaluate_sixHourWindowHigherSeverity_selectsSixHourOverOneHour(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> today = fanOutCluster("T", "ACC-A", "R", now, 10, 5);

        AnalysisContext context = AnalysisContext.build(today, today, List.of());

        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
    }

    @Test
    void evaluate_highBaselineAccount_burstUnderAdaptiveThreshold_returnsNotTriggered(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> oldCluster = fanOutCluster("OLD", "ACC-A", "OR", now.minusDays(10), 20, 2);
        List<Transaction> padding = fanOutCluster("PAD", "ACC-A", "PR", now.minusDays(30), 10, 4320);
        List<Transaction> today = fanOutCluster("T", "ACC-A", "R", now, 9, 5);
        List<Transaction> allHistory = new ArrayList<>();
        allHistory.addAll(oldCluster);
        allHistory.addAll(padding);
        allHistory.addAll(today);

        AnalysisContext context = AnalysisContext.build(today, allHistory, List.of());

        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_highBaselineAccount_genuineAnomalyAboveAdaptiveThreshold_triggers(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> oldCluster = fanOutCluster("OLD", "ACC-A", "OR", now.minusDays(10), 20, 2);
        List<Transaction> padding = fanOutCluster("PAD", "ACC-A", "PR", now.minusDays(30), 10, 4320);
        List<Transaction> today = fanOutCluster("T", "ACC-A", "R", now, 22, 2);
        List<Transaction> allHistory = new ArrayList<>();
        allHistory.addAll(oldCluster);
        allHistory.addAll(padding);
        allHistory.addAll(today);

        AnalysisContext context = AnalysisContext.build(today, allHistory, List.of());

        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
    }

    @Test
    void evaluate_severeBreach_returnsHighSeverityScore(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> today = fanOutCluster("T", "ACC-A", "R", now, 20, 1);

        AnalysisContext context = AnalysisContext.build(today, today, List.of());

        RuleResult result = fanOutRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
        assertTrue(result.getReason().contains("High"));
    }
}