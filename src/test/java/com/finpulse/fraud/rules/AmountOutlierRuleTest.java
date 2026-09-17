package com.finpulse.fraud.rules;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class AmountOutlierRuleTest {

    private final AmountOutlierRule rule = new AmountOutlierRule();
    private Transaction buildTransaction(String id, String sender, String receiver,
                                         double amount, LocalDateTime time) {
        return Transaction.builder()
                .transactionId(id)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .amount(BigDecimal.valueOf(amount))
                .transactionTime(time)
                .build();
    }


    @Test
    void evaluate_insufficientHistory_returnsNotTriggered(){
        List<Transaction> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(buildTransaction("H" + i, "ACC-A", "ACC-X", 1000, LocalDateTime.now().minusDays(i + 1)));
        }

        List<Transaction> todaysTxns = List.of(
                buildTransaction("T1", "ACC-A", "ACC-Y", 50000, LocalDateTime.now())
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());
        ReflectionTestUtils.setField(rule, "zScoreThreshold", 4.0);

        RuleResult result = rule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
        assertEquals("AMOUNT_OUTLIER", result.getRuleCode());
    }

    @Test
    void evaluate_zeroStandardDeviation_returnsNotTriggered(){
        List<Transaction> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            history.add(buildTransaction("H" + i, "ACC-A", "ACC-X", 1000, LocalDateTime.now().minusDays(i + 1)));
        }

        List<Transaction> todaysTxns = List.of(
                buildTransaction("T1", "ACC-A", "ACC-Y", 50000, LocalDateTime.now())
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());
        ReflectionTestUtils.setField(rule, "zScoreThreshold", 4.0);

        RuleResult result = rule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_noTransactionsExceedThreshold_returnsNotTriggered(){
        List<Transaction> history = new ArrayList<>();
        double[] amounts = {950, 1000, 1050, 980, 1020, 1000, 970, 1010, 990, 1000,
                1030, 960, 1000, 1040, 1000, 990, 1010, 1000, 980, 1020,
                1000, 970, 1030, 1000, 990, 1010, 1000, 980, 1020, 1000};
        for (int i = 0; i < amounts.length; i++) {
            history.add(buildTransaction("H" + i, "ACC-A", "ACC-X", amounts[i], LocalDateTime.now().minusDays(i + 1)));
        }

        List<Transaction> todaysTxns = List.of(
                buildTransaction("T1", "ACC-A", "ACC-Y", 1015, LocalDateTime.now())
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());
        ReflectionTestUtils.setField(rule, "zScoreThreshold", 4.0);

        RuleResult result = rule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_lowSeverityOutlier_triggersWithScoreTen(){
        List<Transaction> history = new ArrayList<>();
        double[] amounts = {950, 1000, 1050, 980, 1020, 1000, 970, 1010, 990, 1000,
                1030, 960, 1000, 1040, 1000, 990, 1010, 1000, 980, 1020,
                1000, 970, 1030, 1000, 990, 1010, 1000, 980, 1020, 1000};
        for (int i = 0; i < amounts.length; i++) {
            history.add(buildTransaction("H" + i, "ACC-A", "ACC-X", amounts[i], LocalDateTime.now().minusDays(i + 1)));
        }

        List<Transaction> todaysTxns = List.of(
                buildTransaction("T1", "ACC-A", "ACC-Y", 1100, LocalDateTime.now())
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());
        ReflectionTestUtils.setField(rule, "zScoreThreshold", 4.0);

        RuleResult result = rule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(10, result.getScore());
        assertTrue(result.getReason().contains("Low severity"));
    }

    @Test
    void evaluate_highSeverityOutlier_triggersWithScoreThirty(){
        List<Transaction> history = new ArrayList<>();
        double[] amounts = {950, 1000, 1050, 980, 1020, 1000, 970, 1010, 990, 1000,
                1030, 960, 1000, 1040, 1000, 990, 1010, 1000, 980, 1020,
                1000, 970, 1030, 1000, 990, 1010, 1000, 980, 1020, 1000};
        for (int i = 0; i < amounts.length; i++) {
            history.add(buildTransaction("H" + i, "ACC-A", "ACC-X", amounts[i], LocalDateTime.now().minusDays(i + 1)));
        }

        List<Transaction> todaysTxns = List.of(
                buildTransaction("T1", "ACC-A", "ACC-Y", 50000, LocalDateTime.now())
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());
        ReflectionTestUtils.setField(rule, "zScoreThreshold", 4.0);

        RuleResult result = rule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
        assertTrue(result.getReason().contains("High severity"));
    }

    @Test
    void evaluate_multipleFlaggedTransactions_scoresByTheWorstOne(){
        List<Transaction> history = new ArrayList<>();
        double[] amounts = {950, 1000, 1050, 980, 1020, 1000, 970, 1010, 990, 1000,
                1030, 960, 1000, 1040, 1000, 990, 1010, 1000, 980, 1020,
                1000, 970, 1030, 1000, 990, 1010, 1000, 980, 1020, 1000};
        for (int i = 0; i < amounts.length; i++) {
            history.add(buildTransaction("H" + i, "ACC-A", "ACC-X", amounts[i], LocalDateTime.now().minusDays(i + 1)));
        }

        List<Transaction> todaysTxns = List.of(
                buildTransaction("T1", "ACC-A", "ACC-Y", 1120, LocalDateTime.now().minusMinutes(5)),
                buildTransaction("T2", "ACC-A", "ACC-Z", 50000, LocalDateTime.now())
        );

        AnalysisContext context = AnalysisContext.build(todaysTxns, history, List.of());
        ReflectionTestUtils.setField(rule, "zScoreThreshold", 4.0);

        RuleResult result = rule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());

    }
    
}
