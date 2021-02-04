package com.fk.ppowershell;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

class PowerShellCommandProcessorSyn {
    private static final Logger logger = Logger.getLogger(PowerShellCommandProcessorAsy.class.getName());
    private static final String CRLF = "\r\n";
    private final PowerShellSyn powerShell;
    private final BufferedReader reader;

    public PowerShellCommandProcessorSyn(PowerShellSyn powerShell) {
        this.powerShell = powerShell;
        this.reader = new BufferedReader(new InputStreamReader(powerShell.p.getInputStream()));
        this.powerShell.pid = getPID(reader);
    }

    //Use Powershell command '$PID' in order to recover the process identifier
    private int getPID(BufferedReader reader) {
        powerShell.commandWriter.println("$pid");
        try {
            String commandOutput = reader.readLine().replaceAll("\\D", "");
            if ("65001".equals(commandOutput) || "936".equals(commandOutput) || "437".equals(commandOutput))
                commandOutput = reader.readLine().replaceAll("\\D", "");
            if (!commandOutput.isEmpty()) {
                return Integer.parseInt(commandOutput);
            }
        } catch (IOException e) {
            throw new PowerShellException("", e);
        }
        return -1;
    }

    public String process(boolean iScriptMode) {
        try {
            waitingToReadData();
            return readData(iScriptMode);
        } catch (IOException ioe) {
            Logger.getLogger(PowerShellCommandProcessorAsy.class.getName()).log(Level.SEVERE, "Unexpected error reading PowerShell output", ioe);
            return ioe.getMessage();
        }
    }

    //Reads all data from output
    private String readData(boolean iScriptMode) throws IOException {
        StringBuilder powerShellOutput = new StringBuilder();
        String line;
        if(iScriptMode){
            while (null != (line = this.reader.readLine())) {
                if (line.equals(Constant.END_SCRIPT_STRING)) {
                    break;
                }
                powerShellOutput.append(line).append(CRLF);
            }
        }else{
            while (null != (line = this.reader.readLine())) {
                if (line.equals(Constant.END_SCRIPT_STRING)) {
                    break;
                }
                powerShellOutput.append(line).append(CRLF);
                if (!canContinueReading()) {
                    break;
                }
            }
        }
        return powerShellOutput.toString().replaceAll("\\s+$", "");
    }

    private boolean canContinueReading() {
        //If the reader is not ready, gives it some milliseconds
        //It is important to do that, because the ready method guarantees that the readline will not be blocking
        try {
            if (this.reader.ready()) {
                return true;
            }
            Thread.sleep(5);
            //If not ready yet, wait a moment to make sure it is finished
            if (this.reader.ready()) {
                return true;
            }
            Thread.sleep(50);
            return this.reader.ready();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "", e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private void waitingToReadData() throws IOException {
        try {
            while (!this.reader.ready()) Thread.sleep(50);
        } catch (InterruptedException ex) {
            logger.warning("Interrupt blocking ! , Restore interrupted state");
            Thread.currentThread().interrupt();
        }
    }
}