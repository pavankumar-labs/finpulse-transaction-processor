package com.finpulse.fraud.rules;

import com.finpulse.entity.Transaction;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

 class DormantReactivationRuleTest {


    private final DormantReactivationRule dormantReactivationRule = new DormantReactivationRule();

    @BeforeEach
    void setUp(){
        ReflectionTestUtils.setField(dormantReactivationRule, "dormancyDays", 90L);
    }

    private Transaction txn(String id, String sender, LocalDateTime time, double amount) {
        return Transaction.builder()
                .transactionId(id)
                .senderAccount(sender)
                .receiverAccount("ACC-B")
                .transactionTime(time)
                .amount(BigDecimal.valueOf(amount))
                .build();
    }

    private List<Transaction> historyOfSize(String sender, LocalDateTime mostRecentTime, int count, double amount) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(txn("H" + i, sender, mostRecentTime.minusDays(i), amount));
        }
        return list;
    }

    @Test
    void evaluate_insufficientHistory_returnsNotTriggered(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> history = historyOfSize("ACC-A", now.minusDays(5), 5, 100);
        List<Transaction> today = List.of(txn("T1", "ACC-A", now, 500));

        AnalysisContext context = AnalysisContext.build(today, history, List.of());
        RuleResult result = dormantReactivationRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }


    @Test
    void evaluate_sufficientHistoryButNoTransactionsToday_returnsNotTriggered(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> history = historyOfSize("ACC-A", now.minusDays(10), 30, 100);

        AnalysisContext context = AnalysisContext.build(List.of(), history, List.of());
        RuleResult result = dormantReactivationRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_gapShorterThanDormancyPeriod_returnsNotTriggered(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> history = historyOfSize("ACC-A", now.minusDays(10), 30, 100);
        List<Transaction> today = List.of(txn("T1", "ACC-A", now, 500));

        AnalysisContext context = AnalysisContext.build(today, history, List.of());
        RuleResult result = dormantReactivationRule.evaluate("ACC-A", context);
        assertFalse(result.isTriggered());
    }

    @Test
    void evaluate_dormantReactivationWithTypicalAmount_triggersLowSeverity(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> history = historyOfSize("ACC-A", now.minusDays(100), 30, 100);
        List<Transaction> today = List.of(txn("T1", "ACC-A", now, 150));

        AnalysisContext context = AnalysisContext.build(today, history, List.of());

        RuleResult result = dormantReactivationRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(10, result.getScore());
        assertTrue(result.getReason().contains("Low"));
    }

    @Test
    void evaluate_dormantReactivationWithSevereAmount_triggersHighSeverity(){
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> history = historyOfSize("ACC-A", now.minusDays(100), 30, 100);
        List<Transaction> today = List.of(txn("T1", "ACC-A", now, 600));

        AnalysisContext context = AnalysisContext.build(today, history, List.of());

        RuleResult result = dormantReactivationRule.evaluate("ACC-A", context);
        assertTrue(result.isTriggered());
        assertEquals(30, result.getScore());
        assertTrue(result.getReason().contains("High"));
    }



}
