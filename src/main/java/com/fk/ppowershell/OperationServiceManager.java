package com.fk.ppowershell;

import java.util.HashMap;
import java.util.Map;

public class OperationServiceManager {
    private OperationServiceManager() {
    }

    private static final Object lock = new Object();

    private static Map<String, OperationService> operationServices = null;


    static Map<String, OperationService> getOperationImpl() {
        if (operationServices == null) {
            synchronized (lock) {
                operationServices = new HashMap<>(10);
            }
        }
        return operationServices;
    }

    static void loadOperationServiceImpl(OperationService... services) {
        for (OperationService service : services)
            getOperationImpl().put(service.getOperationKey(), service);
    }
}