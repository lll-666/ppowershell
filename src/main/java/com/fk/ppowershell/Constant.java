package com.fk.ppowershell;

public interface Constant {
    String DEFAULT_WIN_EXECUTABLE = "powershell.exe";
    String DEFAULT_LINUX_EXECUTABLE = "pwsh";
    String TEMP_FOLDER = "tempFolder";
    String IS_ASYNC = "isAsync";
    String MAX_WAIT_TIME = "maxWaitTime";
    String TRY_LOCK_TIME = "tryLockTime";
    String IS_ADD_LOCK = "isAddLock";
    String START_PROCESS_WAIT_TIME = "startProcessWaitTime";
    boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    String IMPL = "IMPL";
    String END_SCRIPT_STRING = "--END-JPOWERSHELL-SCRIPT--";
    String START_SCRIPT_STRING = "--START-JPOWERSHELL-SCRIPT--";
    Character DOUBLE_QUOTE = '"';
}