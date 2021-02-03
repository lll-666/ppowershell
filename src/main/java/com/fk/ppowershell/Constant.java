package com.fk.ppowershell;

public interface Constant {
    String TEMP_FOLDER = "tempFolder";
    String IS_ASYNC = "isAsync";
    String HEAD_CACHE_INITIAL_CAPACITY = "headCacheInitialCapacity";
    String START_PROCESS_WAIT_TIME = "startProcessWaitTime";
    boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    String IMPL = "IMPL";
    String END_SCRIPT_STRING = "--END-JPOWERSHELL-SCRIPT--";
    String START_SCRIPT_STRING = "--START-JPOWERSHELL-SCRIPT--";
}