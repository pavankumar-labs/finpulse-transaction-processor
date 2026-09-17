package com.finpulse.fraud.context;

import com.finpulse.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AnalysisContextTest {

    private AnalysisContext analysisContext;

    @Test
    void build_duplicateSenderInFile_dedupsSendersInFile(){

        Transaction t1 = Transaction.builder()
                .transactionId("TXN1")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-B")
                .transactionTime(LocalDateTime.now())
                .build();
        Transaction t2 = Transaction.builder()
                .transactionId("TXN2")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-C")
                .transactionTime(LocalDateTime.now())
                .build();

        AnalysisContext context = AnalysisContext.build(List.of(t1, t2), List.of(), List.of());
        assertEquals(1, context.sendersInFile().size());
        assertTrue(context.sendersInFile().contains("ACC-A"));
    }

    @Test
    void senderHistory_moreThanTwoHundredRecords_capsAtTwoHundredMostRecent(){
        List<Transaction> history = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(1);

        for (int i = 0; i < 201; i++) {
            history.add(Transaction.builder()
                    .transactionId("TXN" + i)
                    .senderAccount("ACC-A")
                    .receiverAccount("ACC-B")
                    .transactionTime(base.plusMinutes(i))
                    .build());
        }

        AnalysisContext context = AnalysisContext.build(List.of(), history, List.of());
        List<Transaction> result = context.senderHistory("ACC-A");

        assertEquals(200, result.size());
        assertEquals("TXN200", result.get(0).getTransactionId());
        assertFalse(result.stream().anyMatch(t -> t.getTransactionId().equals("TXN0")));
    }

    @Test
    void senderHistoryExcludingToday_accountNotInTodaysFile_keepsFullHistory(){
        Transaction historyTxn = Transaction.builder()
                .transactionId("OLD1")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-B")
                .transactionTime(LocalDateTime.now().minusDays(5))
                .build();

        Transaction fileTxn = Transaction.builder()
                .transactionId("NEW1")
                .senderAccount("ACC-Z")
                .receiverAccount("ACC-B")
                .transactionTime(LocalDateTime.now())
                .build();

        AnalysisContext context = AnalysisContext.build(List.of(fileTxn), List.of(historyTxn), List.of());

        List<Transaction> result = context.senderHistoryExcludingToday("ACC-A");

        assertEquals(1, result.size());
        assertEquals("OLD1", result.get(0).getTransactionId());
    }

    @Test
    void senderHistory_returnsInDescendingTimeOrder(){
        Transaction t1 = Transaction.builder()
                .transactionId("TXN1")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-B")
                .transactionTime(LocalDateTime.now().minusDays(4))
                .build();
        Transaction t2 = Transaction.builder()
                .transactionId("TXN2")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-2")
                .transactionTime(LocalDateTime.now().minusHours(6))
                .build();
        Transaction t3 = Transaction.builder()
                .transactionId("TXN3")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-B")
                .transactionTime(LocalDateTime.now().minusHours(12))
                .build();

        AnalysisContext context = AnalysisContext.build(List.of(), List.of(t1,t2,t3), List.of());
       List<Transaction> senderHistory= context.senderHistory("ACC-A");
       assertEquals("TXN2",senderHistory.get(0).getTransactionId());
       assertEquals("TXN1",senderHistory.get(2).getTransactionId());
    }

    @Test
    void senderHistoryExcludingToday_matchingTransactionId_excludesFromHistoryOnly(){
        Transaction t2 = Transaction.builder()
                .transactionId("TXN2")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-2")
                .transactionTime(LocalDateTime.now().minusHours(6))
                .build();

        AnalysisContext context = AnalysisContext.build(List.of(t2), List.of(t2), List.of());
        List<Transaction> senderHistory= context.senderHistory("ACC-A");
        List<Transaction> senderHistoryNoToday=context.senderHistoryExcludingToday("ACC-A");
        assertEquals("TXN2",senderHistory.get(0).getTransactionId());
        assertTrue(senderHistoryNoToday.isEmpty());
    }

    @Test
    void senderHistory_unknownAccount_returnsEmptyListNotNull(){
        Transaction t1 = Transaction.builder()
                .transactionId("TXN1")
                .senderAccount("ACC-A")
                .receiverAccount("ACC-B")
                .transactionTime(LocalDateTime.now())
                .build();

        AnalysisContext context = AnalysisContext.build(List.of(), List.of(t1), List.of());

        List<Transaction> result = context.senderHistory("ACC-NEVER-SEEN");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
