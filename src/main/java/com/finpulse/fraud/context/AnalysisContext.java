package com.finpulse.fraud.context;

import com.finpulse.entity.Transaction;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnalysisContext {

    private static final int MAX_HISTORY_PER_ACCOUNT = 200;

    private final Set<String> sendersInFile;
    private final Set<String> receiversInFile;
    private final Map<String, List<Transaction>> senderHistory;
    private final Map<String, List<Transaction>> receiverHistory;
    private final Map<String, List<Transaction>> transactionsInFileBySender;
    private final Map<String, List<Transaction>> senderHistoryExcludingToday;
    private final Map<String, List<Transaction>> transactionsInFileByReceiver;
    private final Map<String, List<Transaction>> receiverHistoryExcludingToday;



    private AnalysisContext(Set<String> sendersInFile,
                            Set<String> receiversInFile,
                            Map<String, List<Transaction>> senderHistory,
                            Map<String, List<Transaction>> receiverHistory,
                            Map<String, List<Transaction>> transactionsInFileBySender,
                            Map<String, List<Transaction>> senderHistoryExcludingToday,
                            Map<String, List<Transaction>> transactionsInFileByReceiver,
                            Map<String, List<Transaction>> receiverHistoryExcludingToday) {
        this.sendersInFile = sendersInFile;
        this.receiversInFile = receiversInFile;
        this.senderHistory = senderHistory;
        this.receiverHistory = receiverHistory;
        this.transactionsInFileBySender = transactionsInFileBySender;
        this.senderHistoryExcludingToday = senderHistoryExcludingToday;
        this.transactionsInFileByReceiver = transactionsInFileByReceiver;
        this.receiverHistoryExcludingToday = receiverHistoryExcludingToday;

    }

    public static AnalysisContext build(List<Transaction> fileTransactions,
                                        List<Transaction> senderHistoryRaw,List<Transaction> receiverHistoryRaw){
        Set<String> senders=fileTransactions.stream()
                .map(Transaction::getSenderAccount)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> receivers=fileTransactions.stream()
                .map(Transaction::getReceiverAccount)
                .collect(Collectors.toUnmodifiableSet());

        Map<String, List<Transaction>> bySender = groupAndCap(senderHistoryRaw, Transaction::getSenderAccount);

        Map<String, List<Transaction>> byReceiver=groupAndCap(receiverHistoryRaw, Transaction::getReceiverAccount);

        Map<String, List<Transaction>> inFileBySender = fileTransactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getSenderAccount,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)
                ));

        Map<String, List<Transaction>> senderHistoryNoToday =
                buildExcludingToday(bySender, inFileBySender);

        Map<String, List<Transaction>> inFileByReceiver = fileTransactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getReceiverAccount,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)
                ));

        Map<String, List<Transaction>> receiverHistoryNoToday = buildExcludingToday(byReceiver, inFileByReceiver);


        return new AnalysisContext(senders,receivers,bySender,byReceiver, inFileBySender, senderHistoryNoToday, inFileByReceiver, receiverHistoryNoToday);


    }

    private static  Map<String,List<Transaction>> groupAndCap(List<Transaction> rawHistory, Function<Transaction,String> roleKey){

        Map<String, List<Transaction>> grouped=rawHistory.stream()
                .collect(Collectors.groupingBy(roleKey));

        Map<String, List<Transaction>> capped=new HashMap<>();
        for(Map.Entry<String, List<Transaction>> entry: grouped.entrySet()){
            List<Transaction> sorted=entry.getValue().stream()
                    .sorted(Comparator.comparing(Transaction::getTransactionTime).reversed())
                    .limit(MAX_HISTORY_PER_ACCOUNT)
                    .collect(Collectors.toUnmodifiableList());

            capped.put(entry.getKey(),sorted);


        }
        return capped;


    }

    private static Map<String, List<Transaction>> buildExcludingToday(
            Map<String, List<Transaction>> fullHistory,
            Map<String, List<Transaction>> inFileByAccount
    ){
        Map<String, List<Transaction>> result = new HashMap<>();

        for(Map.Entry<String, List<Transaction>> entry :fullHistory.entrySet()){
            String account= entry.getKey();

            Set<String> todayIds=inFileByAccount.getOrDefault(account, List.of()).stream()
                    .map(Transaction::getTransactionId)
                    .collect(Collectors.toSet());

            List<Transaction> filtered=entry.getValue().stream()
                    .filter(t->!todayIds.contains(t.getTransactionId()))
                    .collect(Collectors.toList());

            result.put(account,filtered);
        }
        return result;
    }


    public Set<String> sendersInFile() {
        return sendersInFile;
    }

    public Set<String> receiversInFile() {
        return receiversInFile;
    }

    public List<Transaction> senderHistory(String account) {
        return senderHistory.getOrDefault(account, List.of());
    }

    public List<Transaction> receiverHistory(String account) {
        return receiverHistory.getOrDefault(account, List.of());
    }

    public List<Transaction> transactionsInFileForSender(String account) {
        return transactionsInFileBySender.getOrDefault(account, List.of());
    }

    public List<Transaction> senderHistoryExcludingToday(String account) {
        return senderHistoryExcludingToday.getOrDefault(account, List.of());
    }

    public List<Transaction> transactionsInFileForReceiver(String account) {
        return transactionsInFileByReceiver.getOrDefault(account, List.of());
    }

    public List<Transaction> receiverHistoryExcludingToday(String account) {
        return receiverHistoryExcludingToday.getOrDefault(account, List.of());
    }




}
