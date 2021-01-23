package com.fk.ppowershell;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public interface OperationService {
    Logger logger = Logger.getLogger(OperationService.class.getName());

    String getOperationKey();

    void handle(Map<String, String> request);

    default void process(Map<String, String> head, String output) {
        handle(head);
    }

    static void defaultProcess(Map<String, String> head, String outPut) {
        logger.info("head=" + head + "\noutPut=" + outPut);
    }

    default void processAsync(Map<String, String> head, String output) {
        CompletableFuture.runAsync(() -> process(head, output));
    }
}