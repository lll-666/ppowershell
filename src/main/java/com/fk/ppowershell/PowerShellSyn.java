package com.fk.ppowershell;


import java.io.*;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.fk.ppowershell.Constant.END_SCRIPT_STRING;
import static com.fk.ppowershell.Constant.IS_WINDOWS;

public class PowerShellSyn implements AutoCloseable {
    private static final Logger log = Logger.getLogger(PowerShellSyn.class.getName());
    // Process to store PowerShell session
    Process p;
    //PID of the process
    long pid = -1;
    // Writer to send commands
    PrintWriter commandWriter;
    private PowerShellCommandProcessorSyn processor;
    // Threaded session variables
    private boolean closed = false;
    private Integer startProcessWaitTime = 1;
    //Default PowerShell executable path
    private static final String DEFAULT_WIN_EXECUTABLE = "powershell.exe";
    private static final String DEFAULT_LINUX_EXECUTABLE = "powershell";
    private File tempFolder = null;

    private PowerShellSyn() {
    }

    public void configuration(Map<String, String> config) {
        try {
            if (config == null) {
                config = new HashMap<>();
            }
            Properties properties = PowerShellConfig.getConfig();
            this.tempFolder = config.get(Constant.TEMP_FOLDER) != null ? getTempFolder(config.get(Constant.TEMP_FOLDER))
                    : getTempFolder(properties.getProperty(Constant.TEMP_FOLDER));
            this.startProcessWaitTime = Integer.parseInt(config.get(Constant.START_PROCESS_WAIT_TIME) != null ? config.get(Constant.START_PROCESS_WAIT_TIME)
                    : properties.getProperty(Constant.START_PROCESS_WAIT_TIME));
        } catch (Exception nfe) {
            log.log(Level.SEVERE, "Could not read configuration. Using default values.", nfe);
        }
    }

    public static PowerShellSyn openProcess() throws PowerShellException {
        return openProcess(null);
    }


    public static PowerShellSyn openProcess(String customPowerShellExecutablePath) {
        PowerShellSyn powerShell = null;
        try {
            powerShell = new PowerShellSyn();
            powerShell.configuration(null);
            String powerShellExecutablePath = customPowerShellExecutablePath == null ? (IS_WINDOWS ? DEFAULT_WIN_EXECUTABLE : DEFAULT_LINUX_EXECUTABLE) : customPowerShellExecutablePath;
            return powerShell.initialize(powerShellExecutablePath);
        } catch (Exception e) {
            if (powerShell != null) {
                powerShell.close();
            }
            log.log(Level.WARNING, "initialize powerShell failed ");
            throw e;
        }
    }

