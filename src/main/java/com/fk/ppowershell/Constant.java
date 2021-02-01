package com.fk.ppowershell;

class Constant {
    static final String TEMP_FOLDER ="tempFolder";
    static final String IS_ASYNC ="isAsync";
    static final String HEAD_CACHE_INITIAL_CAPACITY ="headCacheInitialCapacity";
    static final String START_PROCESS_WAIT_TIME ="startProcessWaitTime";
    static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
}