package com.finpulse.listener;

import com.finpulse.entity.*;
import com.finpulse.event.FileProcessingCompletedEvent;
import com.finpulse.fraud.model.RuleResult;
import com.finpulse.fraud.rules.*;
import com.finpulse.repository.FraudFindingRepository;
import com.finpulse.repository.NotificationRepository;
import com.finpulse.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudAnalysisListenerTest {

    private TransactionRepository transactionRepository;
    private FraudFindingRepository fraudFindingRepository;
    private NotificationRepository notificationRepository;
    private VelocityRule velocityRule;
    private FanInRule fanInRule;
    private FanOutRule fanOutRule;
    private AmountOutlierRule amountOutlierRule;
    private DormantReactivationRule dormantReactivationRule;
    private FraudAnalysisListener listener;
    private static final String FILE_PROCESSING_ID = "proc-1";
    private static final Long COMPANY_ID = 1L;

    private void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        fraudFindingRepository = mock(FraudFindingRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        velocityRule = mock(VelocityRule.class);
        fanInRule = mock(FanInRule.class);
        fanOutRule = mock(FanOutRule.class);
        amountOutlierRule = mock(AmountOutlierRule.class);
        dormantReactivationRule = mock(DormantReactivationRule.class);

        Executor sameThreadExecutor = Runnable::run;

        listener = new FraudAnalysisListener(
                transactionRepository, fraudFindingRepository, notificationRepository,
                sameThreadExecutor, velocityRule, fanInRule, fanOutRule,
                amountOutlierRule, dormantReactivationRule);

        when(velocityRule.evaluate(anyString(), any())).thenReturn(notTriggered("VELOCITY"));
        when(fanInRule.evaluate(anyString(), any())).thenReturn(notTriggered("FAN_IN"));
        when(fanOutRule.evaluate(anyString(), any())).thenReturn(notTriggered("FAN_OUT"));
        when(amountOutlierRule.evaluate(anyString(), any())).thenReturn(notTriggered("AMOUNT_OUTLIER"));
        when(dormantReactivationRule.evaluate(anyString(), any())).thenReturn(notTriggered("DORMANT_REACTIVATION"));
        when(transactionRepository.findSenderHistory(any(), any(), any())).thenReturn(List.of());
        when(transactionRepository.findReceiverHistory(any(), any(), any())).thenReturn(List.of());
    }

    private RuleResult notTriggered(String ruleCode) {
        return RuleResult.builder().ruleCode(ruleCode).triggered(false).build();
    }

    private RuleResult triggered(String ruleCode, int score) {
        return RuleResult.builder().ruleCode(ruleCode).triggered(true).score(score)
                .reason(ruleCode + " fired").build();
    }

    private Transaction txn(String id, String sender, String receiver) {
        return Transaction.builder()
                .transactionId(id)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .amount(BigDecimal.valueOf(100))
                .transactionTime(LocalDateTime.now())
                .senderAccountType(AccountType.PERSONAL)
                .receiverAccountType(AccountType.PERSONAL)
                .build();
    }

    @Test
    void onFileProcessingCompleted_doesNothing_whenNoTransactionsFound() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID)).thenReturn(List.of());
        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verifyNoInteractions(fraudFindingRepository, notificationRepository, velocityRule, fanInRule);
    }


    @Test
    void processBatch_senderOnlyAccount_evaluatesSenderRulesButNotFanIn() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-SENDER", "ACC-OTHER")));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(velocityRule).evaluate(eq("ACC-SENDER"), any());
        verify(fanOutRule).evaluate(eq("ACC-SENDER"), any());
        verify(amountOutlierRule).evaluate(eq("ACC-SENDER"), any());
        verify(dormantReactivationRule).evaluate(eq("ACC-SENDER"), any());
        verify(fanInRule, never()).evaluate(eq("ACC-SENDER"), any());
    }

    @Test
    void processBatch_receiverOnlyAccount_evaluatesOnlyFanIn() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-OTHER", "ACC-RECEIVER")));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fanInRule).evaluate(eq("ACC-RECEIVER"), any());
        verify(velocityRule, never()).evaluate(eq("ACC-RECEIVER"), any());
        verify(fanOutRule, never()).evaluate(eq("ACC-RECEIVER"), any());
        verify(amountOutlierRule, never()).evaluate(eq("ACC-RECEIVER"), any());
        verify(dormantReactivationRule, never()).evaluate(eq("ACC-RECEIVER"), any());
    }

    @Test
    void processBatch_accountThatIsBothSenderAndReceiver_evaluatesAllFiveRules() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(
                        txn("T1", "ACC-BOTH", "ACC-X"),
                        txn("T2", "ACC-Y", "ACC-BOTH")
                ));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(velocityRule).evaluate(eq("ACC-BOTH"), any());
        verify(fanOutRule).evaluate(eq("ACC-BOTH"), any());
        verify(amountOutlierRule).evaluate(eq("ACC-BOTH"), any());
        verify(dormantReactivationRule).evaluate(eq("ACC-BOTH"), any());
        verify(fanInRule).evaluate(eq("ACC-BOTH"), any());
    }

    @Test
    void processBatch_oneAccountsRuleThrowingException_doesNotPreventOtherAccountsFromBeingProcessed() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(
                        txn("T1", "ACC-BROKEN", "ACC-X"),
                        txn("T2", "ACC-FINE", "ACC-Y")
                ));
        when(velocityRule.evaluate(eq("ACC-BROKEN"), any())).thenThrow(new RuntimeException("boom"));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fanOutRule).evaluate(eq("ACC-FINE"), any());
        verify(amountOutlierRule).evaluate(eq("ACC-FINE"), any());
    }

    @Test
    void scoreAndPersist_noRulesTriggered_doesNotCreateFraudFinding() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-CLEAN", "ACC-X")));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fraudFindingRepository, never()).save(any());
    }

    @Test
    void scoreAndPersist_totalScoreThirty_classifiedAsLow() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-LOW", "ACC-X")));
        when(velocityRule.evaluate(eq("ACC-LOW"), any())).thenReturn(triggered("VELOCITY", 30));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fraudFindingRepository).save(argThat(f -> f.getRiskLevel() == RiskLevel.LOW));
    }

    @Test
    void scoreAndPersist_totalScoreThirtyOne_classifiedAsMedium() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-MED", "ACC-X")));
        when(velocityRule.evaluate(eq("ACC-MED"), any())).thenReturn(triggered("VELOCITY", 31));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fraudFindingRepository).save(argThat(f -> f.getRiskLevel() == RiskLevel.MEDIUM));
    }

    @Test
    void scoreAndPersist_totalScoreFifty_classifiedAsMedium() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-MED2", "ACC-X")));
        when(velocityRule.evaluate(eq("ACC-MED2"), any())).thenReturn(triggered("VELOCITY", 20));
        when(fanOutRule.evaluate(eq("ACC-MED2"), any())).thenReturn(triggered("FAN_OUT", 30));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fraudFindingRepository).save(argThat(f -> f.getRiskLevel() == RiskLevel.MEDIUM));
    }

    @Test
    void scoreAndPersist_totalScoreFiftyOne_classifiedAsHigh() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-HIGH", "ACC-X")));
        when(velocityRule.evaluate(eq("ACC-HIGH"), any())).thenReturn(triggered("VELOCITY", 51));

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(fraudFindingRepository).save(argThat(f ->
                f.getRiskLevel() == RiskLevel.HIGH
                        && f.getStatus() == FraudStatus.PENDING
                        && f.getTriggeredRuleCodes().equals("VELOCITY")));
    }

    @Test
    void onFraudAnalysisFullyCompleted_savesNotificationWithFlaggedMessage_whenFindingsExist() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-FLAGGED", "ACC-X")));
        when(velocityRule.evaluate(eq("ACC-FLAGGED"), any())).thenReturn(triggered("VELOCITY", 40));
        when(fraudFindingRepository.countByCompanyIdAndFileProcessingId(COMPANY_ID, FILE_PROCESSING_ID))
                .thenReturn(1L);

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(notificationRepository).save(argThat(n ->
                n.getMessage().contains("1 account(s) flagged")
                        && n.getReferenceId().equals(FILE_PROCESSING_ID)
                        && !n.isViewed()));
    }

    @Test
    void onFraudAnalysisFullyCompleted_savesNotificationWithCleanMessage_whenNoFindings() {
        setUp();
        when(transactionRepository.findByFileProcessingId(FILE_PROCESSING_ID))
                .thenReturn(List.of(txn("T1", "ACC-CLEAN2", "ACC-X")));
        when(fraudFindingRepository.countByCompanyIdAndFileProcessingId(COMPANY_ID, FILE_PROCESSING_ID))
                .thenReturn(0L);

        listener.onFileProcessingCompleted(new FileProcessingCompletedEvent(FILE_PROCESSING_ID, COMPANY_ID));

        verify(notificationRepository).save(argThat(n ->
                n.getMessage().contains("no suspicious activity found")));
    }
}