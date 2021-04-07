package com.fk.ppowershell.control;


import com.fk.ppowershell.PSResponse;
import com.fk.ppowershell.PowerShellCodepage;
import com.fk.ppowershell.PowerShellConfig;
import com.fk.ppowershell.PowerShellException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.fk.ppowershell.Constant.*;

/**
 * 业务线程A 异步向powershell进程输出命令
 * 业务线程A 托管线程X 等待 读取powershell进程输出
 * 业务线程A 控制超时：若超出指定时间，X线程仍未读取到输出，则不再等待输出，直接返回超时
 */
public final class PowerShellControl implements AutoCloseable {
    private static final Logger log = Logger.getLogger(PowerShellControl.class.getName());
    // Process to store PowerShell session
    Process p;
    //PID of the process
    long pid = -1;
    // Writer to send commands
    PrintWriter commandWriter;
    private ProcessorControl processor;
    // Threaded session variables
    private boolean closed = false;
    private Integer startProcessWaitTime = 1;
    //Default PowerShell executable path
    private File tempFolder = null;
    private boolean isAddLock = false;
    private int maxWaitTime = 3;
    private int tryLockTime = 5;
    private final ReentrantLock lock = new ReentrantLock(true);

    private PowerShellControl() {
    }

    public void configuration(Map<String, String> config) {
        try {
            if (config == null) {
                config = new HashMap<>();
            }
            Properties properties = PowerShellConfig.getConfig();
            this.tempFolder = config.get(TEMP_FOLDER) != null ? getTempFolder(config.get(TEMP_FOLDER)) : getTempFolder(properties.getProperty(TEMP_FOLDER));
            this.maxWaitTime = Integer.parseInt(config.get(MAX_WAIT_TIME) != null ? config.get(MAX_WAIT_TIME) : properties.getProperty(MAX_WAIT_TIME));
            this.tryLockTime = Integer.parseInt(config.get(TRY_LOCK_TIME) != null ? config.get(TRY_LOCK_TIME) : properties.getProperty(TRY_LOCK_TIME));
            this.isAddLock = Boolean.parseBoolean(config.get(IS_ADD_LOCK) != null ? config.get(IS_ADD_LOCK) : properties.getProperty(IS_ADD_LOCK));
            this.startProcessWaitTime = Integer.parseInt(config.get(START_PROCESS_WAIT_TIME) != null ? config.get(START_PROCESS_WAIT_TIME)
                    : properties.getProperty(START_PROCESS_WAIT_TIME));
        } catch (Exception nfe) {
            log.log(Level.WARNING, "Could not read configuration. Using default values.", nfe);
        }
    }

    public static PowerShellControl openProcess() throws PowerShellException {
        return openProcess(null);
    }


    public static PowerShellControl openProcess(String customPowerShellExecutablePath) {
        PowerShellControl powerShell = null;
        try {
            powerShell = new PowerShellControl();
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
    private PowerShellControl initialize(String powerShellExecutablePath) {
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
        this.processor = new ProcessorControl(this);
        return this;
    }

    private PSResponse singleCommand(String command) {
        return executeCommand(command, "", false);
    }

    private PSResponse executeCommand(String command, String identity, boolean iScriptMode) {
        checkState();
        PSResponse commandOutput;
        long commandStart = System.currentTimeMillis();
        if (isAddLock) {
            try {
                if (!lock.tryLock(tryLockTime, TimeUnit.SECONDS)) {
                    return new PSResponse(true, "no lock obtained");
                }
            } catch (InterruptedException e) {
                log.warning("Interrupt blocking ! Restore interrupted state");
                Thread.currentThread().interrupt();
                return new PSResponse(true, "Interrupt blocking ! Restore interrupted state");
            }
            commandOutput = execute(command, identity, iScriptMode);
            lock.unlock();
        } else {
            commandOutput = execute(command, identity, iScriptMode);
        }

        long commandEnd = System.currentTimeMillis();
        log.log(Level.INFO, "execution time is {0} ms", commandEnd - commandStart);
        return commandOutput;
    }

    private PSResponse execute(String command, String identity, boolean iScriptMode) {
        try {
            commandWriter.println(command);
            String commandOutput = CompletableFuture.supplyAsync(() -> iScriptMode ? this.processor.process(identity) : this.processor.process()).get(maxWaitTime, TimeUnit.SECONDS);
            return new PSResponse(commandOutput);
        } catch (InterruptedException e) {
            log.warning("Interrupt blocking ! Restore interrupted state");
            Thread.currentThread().interrupt();
            return new PSResponse(true, "Interrupt blocking ! Restore interrupted state");
        } catch (ExecutionException e) {
            log.log(Level.WARNING, "Unexpected error when processing PowerShell command", e);
            return new PSResponse(true, "Unexpected error when processing PowerShell command");
        } catch (TimeoutException e) {
            return new PSResponse(true);
        }
    }

    /**
     * Used to execute a single singleCommand only
     * If there are multiple commands, only the output of the first singleCommand is output
     *
     * @param singleCommand Atomic command
     * @return Command output
     */
    public static PSResponse executeSingleCommand(String singleCommand) {
        try (PowerShellControl process = PowerShellControl.openProcess()) {
            return CompletableFuture.supplyAsync(() -> process.singleCommand(singleCommand)).get(process.maxWaitTime, TimeUnit.SECONDS);
        } catch (PowerShellException ex) {
            return new PSResponse(true, "PowerShell execute business exception");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PSResponse(true, "PowerShell interrupt exception");
        } catch (ExecutionException e) {
            return new PSResponse(true, "PowerShell execute script exception");
        } catch (TimeoutException e) {
            return new PSResponse(true);
        }
    }

    public PSResponse executeScriptFile(String scriptPath) {
        return executeScriptFile(scriptPath, "");
    }

    public PSResponse executeScriptFile(String scriptPath, String params) {
        try (BufferedReader srcReader = new BufferedReader(new FileReader(scriptPath))) {
            return executeScriptText(srcReader.lines().collect(Collectors.joining(";")), params);
        } catch (FileNotFoundException fnfex) {
            log.log(Level.SEVERE, "Unexpected error when processing PowerShell script: file not found", fnfex);
            return new PSResponse(true, "Wrong script path: ");
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Unexpected error when processing PowerShell script", ioe);
            return new PSResponse(true, "IO error reading: " + scriptPath);
        }
    }

    public PSResponse executeScriptText(String script) {
        return executeScriptText(script, "");
    }

    public PSResponse executeScriptText(String script, String params) {
        //1. Create temporary file
        File tmpFile;
        try {
            tmpFile = File.createTempFile("psscript_" + new Date().getTime(), ".ps1", this.tempFolder);
            if (!tmpFile.exists()) {
                return new PSResponse(true, "temporary is not exist");
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Exception creating temporary file", e);
            return new PSResponse(true, "Exception creating temporary file");
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
            tmpWriter.newLine();
            tmpWriter.write('"' + tmpFile.getName() + '"');
        } catch (IOException e) {
            log.log(Level.WARNING, "Unexpected error while writing temporary PowerShell script", e);
            return new PSResponse(true, "Unexpected error while writing temporary PowerShell script");
        }

        //3. Write commands to the PowerShell process And Return process output
        PSResponse psResponse = executeCommand(tmpFile.getAbsolutePath(), tmpFile.getName(), true);

        //4.delete tmpFile
        if (!tmpFile.delete()) {
            log.warning("file delete failed");
        }

        return psResponse;
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