package com.fk.ppowershell.nonblock;


import com.fk.ppowershell.Constant;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.fk.ppowershell.Constant.IMPL;

class ProcessorNonblocking implements Runnable {
    private static final Logger log = Logger.getLogger(ProcessorNonblocking.class.getName());
    private static final String CRLF = "\r\n";
    private final BufferedReader reader;
    private final boolean isAsync;
    private final LinkedList<Map<String, String>> headCache;
    private int retryTimes;
    private LocalDateTime baseTime;
    private final PowerShellNonblocking powerShellNonblocking;

    public ProcessorNonblocking(PowerShellNonblocking powerShellNonblocking, boolean isAsync, LinkedList<Map<String, String>> headCache) throws IOException {
        this.isAsync = isAsync;
        this.headCache = headCache;
        this.powerShellNonblocking = powerShellNonblocking;
        this.reader = new BufferedReader(new InputStreamReader(powerShellNonblocking.getP().getInputStream()));
        powerShellNonblocking.pid = getPID(reader);
    }

    //Use Powershell command '$PID' in order to recover the process identifier
    private int getPID(BufferedReader reader) throws IOException {
        powerShellNonblocking.commandWriter.println("$pid");
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
            log.warning("Unexpected error reading PowerShell output , Process suicide ");
            powerShellNonblocking.close();
        } catch (Exception e) {
            if (baseTime == null) {
                baseTime = LocalDateTime.now();
            }
            if (baseTime.isAfter(LocalDateTime.now().minusSeconds(60))) {
                if (++retryTimes > 10) {
                    log.log(Level.SEVERE, "Retry more than 10 times in 1 minute, exit execution", e);
                    throw e;
                }
            } else {
                log.info("Reset the switch for more than 1 minute");
                baseTime = LocalDateTime.now();
                retryTimes = 0;
            }
            log.warning("Unexpected error reading PowerShell output , Try again !");
            run();
        }
    }

    private void readData() throws IOException {
        String line;
        while (null != (line = this.reader.readLine())) {
            if (line.equals(Constant.START_SCRIPT_STRING)) {
                String identify = this.reader.readLine();
                StringBuilder body = new StringBuilder();
                while (null != (line = this.reader.readLine())) {
                    if (line.equals(Constant.END_SCRIPT_STRING)) {
                        if (identify.equals(this.reader.readLine()))
                            handCommandOutput(identify, body);
                        break;
                    } else
                        body.append(line).append(CRLF);
                }
            }
        }
    }

    private void handCommandOutput(String identify, StringBuilder body) {
        Map<String, String> head;
        String filePath;
        do {
            head = headCache.pollFirst();
            if (head == null) {
                log.log(Level.WARNING, "[{}] is not in headCache !", identify);
                return;
            }
            filePath = head.get(identify);
        } while (filePath == null);

        deleteTmpFile(filePath);

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
            log.log(Level.WARNING, "Failed to delete file {0}, Eat the exception and continue the current program", headFlag);
        }
    }
}