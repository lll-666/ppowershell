package com.fk.ppowershell.nonblock;

import java.util.HashMap;
import java.util.Map;

public class OperationServiceManager {
    private OperationServiceManager() {
    }

    private static Map<String, OperationService> operationServices = new HashMap<>(10);

    static Map<String, OperationService> getOperationImpl() {
        return operationServices;
    }

    static void loadOperationServiceImpl(OperationService... services) {
        for (OperationService service : services)
            getOperationImpl().put(service.getOperationKey(), service);
    }
}