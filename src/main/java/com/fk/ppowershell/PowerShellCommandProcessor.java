/*
 * Copyright 2016-2018 Javier Garcia Alonso.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fk.ppowershell;


import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class PowerShellCommandProcessor{
    private static final Logger logger = Logger.getLogger(PowerShellCommandProcessor.class.getName());
    private static final String CRLF = "\r\n";
    private final BufferedReader reader;
    private final boolean isAsync;
    private final Map<String, Map<String, String>> headCache;
    private int retryTimes;
    private LocalDateTime baseTime;

    public PowerShellCommandProcessor(BufferedReader reader, boolean isAsync, Map<String, Map<String, String>> headCache) {
        this.reader = reader;
        this.isAsync = isAsync;
        this.headCache = headCache;
    }

    public void run() {
        try {
            readData();
        } catch (IOException e) {
            logger.warning("Unexpected error reading PowerShell output , Process suicide ");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (baseTime == null) {
                baseTime = LocalDateTime.now();
            }
            if (baseTime.isAfter(LocalDateTime.now().minusSeconds(60))) {
                if (++retryTimes > 10) {
                    logger.log(Level.SEVERE,"Retry more than 10 times in 1 minute, exit execution",e);
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
        isContinueReady();
        String line;
        while (null != (line = this.reader.readLine())) {
            if (line.equals(PowerShell.START_SCRIPT_STRING)) {
                String headFlag = this.reader.readLine();
                String implFlag = this.reader.readLine();
                StringBuilder body = new StringBuilder();
                while (null != (line = this.reader.readLine())) {
                    if (line.equals(PowerShell.END_SCRIPT_STRING)) {
                        handCommandOutput(headFlag, implFlag, body);
                        break;
                    } else {
                        body.append(line).append(CRLF);
                    }
                }
            }
            isContinueReady();
        }
    }

    private void handCommandOutput(String headFlag, String implFlag, StringBuilder bodySb) {
        OperationService operationService = OperationServiceManager.getOperationImpl().get(implFlag);
        Map<String, String> head = headCache.remove(headFlag);
        deleteTmpFile(head.remove(headFlag));
        if (operationService == null) {
            OperationService.defaultProcess(head, bodySb.toString());
        } else if (isAsync) {
            operationService.processAsync(head, bodySb.toString());
        } else {
            operationService.process(head, bodySb.toString());
        }
    }

    private void deleteTmpFile(String headFlag) {
        try {
            Files.delete(Path.of(headFlag));
        } catch (IOException e) {
            logger.info("Failed to delete file " + headFlag + ", Eat the exception and continue the current program");
        }
    }

    private void isContinueReady() throws IOException {
        try {
            while (true) {
                if (!this.reader.ready()) {
                    Thread.sleep(5);
                } else {
                    break;
                }
            }
        } catch (InterruptedException ex) {
            logger.warning("Interrupted! , Interrupt the current thread");
            Thread.currentThread().interrupt();
        }
    }
}