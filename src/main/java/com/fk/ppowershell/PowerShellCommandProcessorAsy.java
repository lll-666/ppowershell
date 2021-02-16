package com.fk.ppowershell;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.fk.ppowershell.Constant.IMPL;

class PowerShellCommandProcessorAsy implements Runnable {
    private static final Logger logger = Logger.getLogger(PowerShellCommandProcessorAsy.class.getName());
    private static final String CRLF = "\r\n";
    private final BufferedReader reader;
    private final boolean isAsync;
    private final Map<String, Map<String, String>> headCache;
    private int retryTimes;
    private LocalDateTime baseTime;
    private final PowerShellAyn powerShellAyn;

    public PowerShellCommandProcessorAsy(PowerShellAyn powerShellAyn, boolean isAsync, Map<String, Map<String, String>> headCache) throws IOException {
        this.isAsync = isAsync;
        this.headCache = headCache;
        this.powerShellAyn = powerShellAyn;
        this.reader = new BufferedReader(new InputStreamReader(powerShellAyn.getP().getInputStream()));
        powerShellAyn.pid = getPID(reader);
    }

    //Use Powershell command '$PID' in order to recover the process identifier
    private int getPID(BufferedReader reader) throws IOException {
        powerShellAyn.commandWriter.println("$pid");
        String commandOutput = reader.readLine().replaceAll("\\D", "");
        if ("65001".equals(commandOutput) || "936".equals(commandOutput) || "437".equals(commandOutput))
            commandOutput = reader.readLine().replaceAll("\\D", "");
        if (!commandOutput.isEmpty()) {
            return Integer.parseInt(commandOutput);
        }
        return -1;
    }

    public void run() {
        try {
            readData();
        } catch (IOException e) {
            logger.warning("Unexpected error reading PowerShell output , Process suicide ");
            powerShellAyn.close();
        } catch (Exception e) {
            if (baseTime == null) {
                baseTime = LocalDateTime.now();
            }
            if (baseTime.isAfter(LocalDateTime.now().minusSeconds(60))) {
                if (++retryTimes > 10) {
                    logger.log(Level.SEVERE, "Retry more than 10 times in 1 minute, exit execution", e);
                    throw e;
                }
            } else {
                logger.info("Reset the switch for more than 1 minute");
                baseTime = LocalDateTime.now();
                retryTimes = 0;
            }
            logger.warning("Unexpected error reading PowerShell output , Try again !");
            run();
        }
    }

    private void readData() throws IOException {
        String line;
        while (null != (line = this.reader.readLine())) {
            if (line.equals(Constant.START_SCRIPT_STRING)) {
                String headFlag = this.reader.readLine();
                StringBuilder body = new StringBuilder();
                while (null != (line = this.reader.readLine())) {
                    if (line.equals(Constant.END_SCRIPT_STRING)) {
                        handCommandOutput(headFlag, body);
                        break;
                    } else {
                        body.append(line).append(CRLF);
                    }
                }
            }
        }
    }

    private void handCommandOutput(String headFlag, StringBuilder body) {
        Map<String, String> head = headCache.remove(headFlag);
        if (head == null) {
            logger.log(Level.WARNING, "[{}] is not in head !", headFlag);
            return;
        }

        deleteTmpFile(head.remove(headFlag));

        OperationService operationService = OperationServiceManager.getOperationImpl().get(head.remove(IMPL));
        if (operationService == null) {
            OperationService.defaultProcess(head, body.toString());
        } else if (isAsync) {
            operationService.processAsync(head, body.toString());
        } else {
            operationService.process(head, body.toString());
        }
    }

    private void deleteTmpFile(String headFlag) {
        try {
            Files.delete(new File(headFlag).toPath());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete file {0}, Eat the exception and continue the current program", headFlag);
        }
    }
}