    // Initializes PowerShell console in which we will enter the commands
    private PowerShellSyn initialize(String powerShellExecutablePath) throws PowerShellException {
        String codePage = PowerShellCodepage.getIdentifierByCodePageName(Charset.defaultCharset().name());
        ProcessBuilder pb;

        //Start powershell executable in process
        if (IS_WINDOWS) {
            pb = new ProcessBuilder("cmd.exe", "/c", "chcp", codePage, ">", "NUL", "&", powerShellExecutablePath,
                    "-ExecutionPolicy", "Bypass", "-NoExit", "-NoProfile", "-Command", "-");
        } else {
            pb = new ProcessBuilder(powerShellExecutablePath, "-nologo", "-noexit", "-Command", "-");
        }

        //Merge standard and error streams
        pb.redirectErrorStream(true);
        try {
            //Launch process
            p = pb.start();
            if (p.waitFor(startProcessWaitTime, TimeUnit.SECONDS) && !p.isAlive()) {
                throw new PowerShellException("Cannot execute PowerShell. Please make sure that it is installed in your system. Errorcode:" + p.exitValue());
            }
        } catch (IOException ex) {
            throw new PowerShellException("Cannot execute PowerShell. Please make sure that it is installed in your system", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PowerShellException("Cannot execute PowerShell. Please make sure that it is installed in your system", ex);
        }

        //Prepare writer that will be used to send commands to powershell
        this.commandWriter = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(p.getOutputStream())), true);
        this.processor = new PowerShellCommandProcessorSyn(this);
        return this;
    }


    private String executeCommand(String command, boolean iScriptMode) {
        checkState();
        String commandOutput = "";
        long commandStart = System.currentTimeMillis();
        try {
            commandWriter.println(command);
            commandOutput = this.processor.process(iScriptMode);
            long commandEnd = System.currentTimeMillis();
            log.log(Level.INFO, "execution time is {0} ms", commandEnd - commandStart);
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Unexpected error when processing PowerShell command", ex);
        }
        return commandOutput;
    }

    public static String executeSingleCommand(String command) {
        String response = null;

        try (PowerShellSyn session = PowerShellSyn.openProcess()) {
            response = session.executeCommand(command, false);
        } catch (PowerShellException ex) {
            log.log(Level.SEVERE, "PowerShell not available", ex);
        }

        return response;
    }


    public boolean isLastCommandInError() {
        return !Boolean.parseBoolean(executeCommand("$?", false));
    }

    public String executeScriptFile(String scriptPath) {
        return executeScriptFile(scriptPath, "");
    }

    public String executeScriptFile(String scriptPath, String params) {
        try (BufferedReader srcReader = new BufferedReader(new FileReader(new File(scriptPath)))) {
            return executeScriptText(srcReader.lines().toString(), params);
        } catch (FileNotFoundException fnfex) {
            log.log(Level.SEVERE, "Unexpected error when processing PowerShell script: file not found", fnfex);
            return "Wrong script path: ";
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Unexpected error when processing PowerShell script", ioe);
            return "IO error reading: " + scriptPath;
        }
    }

    public String executeScriptText(String script) {
        return executeScriptText(script, "");
    }

    public String executeScriptText(String script, String params) {
        //1. Create temporary file
        File tmpFile;
        try {
            tmpFile = File.createTempFile("psscript_" + new Date().getTime(), ".ps1", this.tempFolder);
            if (!tmpFile.exists()) {
                return "temporary is not exist";
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Exception creating temporary file", e);
            return "Exception creating temporary file";
        }

        //2. Writing scripts to temporary files
        try (BufferedReader srcReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(script.getBytes())));
             BufferedWriter tmpWriter = new BufferedWriter(new FileWriter(tmpFile))) {
            String line;
            while ((line = srcReader.readLine()) != null) {
                tmpWriter.write(line);
                tmpWriter.newLine();
            }
            tmpWriter.write('"' + END_SCRIPT_STRING + '"');
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unexpected error while writing temporary PowerShell script", e);
            return "Unexpected error while writing temporary PowerShell script";
        }

        //3. Write commands to the PowerShell process And Return process output
        return executeCommand(tmpFile.getAbsolutePath(), true);
    }

    @Override
    public void close() {
        if (!this.closed) {
            try {
                commandWriter.println("exit");
                if (this.pid > 0) {
                    //If it can be closed, force kill the process
                    log.log(Level.SEVERE, "Forcing PowerShell to close. PID: " + this.pid);
                    try {
                        Runtime.getRuntime().exec("taskkill.exe /PID " + pid + " /F /T");
                        this.closed = true;
                    } catch (IOException e) {
                        log.log(Level.SEVERE, "Unexpected error while killing powershell process", e);
                    }
                }
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Unexpected error when when closing PowerShell", ex);
            } finally {
                commandWriter.close();
                try {
                    if (p.isAlive()) {
                        p.getInputStream().close();
                    }
                } catch (IOException ex) {
                    log.log(Level.SEVERE, "Unexpected error when when closing streams", ex);
                }
                this.closed = true;
            }
        }
    }

    //Checks if PowerShell have been already closed
    private void checkState() {
        if (this.closed) {
            throw new IllegalStateException("PowerShell is already closed. Please open a new session.");
        }
    }

    //Return the temp folder File object or null if the path does not exist
    private File getTempFolder(String tempPath) {
        if (tempPath != null) {
            File folder = new File(tempPath);
            if (folder.exists()) {
                return folder;
            }
        }
        return null;
    }
}
