package com.finpulse.fraud.rules;

import com.finpulse.entity.AccountType;
import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class FanInRuleTest {

    private final FanInRule rule = new FanInRule();

    private Transaction buildTransaction(String id, String sender, String receiver,
                                         AccountType receiverAccountType, LocalDateTime time) {
        return Transaction.builder()
                .transactionId(id)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .receiverAccountType(receiverAccountType)
                .amount(BigDecimal.valueOf(100))
                .transactionTime(time)
                .build();
    }

    private List<Transaction> combine(List<Transaction> a, List<Transaction> b) {
        List<Transaction> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
    }

    @Test
    void evaluate_noTransactionsToday_returnsNotTriggered() {
        AnalysisContext context = AnalysisContext.build(List.of(), List.of(), List.of());
        RuleResult result = rule.evaluate("ACC-EMPTY", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_belowMinimumDistinctSendersAndNotUncapped_returnsNotTriggered() {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> today = List.of(
                buildTransaction("T1", "S1", "ACC-A", AccountType.PERSONAL, now),
                buildTransaction("T2", "S2", "ACC-A", AccountType.PERSONAL, now),
                buildTransaction("T3", "S3", "ACC-A", AccountType.PERSONAL, now)
        );

        AnalysisContext context = AnalysisContext.build(today, List.of(), List.of());
        RuleResult result = rule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_businessAccountWithoutEnoughHistory_usesPersonalFloorNotBusinessTreatment() {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> sparseHistory = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sparseHistory.add(buildTransaction("H" + i, "S" + i, "ACC-BIZ",
                    AccountType.BUSINESS, now.minusDays(i + 1)));
        }
        List<Transaction> today = List.of(
                buildTransaction("T1", "S1", "ACC-BIZ", AccountType.BUSINESS, now),
                buildTransaction("T2", "S2", "ACC-BIZ", AccountType.BUSINESS, now),
                buildTransaction("T3", "S3", "ACC-BIZ", AccountType.BUSINESS, now),
                buildTransaction("T4", "S4", "ACC-BIZ", AccountType.BUSINESS, now),
                buildTransaction("T5", "S5", "ACC-BIZ", AccountType.BUSINESS, now)
        );

        AnalysisContext context = AnalysisContext.build(today, List.of(), combine(sparseHistory, today));

        RuleResult result = rule.evaluate("ACC-BIZ", context);
        assertTrue(result.isTriggered());
        assertEquals(10, result.getScore());
    }

    @Test
    void evaluate_businessAccountWithEnoughHistory_evaluatesLargeFanInBurstAsHighSeverity() {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> richHistory = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            richHistory.add(buildTransaction("H" + i, "REGULAR-SENDER", "ACC-BIZ2",
                    AccountType.BUSINESS, now.minusDays(i + 1)));
        }
        List<Transaction> today = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            today.add(buildTransaction("T" + i, "S" + i, "ACC-BIZ2", AccountType.BUSINESS, now));
        }

        AnalysisContext context = AnalysisContext.build(today, List.of(), combine(richHistory, today));

        RuleResult result = rule.evaluate("ACC-BIZ2", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
    }

    @Test
    void evaluate_personalAccountThreshold_isClampedToSystemCeiling() {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> burstHistory = new ArrayList<>();
        LocalDateTime burstAnchor = now.minusDays(5);
        for (int i = 0; i < 25; i++) {
            burstHistory.add(buildTransaction("H" + i, "SENDER" + i, "ACC-PERS",
                    AccountType.PERSONAL, burstAnchor.plusMinutes(i)));
        }
        for (int i = 25; i < 30; i++) {
            burstHistory.add(buildTransaction("HPAD" + i, "OLD-SENDER", "ACC-PERS",
                    AccountType.PERSONAL, now.minusDays(20 + i)));
        }
        List<Transaction> today = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            today.add(buildTransaction("T" + i, "NEWSENDER" + i, "ACC-PERS",
                    AccountType.PERSONAL, now));
        }

        AnalysisContext context = AnalysisContext.build(today, List.of(), combine(burstHistory, today));

        RuleResult result = rule.evaluate("ACC-PERS", context);
        assertTrue(result.isTriggered());
    }

    @Test
    void evaluate_transactionExactlyAtWindowBoundary_isIncludedInCount() {
        LocalDateTime anchor = LocalDateTime.now();
        List<Transaction> today = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            today.add(buildTransaction("T" + i, "S" + i, "ACC-EDGE", AccountType.PERSONAL, anchor));
        }
        List<Transaction> boundaryHistory = List.of(
                buildTransaction("H-BOUNDARY", "BOUNDARY-SENDER", "ACC-EDGE",
                        AccountType.PERSONAL, anchor.minusHours(1))
        );

        AnalysisContext context = AnalysisContext.build(
                today, List.of(), combine(today, boundaryHistory));

        RuleResult result = rule.evaluate("ACC-EDGE", context);
        assertTrue(result.isTriggered());
        assertEquals(20, result.getScore(),
                "Score should be 20 (Moderate) only if the boundary transaction at " +
                        "exactly anchor-1h is counted as inside the window - a score of 10 " +
                        "would indicate an off-by-one exclusion at the window edge.");
    }

    @Test
    void evaluate_ratioExactlyTwo_scoresAsHighSeverity() {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> today = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            today.add(buildTransaction("T" + i, "S" + i, "ACC-RATIO", AccountType.PERSONAL, now));
        }

        AnalysisContext context = AnalysisContext.build(today, List.of(), today);

        RuleResult result = rule.evaluate("ACC-RATIO", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
    }
}
