package com.finpulse.listener;


import com.finpulse.entity.*;
import com.finpulse.event.FileProcessingCompletedEvent;
import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;
import com.finpulse.fraud.rule.*;
import com.finpulse.repository.FraudFindingRepository;
import com.finpulse.repository.NotificationRepository;
import com.finpulse.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudAnalysisListener {

    private static final int HISTORY_WINDOW_DAYS = 365;
    private static final int BATCH_SIZE = 10;

    private final TransactionRepository transactionRepository;
    private final FraudFindingRepository fraudFindingRepository;
    private final NotificationRepository notificationRepository;

    @Qualifier("accountEvaluationExecutor")
    private final Executor accountEvaluationExecutor;

    private final VelocityRule velocityRule;
    private final FanInRule fanInRule;
    private final FanOutRule fanOutRule;
    private final AmountOutlierRule amountOutlierRule;
    private final DormantReactivationRule dormantReactivationRule;

    @Async("fraudAnalysisExecutor")
    @EventListener
    public void onFileProcessingCompleted(FileProcessingCompletedEvent event){

        List<Transaction> fileTransactions =
                transactionRepository.findByFileProcessingId(event.getFileProcessingId());

        if (fileTransactions.isEmpty()) {
            log.warn("No transactions found for fraud analysis. fileProcessingId={}, companyId={}",
                    event.getFileProcessingId(), event.getCompanyId());
            return;
        }

        LocalDateTime windowStart = LocalDateTime.now().minusDays(HISTORY_WINDOW_DAYS);

        Set<String> senders = fileTransactions.stream()
                .map(Transaction::getSenderAccount)
                .collect(Collectors.toSet());

        Set<String> receivers = fileTransactions.stream()
                .map(Transaction::getReceiverAccount)
                .collect(Collectors.toSet());


        List<Transaction> senderHistoryRaw =
                transactionRepository.findSenderHistory(event.getCompanyId(), senders, windowStart);

        List<Transaction> receiverHistoryRaw =
                transactionRepository.findReceiverHistory(event.getCompanyId(), receivers, windowStart);

        AnalysisContext context = AnalysisContext.build(fileTransactions, senderHistoryRaw, receiverHistoryRaw);

        log.info("AnalysisContext built. fileProcessingId={}, companyId={}, senders={}, receivers={}",
                event.getFileProcessingId(), event.getCompanyId(), senders.size(), receivers.size());

        dispatchAccountEvaluations(context, event);

    }


    private void dispatchAccountEvaluations(AnalysisContext context, FileProcessingCompletedEvent event){

        Set<String> allAccounts = new HashSet<>();
        allAccounts.addAll(context.sendersInFile());
        allAccounts.addAll(context.receiversInFile());


        List<List<String>> batches = partitionIntoBatches(new ArrayList<>(allAccounts), BATCH_SIZE);

        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        for(List<String> batch : batches){

            CompletableFuture<Void> future=CompletableFuture.runAsync(
                    ()->processBatch(batch,context, event.getCompanyId(), event.getFileProcessingId()),
                    accountEvaluationExecutor
            );

            batchFutures.add(future);
        }

        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                .thenRun(()-> onFraudAnalysisFullyCompleted(event, allAccounts.size()));
    }

    private void onFraudAnalysisFullyCompleted(FileProcessingCompletedEvent event, int accountsProcessed){

        long flaggedCount = fraudFindingRepository
                .countByCompanyIdAndFileProcessingId(event.getCompanyId(), event.getFileProcessingId());

        log.info("Fraud analysis fully completed. fileProcessingId={}, companyId={}, accountsProcessed={}, flagged={}",
                event.getFileProcessingId(), event.getCompanyId(), accountsProcessed, flaggedCount);

        String message = flaggedCount > 0
                ? String.format("Fraud analysis complete: %d account(s) flagged for review.", flaggedCount)
                : "Fraud analysis complete: no suspicious activity found.";

        Notification notification = Notification.builder()
                .companyId(event.getCompanyId())
                .message(message)
                .referenceId(event.getFileProcessingId())
                .viewed(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    private List<List<String>> partitionIntoBatches(List<String> accounts, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i += batchSize) {
            batches.add(accounts.subList(i, Math.min(i + batchSize, accounts.size())));
        }
        return batches;
    }

    private void processBatch(List<String> batch, AnalysisContext context, Long companyId, String fileProcessingId){
        for (String account : batch){
            try{
                List<RuleResult> results = new ArrayList<>();

                if (context.sendersInFile().contains(account)) {
                    results.add(velocityRule.evaluate(account, context));
                    results.add(fanOutRule.evaluate(account, context));
                    results.add(amountOutlierRule.evaluate(account, context));
                    results.add(dormantReactivationRule.evaluate(account, context));
                }

                if (context.receiversInFile().contains(account)) {
                    results.add(fanInRule.evaluate(account, context));
                }

                scoreAndPersist(account, results, companyId, fileProcessingId);
            } catch (Exception e) {
                log.error("Fraud rule evaluation failed for account. account={}, fileProcessingId={}",
                        account, fileProcessingId, e);
            }
        }
    }

    private void scoreAndPersist(String account, List<RuleResult> results, Long companyId, String fileProcessingId){

        List<RuleResult> triggered = results.stream()
                .filter(RuleResult::isTriggered)
                .toList();


        if (triggered.isEmpty()) {
            return;
        }

        int totalScore = triggered.stream().mapToInt(RuleResult::getScore).sum();
        RiskLevel riskLevel = riskLevelFor(totalScore);

        String ruleCodes = triggered.stream()
                .map(RuleResult::getRuleCode)
                .collect(Collectors.joining(","));

        String reason = triggered.stream()
                .map(r -> r.getRuleCode() + ": " + r.getReason())
                .collect(Collectors.joining(" | "));

        FraudFinding finding = FraudFinding.builder()
                .companyId(companyId)
                .fileProcessingId(fileProcessingId)
                .accountNumber(account)
                .riskLevel(riskLevel)
                .status(FraudStatus.PENDING)
                .triggeredRuleCodes(ruleCodes)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();


        fraudFindingRepository.save(finding);
    }

    private RiskLevel riskLevelFor(int score) {
        if (score >= 51) {
            return RiskLevel.HIGH;
        } else if (score >= 31) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

}
