package com.finpulse.fraud.rules;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class VelocityRuleTest {

    private final VelocityRule velocityRule = new VelocityRule();
    private Transaction txn(String id, String sender, LocalDateTime time) {
        return Transaction.builder()
                .transactionId(id)
                .senderAccount(sender)
                .receiverAccount("ACC-B")
                .transactionTime(time)
                .build();
    }
    private List<Transaction> cluster(String idPrefix, String sender, LocalDateTime anchorTime, int count, int gapMinutes) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(txn(idPrefix + i, sender, anchorTime.minusMinutes((long) i * gapMinutes)));
        }
        return list;
    }

    @Test
    void evaluate_noTransactionsToday_returnsNotTriggered(){
        AnalysisContext context = AnalysisContext.build(List.of(), List.of(), List.of());

        RuleResult result = velocityRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());

    }

    @Test
    void evaluate_fiveMinuteSpike_triggersRegardlessOfHistorySize(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> todaysTxns = List.of(txn("T1", "ACC-A", now));

        List<Transaction> history = List.of(
                txn("H1", "ACC-A", now.minusMinutes(1)),
                txn("H2", "ACC-A", now.minusMinutes(2)),
                txn("H3", "ACC-A", now.minusMinutes(3)),
                txn("H4", "ACC-A", now.minusMinutes(4))
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());

        RuleResult result = velocityRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertTrue(result.getReason().contains("5m"));
    }

    @Test
    void evaluate_insufficientHistory_floorThresholdBreached_triggers(){
        LocalDateTime now = LocalDateTime.now();

        List<Transaction> todaysTxns = List.of(txn("T1", "ACC-A", now));
        List<Transaction> history = List.of(
                txn("H1", "ACC-A", now.minusMinutes(10)),
                txn("H2", "ACC-A", now.minusMinutes(20)),
                txn("H3", "ACC-A", now.minusMinutes(30)),
                txn("H4", "ACC-A", now.minusMinutes(40)),
                txn("H5", "ACC-A", now.minusMinutes(50)),
                txn("H6", "ACC-A", now.minusMinutes(55))
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());

        RuleResult result = velocityRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertTrue(result.getReason().contains("1h"));
    }

    @Test
    void evaluate_severeBreach_returnsHighSeverityScore(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> todaysTxns = List.of(txn("T1", "ACC-A", now));
        List<Transaction> history = cluster("H", "ACC-A", now.minusMinutes(6), 15, 2);

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());

        RuleResult result = velocityRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
        assertTrue(result.getReason().contains("High"));
    }

    @Test
    void evaluate_highBaselineAccount_burstUnderAdaptiveThreshold_returnsNotTriggered(){
        LocalDateTime now = LocalDateTime.now();

        List<Transaction> todaysTxns = List.of(txn("T1", "ACC-A", now));
        List<Transaction> oldCluster = cluster("OLD", "ACC-A", now.minusDays(10), 20, 2);
        List<Transaction> padding = cluster("PAD", "ACC-A", now.minusDays(30), 2, 4320);
        List<Transaction> recentBurst = cluster("REC", "ACC-A", now.minusMinutes(6), 8, 6);

        List<Transaction> allHistory = new ArrayList<>();
        allHistory.addAll(oldCluster);
        allHistory.addAll(padding);
        allHistory.addAll(recentBurst);

        AnalysisContext context = AnalysisContext.build(todaysTxns, allHistory, List.of());
        RuleResult result = velocityRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_highBaselineAccount_genuineAnomalyAboveAdaptiveThreshold_triggers(){
        LocalDateTime now = LocalDateTime.now();

        List<Transaction> todaysTxns = List.of(txn("T1", "ACC-A", now));
        List<Transaction> oldCluster = cluster("OLD", "ACC-A", now.minusDays(10), 20, 2);
        List<Transaction> recentBurst = cluster("REC", "ACC-A", now.minusMinutes(6), 23, 2);
        List<Transaction> allHistory = new ArrayList<>();
        allHistory.addAll(oldCluster);
        allHistory.addAll(recentBurst);


        AnalysisContext context = AnalysisContext.build(todaysTxns, allHistory, List.of());
        RuleResult result = velocityRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
    }

}
