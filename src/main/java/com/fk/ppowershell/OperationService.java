package com.fk.ppowershell;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface OperationService {
    Logger logger = Logger.getLogger(OperationService.class.getName());

    String getOperationKey();

    void process(Map<String, String> head, String output);

    static void defaultProcess(Map<String, String> head, String outPut) {
        logger.log(Level.INFO, "head={0}\noutPut={1}", new Object[]{head, outPut});
    }

    default void processAsync(Map<String, String> head, String output) {
        CompletableFuture.runAsync(() -> process(head, output));
    }
}